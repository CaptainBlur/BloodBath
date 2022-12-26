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

class AlarmExecutionDispatch: BroadcastReceiver() {

    private lateinit var repo: AlarmRepo

    @SuppressLint("WakelockTimeout")
    override fun onReceive(context: Context?, intent: Intent?) {
        val res = goAsync()
        val wakeLock =
            (context!!.getSystemService(Service.POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "${context.packageName}:${this::class.java.name}").also { it.acquire() }
        sl.en()

        val handler = BackgroundUtils.getHandler("executionDispatch")
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
            Alarm.STATE_FIRE-> launchFire(context, alarm, repo)
            Alarm.STATE_PREPARE_SNOOZE-> setPrepareSnooze(context, alarm, repo)
            Alarm.STATE_SNOOZE-> launchSnooze(context, alarm, repo)
            else-> throw IllegalArgumentException("Cannot handle *${alarm.state}* state")
        }

    }

    companion object{
        @JvmStatic
        fun defineNewState(context: Context, alarm: Alarm, repo: AlarmRepo){
            /*
            This condition takes place only if target instance didn't fire, but supposed to do before
             */
            if (alarm.triggerTime!=null){
                if (!alarm.enabled) throw IllegalStateException("Cannot proceed with set triggerTime and disabled state")

                if (alarm.triggerTime!!.before(Date()) or (alarm.triggerTime!! == Date())){
                    sl.f("missed triggerTime detected!")
                    val info = repo.getTimesInfo(alarm.id)

                    if (getPlusDateMins(info.globalLost.toShort(), alarm.triggerTime).after(Date())){//if we didn't reach lost time for firing state yet
                        if (!alarm.snoozed) launchFire(context, alarm, repo)
                        else launchSnooze(context, alarm, repo)
                        return
                    }
                    else {//time to fire is lost already, we have to set one of the miss states
                        alarm.lastTriggerTime = alarm.triggerTime

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
            Assigning temporary or permanent disable here
            Non-active alarms shouldn't get through
             */
            if (!alarm.enabled && alarm.lastTriggerTime==null){//Setting disable with no doubts
                setDisable(context, alarm, repo)
                return
            }
            else if (alarm.lastTriggerTime!=null){//Checking whether the time for miss state is gone
                if (alarm.lastTriggerTime!!.before(getPlusDate(-2))){//Time is gone, but if alarm should fire in the future, we have to consider a new state in the block below
                    setDisable(context, alarm, repo)
                    if (!alarm.enabled) return//else temporarily setting disable
                }
                else if (!alarm.enabled) {//Just time for miss
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

            BackgroundUtils.cancelNotification(context, alarm.id, Alarm.STATE_ALL)
            BackgroundUtils.cancelPI(context, alarm.id, Alarm.STATE_ALL)
        }
        //When instance is not active, we setting miss and scheduling disable
        private fun setMiss(context: Context, alarm: Alarm, repo: AlarmRepo) {
            alarm.state=Alarm.STATE_DISABLE
            val newTime = getPlusDate(2, alarm.lastTriggerTime)
            handleLog(Alarm.STATE_MISS, alarm.id, Alarm.STATE_DISABLE)
            repo.update(alarm, false)

            BackgroundUtils.createNotification(context, alarm.id, Alarm.STATE_MISS)
            BackgroundUtils.scheduleExact(context, alarm.id, alarm.state, newTime.time)
        }
        //When instance should fire in future, we need to schedule change from miss to suspend
        private fun setMissPlusSchedule(context: Context, alarm: Alarm, repo: AlarmRepo) {
            alarm.state=Alarm.STATE_SUSPEND
            val newTime = getPlusDate(2, alarm.lastTriggerTime)
            handleLog(Alarm.STATE_MISS, alarm.id, Alarm.STATE_SUSPEND)
            repo.update(alarm, false)

            BackgroundUtils.createNotification(context, alarm.id, Alarm.STATE_MISS)
            BackgroundUtils.scheduleExact(context, alarm.id, alarm.state, newTime.time)
        }
        private fun setSuspend(context: Context, alarm: Alarm, repo: AlarmRepo){
            alarm.state = Alarm.STATE_ANTICIPATE
            val newTime = getPlusDate(-10, alarm.triggerTime)
            handleLog(Alarm.STATE_SUSPEND, alarm.id, Alarm.STATE_ANTICIPATE)
            repo.update(alarm, false)

            BackgroundUtils.cancelNotification(context, alarm.id, Alarm.STATE_ALL)
            BackgroundUtils.scheduleExact(context, alarm.id, Alarm.STATE_ANTICIPATE, newTime.time)
        }
        private fun setAnticipate(context: Context, alarm: Alarm, repo: AlarmRepo){
            alarm.state = Alarm.STATE_PREPARE
            val newTime = getPlusDate(-2, alarm.triggerTime)
            handleLog(Alarm.STATE_ANTICIPATE, alarm.id, Alarm.STATE_PREPARE)
            repo.update(alarm, false)

            BackgroundUtils.scheduleExact(context, alarm.id, Alarm.STATE_PREPARE, newTime.time)
            BackgroundUtils.scheduleAlarm(context, alarm.id, Alarm.STATE_FIRE, alarm.triggerTime!!.time)
        }
        private fun setPrepareFire(context: Context, alarm: Alarm, repo: AlarmRepo){
            alarm.state = Alarm.STATE_FIRE
            handleLog(Alarm.STATE_PREPARE, alarm.id, Alarm.STATE_FIRE)
            repo.update(alarm, false)

            BackgroundUtils.createNotification(context, alarm.id, Alarm.STATE_PREPARE)
            BackgroundUtils.cancelPI(context, alarm.id, Alarm.STATE_FIRE)//control cancelling in case when previous was anticipate
            BackgroundUtils.scheduleAlarm(context, alarm.id, Alarm.STATE_FIRE, alarm.triggerTime!!.time)
        }
        private fun setPrepareSnooze(context: Context, alarm: Alarm, repo: AlarmRepo){
            alarm.state = Alarm.STATE_SNOOZE
            handleLog(Alarm.STATE_PREPARE_SNOOZE, alarm.id, Alarm.STATE_SNOOZE)
            repo.update(alarm, false)

            BackgroundUtils.createNotification(context, alarm.id, Alarm.STATE_PREPARE_SNOOZE)
            BackgroundUtils.scheduleAlarm(context, alarm.id, Alarm.STATE_SNOOZE, alarm.triggerTime!!.time)
        }
        private fun launchFire(context: Context, alarm: Alarm, repo: AlarmRepo){
            alarm.state = Alarm.STATE_FIRE
            handleLog(alarm.state, alarm.id)
            repo.update(alarm, false)

            BackgroundUtils.cancelNotification(context, alarm.id, Alarm.STATE_PREPARE)
            BackgroundUtils.createBroadcast(context, alarm.id)
        }
        private fun launchSnooze(context: Context, alarm: Alarm, repo: AlarmRepo) {
            alarm.state = Alarm.STATE_SNOOZE
            handleLog(alarm.state, alarm.id)
            repo.update(alarm, false)

            BackgroundUtils.cancelNotification(context, alarm.id, Alarm.STATE_PREPARE_SNOOZE)
            BackgroundUtils.cancelNotification(context, alarm.id, Alarm.STATE_SNOOZE)
            BackgroundUtils.createBroadcast(context, alarm.id)
        }

        @JvmStatic
        fun helpSnooze(context: Context, id: String, repo: AlarmRepo){
            sl.i("^Snooze^")
            val alarm = repo.getOne(id)
            alarm.triggerTime = getPlusDateMins(repo.getTimesInfo(alarm.id).globalSnoozed.toShort())
            alarm.snoozed = true
            setPrepareSnooze(context, alarm, repo)

            stopService(context)
        }
        @JvmStatic
        fun helpDismiss(context: Context, id: String, repo: AlarmRepo){
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
                if (!alarm.repeatable){
                    alarm.enabled = false
                    alarm.triggerTime = null
                }
                defineNewState(context, alarm, repo)

            }
            stopService(context)
        }
        @JvmStatic
        fun helpAfterDetection(context: Context, id: String){
            sl.i("^After detection^")
            val repo = (context as MyApp).component.repo

            val alarm = repo.getOne(id)
            alarm.snoozed = false
            if (!alarm.repeatable){
                alarm.enabled = false
                alarm.triggerTime = null
            }
            defineNewState(context, alarm, repo)
        }
        @JvmStatic
        fun helpMiss(context: Context, id: String, repo: AlarmRepo){
            sl.i("^Miss^")
            val alarm = repo.getOne(id)

            alarm.snoozed = false
            alarm.lastTriggerTime = alarm.triggerTime
            if (!alarm.repeatable){
                alarm.enabled = false
                alarm.triggerTime = null
            }
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


        @JvmStatic fun wipeOne(context: Context, alarm: Alarm){
            if (alarm.enabled || alarm.lastTriggerTime!=null) {
                BackgroundUtils.cancelNotification(context, alarm.id, Alarm.STATE_ALL)
                BackgroundUtils.cancelPI(context, alarm.id, Alarm.STATE_ALL)
            }
            else sl.fp("no need for states erasure")
        }
        @JvmStatic fun wipeAll(context: Context, list: List<Alarm>){
            for (alarm in list) wipeOne(context, alarm)
        }
    }

}