package com.vova9110.bloodbath.alarmScreenBackground

import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Message
import android.os.PowerManager
import com.vova9110.bloodbath.MyApp
import com.vova9110.bloodbath.SplitLogger
import com.vova9110.bloodbath.database.Alarm
import java.util.*
import javax.inject.Inject

typealias sl = SplitLogger
typealias AED = AlarmExecutionDispatch
typealias BU = BackgroundUtils


class AlarmExecutionDispatch: BroadcastReceiver() {

    private lateinit var repo: AlarmRepo

    @SuppressLint("WakelockTimeout")
    override fun onReceive(context: Context?, intent: Intent?) {
        val res = goAsync()
        val wakeLock =
            (context!!.getSystemService(Service.POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "${context.packageName}:${this::class.java.name}").also { it.acquire() }
        sl.en()

        val handler = BU.getHandler("executionDispatch")
        handler.post {
            handleStateChange(context.applicationContext, intent!!)
            res.finish()
            wakeLock.release()
            sl.ex()
        }
    }

    private fun handleStateChange(context: Context, intent: Intent){
        if (intent.action==null) throw IllegalArgumentException("Cannot proceed without passed ID")
        repo = (context as MyApp).component.repo
        val alarm = repo.getOne(intent.action)

        sl.f("new state is *${alarm.state}*")
        when (alarm.state){
            Alarm.STATE_DISABLE-> setDisable(context, alarm, repo)
            Alarm.STATE_SUSPEND-> setSuspend(context, alarm, repo)
            Alarm.STATE_ANTICIPATE-> setAnticipate(context, alarm, repo)
            Alarm.STATE_PREPARE-> setPrepareFire(context, alarm, repo)
            Alarm.STATE_PREPARE_SNOOZE-> setPrepareSnooze(context, alarm, repo)
            else-> throw IllegalArgumentException("Cannot handle *${alarm.state}* state")
        }

    }

    companion object{

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
                    val info = repo.getTimesInfo(alarm.id)

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
            Non-active alarms shouldn't get through
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
                if (!alarm.snoozed) setPrepareFire(context, alarm, repo)
                else setPrepareSnooze(context, alarm, repo)
                return
            }

        }

        private fun setDisable(context: Context, alarm: Alarm, repo: AlarmRepo){
            alarm.lastTriggerTime = null

            if (!alarm.enabled){
                alarm.state=Alarm.STATE_DISABLE
                alarm.triggerTime = null
                handleLog(alarm.state, alarm.id)
                repo.update(alarm, false)
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
            repo.update(alarm, false)

            BU.createNotification(context, alarm.id, Alarm.STATE_MISS)
            BU.scheduleExact(context, alarm.id, alarm.state, newTime.time)
            BU.setGlobalID(context, repo)
        }
        //When instance should fire in future, we need to schedule change from miss to suspend
        private fun setMissPlusSchedule(context: Context, alarm: Alarm, repo: AlarmRepo) {
            alarm.state=Alarm.STATE_SUSPEND
            val newTime = getPlusDate(2, alarm.lastTriggerTime)
            handleLog(Alarm.STATE_MISS, alarm.id, Alarm.STATE_SUSPEND)
            repo.update(alarm, false)

            BU.createNotification(context, alarm.id, Alarm.STATE_MISS)
            BU.scheduleExact(context, alarm.id, alarm.state, newTime.time)
            BU.setGlobalID(context, repo)
        }
        private fun setSuspend(context: Context, alarm: Alarm, repo: AlarmRepo){
            alarm.state = Alarm.STATE_ANTICIPATE
            val newTime = getPlusDate(-10, alarm.triggerTime)
            handleLog(Alarm.STATE_SUSPEND, alarm.id, Alarm.STATE_ANTICIPATE)
            repo.update(alarm, false)

            BU.cancelNotification(context, alarm.id, Alarm.STATE_ALL)
            BU.scheduleExact(context, alarm.id, Alarm.STATE_ANTICIPATE, newTime.time)
            BU.setGlobalID(context, repo)
        }
        private fun setAnticipate(context: Context, alarm: Alarm, repo: AlarmRepo){
            alarm.state = Alarm.STATE_PREPARE
            val newTime = getPlusDate(-2, alarm.triggerTime)
            handleLog(Alarm.STATE_ANTICIPATE, alarm.id, Alarm.STATE_PREPARE)
            repo.update(alarm, false)

            BU.scheduleExact(context, alarm.id, Alarm.STATE_PREPARE, newTime.time)
            BU.scheduleAlarm(context, alarm.id, Alarm.STATE_FIRE, alarm.triggerTime!!.time)
            BU.setGlobalID(context, repo)
        }
        private fun setPrepareFire(context: Context, alarm: Alarm, repo: AlarmRepo){
            alarm.state = Alarm.STATE_FIRE
            handleLog(Alarm.STATE_PREPARE, alarm.id, Alarm.STATE_FIRE)
            repo.update(alarm, false)

            BU.createNotification(context, alarm.id, Alarm.STATE_PREPARE)
            BU.cancelPI(context, alarm.id, Alarm.STATE_FIRE)//control cancelling in case when previous was anticipate
            BU.scheduleAlarm(context, alarm.id, Alarm.STATE_FIRE, alarm.triggerTime!!.time)
            BU.setGlobalID(context, repo)
        }
        private fun setPrepareSnooze(context: Context, alarm: Alarm, repo: AlarmRepo){
            alarm.state = Alarm.STATE_SNOOZE
            handleLog(Alarm.STATE_PREPARE_SNOOZE, alarm.id, Alarm.STATE_SNOOZE)
            repo.update(alarm, false)

            BU.createNotification(context, alarm.id, Alarm.STATE_PREPARE_SNOOZE)
            BU.scheduleAlarm(context, alarm.id, Alarm.STATE_SNOOZE, alarm.triggerTime!!.time)
            BU.setGlobalID(context, repo)
        }

        //These two little helpers are meant to be launched from FCR when we need AED's functionality,
        //but we're too afraid to send broadcast to AED, exit FCR and then jump back to FCR when these operations are done.
        //Also, we don't have to update Alarm instance's data in DB because it's already in a proper state
        @JvmStatic
        fun handleFire(context: Context, alarm: Alarm){
            BU.cancelNotification(context, alarm.id, Alarm.STATE_PREPARE)
        }
        @JvmStatic
        fun handleSnooze(context: Context, alarm: Alarm) {
            BU.cancelNotification(context, alarm.id, Alarm.STATE_PREPARE_SNOOZE)
            BU.cancelNotification(context, alarm.id, Alarm.STATE_SNOOZE)
        }
        private fun launchFire(context: Context, alarm: Alarm, repo: AlarmRepo){
            alarm.state = Alarm.STATE_FIRE
            handleLog(alarm.state, alarm.id)
            repo.update(alarm, false)

            BU.cancelNotification(context, alarm.id, Alarm.STATE_PREPARE)
            BU.createBroadcast(context, alarm.id)
        }
        private fun launchSnooze(context: Context, alarm: Alarm, repo: AlarmRepo) {
            alarm.state = Alarm.STATE_SNOOZE
            handleLog(alarm.state, alarm.id)
            repo.update(alarm, false)

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
            alarm.triggerTime = getPlusDateMins(repo.getTimesInfo(alarm.id).globalSnoozed.toShort())
            alarm.snoozed = true
            setPrepareSnooze(context, alarm, repo)

            stopService(context)
        }

        /**
         * If Detection required, we just starting service.
         * If no more than Dismiss than disabling snoozed flag, and if alarm is NOT repeatable,
         * disabling it and nulling triggerTime. Than defining new state and stopping service
         */
        @JvmStatic
        fun helpDismiss(context: Context, id: String, repo: AlarmRepo){
            sl.i("^Dismiss^")

            val alarm = repo.getOne(id)
            if (alarm.detection){
                sl.i("^Activeness detection required. Starting ADS")

                val intent = Intent(context, ActivenessDetectionService::class.java)
                intent.putExtra("info", repo.getTimesInfo(alarm.id))
                context.startForegroundService(intent)
            }
            else{
                sl.i("^No need for activeness detection. Preparing for exit")

                alarm.snoozed = false
                processRepeatable(alarm)
                defineNewState(context, alarm, repo)

            }
            stopService(context)
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
         * Setting snoozed flag for false, leveling lastTriggerTime for triggerTime, and
         * if NOT repeatable, setting power to false and triggerTime for null. Than defining
         * new state, stopping service and sending to FCR a broadcast with KILL action
         */
        @JvmStatic
        fun helpMiss(context: Context, id: String, repo: AlarmRepo){
            sl.i("^Miss^")
            val alarm = repo.getOne(id)

            alarm.snoozed = false
            alarm.lastTriggerTime = alarm.triggerTime?.clone() as Date
            processRepeatable(alarm)
            defineNewState(context, alarm, repo)

            stopService(context)
            context.sendBroadcast(Intent(FiringControlService.ACTION_KILL))
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
            for (alarm in list) if (alarm.state!=Alarm.STATE_DISABLE){
                BU.cancelNotification(context, alarm.id, Alarm.STATE_ALL)
                BU.cancelPI(context, alarm.id, Alarm.STATE_ALL)
                defineNewState(context, alarm, repo)
            }
            sl.f("!all checked!")
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