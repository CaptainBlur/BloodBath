package com.vova9110.bloodbath.alarmScreenBackground

import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import com.vova9110.bloodbath.MyApp
import com.vova9110.bloodbath.SplitLogger
import com.vova9110.bloodbath.database.Alarm
import java.util.*

typealias sl = SplitLogger
typealias AED = AlarmExecutionDispatch
typealias BU = BackgroundUtils


class AlarmExecutionDispatch: BroadcastReceiver() {

    /**
     * There are two cases when Broadcast is expected: on system locked boot or when user
     * pressed button to dismiss an upcoming (or snoozed) alarm
     */
    @SuppressLint("WakelockTimeout")
    override fun onReceive(context: Context?, intent: Intent?) {
        val res = goAsync()
        val wakeLock =
            (context!!.getSystemService(Service.POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "${context.packageName}:${this::class.java.name}").also { it.acquire() }
        sl.en()

        val handler = BU.getHandler("executionDispatch")
        handler.post {
            handleBroadcast(context.applicationContext, intent!!)
            res.finish()
            wakeLock.release()
            sl.ex()
        }
    }

    private fun handleBroadcast(context: Context, intent: Intent){
        lateinit var repo: AlarmRepo
        val action = intent.action

        if (action!=null &&
            (action == Intent.ACTION_LOCKED_BOOT_COMPLETED ||
                    action == Intent.ACTION_TIMEZONE_CHANGED ||
                    action == Intent.ACTION_TIME_CHANGED)){
            sl.i("checking all by system broadcast")
            val enc = context.createDeviceProtectedStorageContext()
            repo = (enc.applicationContext as MyApp).component.repo

            checkAll(enc, repo, repo.actives)
        }
        else{
            repo = (context as MyApp).component.repo
            val id = intent.getStringExtra(extraID)
            val state = intent.getStringExtra(extraState)
            if (id==null || state==null) sl.s("forced state change expected, but missing intent extras").also { return }

            sl.f("forced state change requested for *$id*")
            val alarm = try {
                repo.getOne(id!!)
            } catch (e: Exception) {
                sl.s("cannot get alarm with requested id: $id", e)
                return
            }

            //Covers both (fire and preliminary) state alarms
            if (state == targetStateDismiss){
                /*
                helpDismiss may stumble upon upcoming preliminary - we're skipping the first condition,
                so it goes directly to the ^disable^ setter
                */
                if (alarm.preliminary) alarm.preliminaryFired = true
                repo.update(alarm, false, context)
                helpDismiss(context, id, repo, false)
            }
            //Only to disable preliminary state and schedule firing
            else{
                /*
                Since after helping method it goes to the ^prepare^ setter,
                we have to cancel ^preliminary^ firing PI, cause setter doesn't
                */
                BU.cancelPI(context, id, Alarm.STATE_PRELIMINARY)
                helpDismiss(context, id, repo, false)
            }
        }
    }

    companion object{

        const val extraID = "extra_id"
        const val extraState = "extra_state"
        const val targetStateDismiss = "dismiss"
        const val targetStateDismissPreliminary = "dismiss_preliminary"

        @JvmStatic fun handleStateChange(context: Context, alarm: Alarm){
        val repo = (context as MyApp).component.repo

        sl.fp("new state is *${alarm.state}*")
        when (alarm.state){
            Alarm.STATE_DISABLE-> setDisable(context, alarm, repo)
            Alarm.STATE_SUSPEND-> setSuspend(context, alarm, repo)
            Alarm.STATE_ANTICIPATE-> setAnticipate(context, alarm, repo)
            Alarm.STATE_PREPARE-> {
                if (alarm.getInfo(context).preliminary) setPreparePreliminary(context, alarm, repo)
                else setPrepareFire(context, alarm, repo)
            }
            Alarm.STATE_PREPARE_SNOOZE-> setPrepareSnooze(context, alarm, repo)
            else-> throw IllegalArgumentException("Cannot handle *${alarm.state}* state")
        }
        }

        /**
         * It's crucial that arriving to this point, triggerTime of an ENABLED alarm should be set
         * to a proper value.
         */
        @JvmStatic fun defineNewState(context: Context, alarm: Alarm, repo: AlarmRepo){
            /*
            This condition takes place only if target instance didn't fire, but supposed to do before
            (because if we have enabled state and set triggerTime than we have either missed or repeatable instance,
            and for the last case we'll set new triggerTime).
            Also, just to point it out: ^lastTriggerTime^ is a error handling value just to detect whether firing time is missed
             */
            if (alarm.triggerTime!=null){
                if (!alarm.enabled) throw IllegalStateException("Cannot proceed with set triggerTime and disabled state")

                if (alarm.triggerTime!!.before(Date()) or (alarm.triggerTime!! == Date())){
                    sl.f("missed triggerTime detected for *${alarm.id}*!")
                    val info = alarm.getInfo(context)

                    //missing preliminary don't bother us, so let's find out
                    //what about missing real firing time
                    if (alarm.getInfo(context).preliminary){
                        processPreliminary(alarm).also{ sl.fr("skipping missed preliminary") }
                        setPrepareFire(context, alarm, repo)
                        return
                    }

                    //if we didn't reach ^lost^ time for firing state yet,
                    //it's time to assign something and return
                    if (getPlusDateMins(info.globalLost.toShort(), alarm.triggerTime).after(Date())){
                        if (!alarm.snoozed) launchFire(context, alarm, repo)
                        else launchSnooze(context, alarm, repo)
                        return
                    }
                    //time to fire is lost already, we have to set one of the miss states later,
                    //but before that we must check maybe it's repeatable
                    else {
                        alarm.lastTriggerTime = alarm.triggerTime?.clone() as Date

                        if (alarm.repeatable) alarm.calculateTriggerTime()
                        else{
                            alarm.enabled = false
                            alarm.triggerTime = null
                        }
                    }
                }
            }
            else if (alarm.enabled) throw IllegalStateException("Cannot proceed with enabled state and null triggerTime")

            /*
            Next evaluation stage
            Mostly dealing with Miss state here (that's what we have lastTriggerTime for)
            Filtering non-active instances
             */
            if (!alarm.enabled && alarm.lastTriggerTime==null){//Setting disable without a doubt
                setDisable(context, alarm, repo)
                return
            }
            //Power is still off, and we're checking whether the time for miss state is gone
            else if (alarm.lastTriggerTime!=null){
                if (alarm.lastTriggerTime!!.before(getPlusDate(-2))){//Time is gone, but if alarm should fire in the future, we have to consider a new state in the block below
                    setDisable(context, alarm, repo)
                    if (!alarm.enabled) return//else temporarily setting disable, just to clear notifications and appointments
                }
                //We're just in time for miss
                else if (!alarm.enabled) {
                    setMiss(context, alarm, repo)
                    return
                }
            }
            /*
            Final block for assigning new states only for active ones
             */
            if (alarm.lastTriggerTime!=null){//Time for miss, but we have to schedule suspend
                setMissPlusSchedule(context, alarm, repo)
                return
            }
            if (Date().before(getPlusDate(-10, alarm.triggerTime))){
                setSuspend(context, alarm, repo)
                return
            }
            if (Date().before(getPlusDate(-2, alarm.triggerTime))){
                setAnticipate(context, alarm, repo)
                return
            }
            if (Date().before(alarm.triggerTime)) {
                //Now we have to decide 'which' triggerTime is actually stored inside alarm
                if (!alarm.snoozed && alarm.getInfo(context).preliminary) setPreparePreliminary(context, alarm, repo)
                else if (!alarm.snoozed) setPrepareFire(context, alarm, repo)
                else setPrepareSnooze(context, alarm, repo)
                return
            }

        }

        private fun setDisable(context: Context, alarm: Alarm, repo: AlarmRepo){
            alarm.lastTriggerTime = null

            if (!alarm.enabled && !alarm.test){
                alarm.state=Alarm.STATE_DISABLE
                alarm.triggerTime = null
                handleLog(alarm.state, alarm.id)
                repo.update(alarm, false, context)
            }
            else if (alarm.test && alarm.preliminaryFired){
                sl.i("test entry reached *disable* state")
                repo.deleteOne(alarm, context)
            }

            BU.cancelNotification(context, alarm.id, Alarm.STATE_ALL)
            BU.cancelPI(context, alarm.id, Alarm.STATE_ALL)
            BU.setGlobalID(context, repo)
        }
        //When instance is not active, we setting miss and scheduling disable
        private fun setMiss(context: Context, alarm: Alarm, repo: AlarmRepo) {
            alarm.state=Alarm.STATE_DISABLE
            val newTime = getPlusDate(2, alarm.lastTriggerTime)
            handleLog(Alarm.STATE_MISS, alarm.id, Alarm.STATE_DISABLE)
            repo.update(alarm, false, context)

            BU.createNotification(context, alarm.id, Alarm.STATE_MISS)
            BU.scheduleExact(context, alarm.id, alarm.state, newTime.time)
            BU.setGlobalID(context, repo)
        }
        //When instance should fire in future, we need to schedule change from miss to suspend
        private fun setMissPlusSchedule(context: Context, alarm: Alarm, repo: AlarmRepo) {
            alarm.state=Alarm.STATE_SUSPEND
            val newTime = getPlusDate(2, alarm.lastTriggerTime)
            handleLog(Alarm.STATE_MISS, alarm.id, Alarm.STATE_SUSPEND)
            repo.update(alarm, false, context)

            BU.createNotification(context, alarm.id, Alarm.STATE_MISS)
            BU.scheduleExact(context, alarm.id, alarm.state, newTime.time)
            BU.setGlobalID(context, repo)
        }
        private fun setSuspend(context: Context, alarm: Alarm, repo: AlarmRepo){
            alarm.state = Alarm.STATE_ANTICIPATE
            val newTime = getPlusDate(-10, alarm.triggerTime)
            handleLog(Alarm.STATE_SUSPEND, alarm.id, Alarm.STATE_ANTICIPATE)
            repo.update(alarm, false, context)

            BU.cancelNotification(context, alarm.id, Alarm.STATE_ALL)
            BU.scheduleExact(context, alarm.id, Alarm.STATE_ANTICIPATE, newTime.time)
            BU.setGlobalID(context, repo)
        }
        private fun setAnticipate(context: Context, alarm: Alarm, repo: AlarmRepo){
            alarm.state = Alarm.STATE_PREPARE
            val newTime = getPlusDate(-2, alarm.triggerTime)
            handleLog(Alarm.STATE_ANTICIPATE, alarm.id, Alarm.STATE_PREPARE)
            repo.update(alarm, false, context)

            BU.scheduleExact(context, alarm.id, Alarm.STATE_PREPARE, newTime.time)
            BU.scheduleAlarm(context, alarm.id, if (alarm.getInfo(context).preliminary) Alarm.STATE_PRELIMINARY else Alarm.STATE_FIRE, alarm.triggerTime!!.time)
            BU.setGlobalID(context, repo)
        }
        private fun setPreparePreliminary(context: Context, alarm: Alarm, repo: AlarmRepo){
            sl.f("(preliminary)")
            alarm.state = Alarm.STATE_PRELIMINARY
            handleLog(Alarm.STATE_PREPARE, alarm.id, alarm.state)
            repo.update(alarm, false, context)

            BU.createPreliminaryNotification(context, alarm.id, alarm.triggerTime)
            BU.cancelPI(context, alarm.id, alarm.state)//control cancelling in case when previous was anticipate
            BU.scheduleAlarm(context, alarm.id, alarm.state, alarm.triggerTime!!.time)
            BU.setGlobalID(context, repo)
        }
        private fun setPrepareFire(context: Context, alarm: Alarm, repo: AlarmRepo){
            sl.f("(firing)")
            alarm.state = Alarm.STATE_FIRE
            handleLog(Alarm.STATE_PREPARE, alarm.id, alarm.state)
            repo.update(alarm, false, context)

            BU.createNotification(context, alarm.id, Alarm.STATE_PREPARE)
            BU.cancelPI(context, alarm.id, alarm.state)//control cancelling in case when previous was anticipate
            BU.scheduleAlarm(context, alarm.id, alarm.state, alarm.triggerTime!!.time)
            BU.setGlobalID(context, repo)
        }
        private fun setPrepareSnooze(context: Context, alarm: Alarm, repo: AlarmRepo){
            alarm.state = Alarm.STATE_SNOOZE
            handleLog(Alarm.STATE_PREPARE_SNOOZE, alarm.id, Alarm.STATE_SNOOZE)
            repo.update(alarm, false, context)

            BU.createNotification(context, alarm.id, Alarm.STATE_PREPARE_SNOOZE)
            BU.scheduleAlarm(context, alarm.id, Alarm.STATE_SNOOZE, alarm.triggerTime!!.time)
            BU.setGlobalID(context, repo)
        }

        //These two little helpers are meant to be launched from FCR when we need AED's functionality,
        //but we're too afraid to send broadcast to AED, exit FCR and then jump back to FCR when these operations are done.
        //Also, we don't have to update Alarm instance's data in DB because it's already in a proper state
        @JvmStatic
        fun killFireNotification(context: Context, alarm: Alarm){
            BU.cancelNotification(context, alarm.id, Alarm.STATE_PREPARE)
        }
        @JvmStatic
        fun killSnoozeNotification(context: Context, alarm: Alarm) {
            BU.cancelNotification(context, alarm.id, Alarm.STATE_PREPARE_SNOOZE)
            BU.cancelNotification(context, alarm.id, Alarm.STATE_SNOOZE)
        }
        private fun launchFire(context: Context, alarm: Alarm, repo: AlarmRepo){
            alarm.state = Alarm.STATE_FIRE
            handleLog(alarm.state, alarm.id)
            repo.update(alarm, false, context)

            BU.cancelNotification(context, alarm.id, Alarm.STATE_PREPARE)
            BU.createBroadcast(context, alarm.id)
        }
        private fun launchSnooze(context: Context, alarm: Alarm, repo: AlarmRepo) {
            alarm.state = Alarm.STATE_SNOOZE
            handleLog(alarm.state, alarm.id)
            repo.update(alarm, false, context)

            BU.cancelNotification(context, alarm.id, Alarm.STATE_PREPARE_SNOOZE)
            BU.cancelNotification(context, alarm.id, Alarm.STATE_SNOOZE)
            BU.createBroadcast(context, alarm.id)
        }

        private fun processRepeatable(alarm: Alarm){
            if (alarm.repeatable) alarm.calculateTriggerTime()//Instance is ^on^ on arrival, we just need to set tT
            else {
                alarm.enabled = false
                alarm.triggerTime = null
            }
        }
        private fun processPreliminary(alarm: Alarm){
            alarm.preliminaryFired = true
            alarm.calculateTriggerTime()
        }
        /**
         * Practically the same as simple Dismiss
         */
        @JvmStatic
        fun helpNotPassed(context: Context, id: String, repo: AlarmRepo){
            sl.w("Passed alarm's id *${id}* do not match with global *${BU.getGlobalID(context)}*")

            val alarm = repo.getOne(id)
            alarm.snoozed = false
            processRepeatable(alarm)
            defineNewState(context, alarm, repo)
            stopService(context)
        }
        /**
         * Just setting new triggerTime and stopping service
         */
        @JvmStatic
        fun helpSnooze(context: Context, id: String, repo: AlarmRepo){
            sl.i("^Snooze^")

            val alarm = repo.getOne(id)
            alarm.triggerTime = getPlusDateMins(alarm.getInfo(context).globalSnoozed.toShort())
            alarm.snoozed = true
            setPrepareSnooze(context, alarm, repo)

            stopService(context)
        }

        /**
         * In any case of incoming Preliminary, Fire should follow
         * If Detection required, we just starting service.
         * If no more than Dismiss than disabling snoozed flag, and if alarm is NOT repeatable,
         * disabling it and nulling triggerTime. Than defining new state and stopping service
         */
        @JvmStatic
        fun helpDismiss(context: Context, id: String, repo: AlarmRepo, stopService: Boolean = true){
            sl.i("^Dismiss^")

            val alarm = repo.getOne(id)
            if (alarm.getInfo(context).preliminary){
                sl.i("^Preliminary cancelled. Preparing for real firing")

                processPreliminary(alarm)
                defineNewState(context, alarm, repo)
            }
            else if (alarm.detection){
                sl.i("^Activeness detection required. Starting ADS")

                val intent = Intent(context, ActivenessDetectionService::class.java)
                intent.putExtra("info", alarm.getInfo(context))
                context.startForegroundService(intent)
            }
            else{
                sl.i("^No need for activeness detection. Preparing for exit")

                alarm.snoozed = false
                alarm.preliminaryFired = false
                processRepeatable(alarm)
                defineNewState(context, alarm, repo)
            }
            if (stopService) stopService(context)
        }

        /**
         * Doing the same as for the simple Dismiss action
         */
        @JvmStatic
        fun helpAfterDetection(context: Context, id: String){
            sl.i("^After detection^")
            val repo = (context as MyApp).component.repo

            val alarm = repo.getOne(id)
            alarm.snoozed = false
            processRepeatable(alarm)
            defineNewState(context, alarm, repo)
        }

        /**
         * Resetting snoozed flag, leveling lastTriggerTime for triggerTime, and
         * if NOT repeatable, setting power to false and triggerTime for null. Than defining
         * new state, stopping FCR with intent and sending AA a broadcast with KILL action
         */
        @JvmStatic
        fun helpMiss(context: Context, id: String, repo: AlarmRepo){
            sl.i("^Miss^")
            val alarm = repo.getOne(id)

            if (alarm.getInfo(context).preliminary){
                sl.i("^Preliminary missed. Preparing for real firing")

                processPreliminary(alarm)
                defineNewState(context, alarm, repo)
            }
            else {
                alarm.snoozed = false
                alarm.lastTriggerTime = alarm.triggerTime?.clone() as Date
                processRepeatable(alarm)
                defineNewState(context, alarm, repo)
            }

            stopService(context)
            context.sendBroadcast(Intent(FiringControlService.ACTION_KILL))
        }

        //In case the KILL call didn't reach the activity in previous function
        @JvmStatic
        fun reassureStillValid(id: String, repo: AlarmRepo): Boolean {
            val alarm = repo.getOne(id)

            return when(alarm.state){
                Alarm.STATE_FIRE, Alarm.STATE_PRELIMINARY, Alarm.STATE_SNOOZE -> true
                else -> false
            }
        }
        private fun stopService(context: Context){
            val intent = Intent(context, FiringControlService::class.java)
            intent.action = FiringControlService.STOP_SERVICE
            context.startService(intent)
        }


        private fun getPlusDate(hours: Short, oldDate: Date?=null): Date {
            return if (oldDate==null) Date(System.currentTimeMillis().plus(hours * 60 * 60 * 1000))
            else Date(oldDate.time.plus(hours * 60 * 60 * 1000))
        }

        private fun getPlusDateMins(mins: Short, oldDate: Date?=null): Date {
            return if (oldDate==null) Date(System.currentTimeMillis().plus(mins * 60 * 1000))
            else Date(oldDate.time.plus(mins * 60 * 1000))
        }
        private fun handleLog(state: String, id: String, nextState: String? = null){
            sl.i("state: *$state* has been set for *$id*")
            if (nextState!=null) sl.fpc("next state is: *$nextState*")
        }

        @JvmStatic fun checkAll(context: Context, repo: AlarmRepo, list: List<Alarm>){
            sl.i("||| reassuring all actives")
            sl.i("∨∨∨")
            for (alarm in list) if (alarm.state!=Alarm.STATE_DISABLE){
                BU.cancelNotification(context, alarm.id, Alarm.STATE_ALL)
                BU.cancelPI(context, alarm.id, Alarm.STATE_ALL)
                defineNewState(context, alarm, repo)
            }
            sl.i("∧∧∧")
            sl.i("||| all checked")
        }
        @JvmStatic fun wipeOne(context: Context, alarm: Alarm){
            if (alarm.enabled || alarm.lastTriggerTime!=null) {
                BU.cancelNotification(context, alarm.id, Alarm.STATE_ALL)
                BU.cancelPI(context, alarm.id, Alarm.STATE_ALL)
            }
            else sl.fp("no need for states erasure")
        }
        @JvmStatic fun wipeAll(context: Context, list: List<Alarm>){
            for (alarm in list) wipeOne(context, alarm)
        }
    }

}