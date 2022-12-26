package com.vova9110.bloodbath.alarmScreenBackground

import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.*
import android.os.PowerManager.WakeLock
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.vova9110.bloodbath.MyApp
import com.vova9110.bloodbath.database.Alarm
import javax.inject.Inject

class FiringControlService: Service() {

    private lateinit var wakeLock: WakeLock
    private lateinit var repo: AlarmRepo
    private lateinit var alarm: Alarm
    private lateinit var ntph: NotificationTimerPlayerHelper
    private var activityBound = false
    private var receiverRegistered = false
    private var launched = false
    private var noiseActivated = false

    private val expire = Runnable {
        AlarmExecutionDispatch.helpMiss(applicationContext, alarm.id, repo)
    }

    @SuppressLint("WakelockTimeout")
    override fun onCreate() {
        super.onCreate()
        repo = (applicationContext as MyApp).component.repo

        wakeLock =
            (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "${applicationContext.packageName}:${this::class.java.name}").also { it.acquire() }
        sl.en()
        sl.f("globalID is: *${BackgroundUtils.getGlobalID(applicationContext)}* now")
        ntph = NotificationTimerPlayerHelper(this, expire)

        registerReceiver(receiver, filter)
        receiverRegistered = true
    }

    override fun onDestroy() {
        sl.ex()
        super.onDestroy()
        if (receiverRegistered){
            unregisterReceiver(receiver)
            receiverRegistered = false
        }
        if (wakeLock.isHeld) wakeLock.release()
        else sl.w("wakelock is not being held")
        launched = false
    }

    override fun onBind(intent: Intent?): IBinder {
        activityBound = true
        return Binder()
    }
    override fun onUnbind(intent: Intent?): Boolean {
        activityBound = false
        return false
    }

    private fun stopService(){
        ntph.handler.dispatchMessage(Message().apply {
            arg1 = NotificationTimerPlayerHelper.MSG_STOP
        })
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent!!.action== STOP_SERVICE){
            stopService()
            return START_NOT_STICKY
        }
        processIntent(intent)
        return START_NOT_STICKY
    }

    private val receiver = object: BroadcastReceiver(){
        override fun onReceive(context: Context?, intent: Intent?) {
            sl.fr("broadcast received")
            intent!!

            if (activityBound) return

            sl.fp("handling alarm with id: *${alarm.id}*")
            when(intent.action){
                ACTION_SNOOZE-> AlarmExecutionDispatch.helpSnooze(applicationContext, alarm.id, repo)
                ACTION_DISMISS-> AlarmExecutionDispatch.helpDismiss(applicationContext, alarm.id, repo)
            }
        }
    }
    private val filter = IntentFilter().apply {
        addAction(ACTION_SNOOZE)
        addAction(ACTION_DISMISS)
    }


    private fun processIntent(intent: Intent){
        val current = repo.getOne(intent.action)
        val info = repo.getTimesInfo(current.id)
        val globalID = BackgroundUtils.getGlobalID(applicationContext)

        if (globalID == "null") throw IllegalStateException("Cannot proceed with *null* globalID")
        if (current.id != globalID){
            sl.w("Passed alarm's id *${current.id}* do not match with global *$globalID*")

            current.snoozed = false
            if (!current.repeatable){
                current.enabled = false
                current.triggerTime = null
            }
            AlarmExecutionDispatch.defineNewState(applicationContext, current, repo)
            return
        }
        if (current.triggerTime==null) throw IllegalArgumentException("Cannot proceed without triggerTime")
        if (current.state!=Alarm.STATE_FIRE && current.state!=Alarm.STATE_SNOOZE) throw IllegalArgumentException("Cannot proceed with *${current.state}* state")
        if ((current.state==Alarm.STATE_FIRE && current.snoozed) || (current.state==Alarm.STATE_SNOOZE && !current.snoozed)) throw IllegalArgumentException("Cannot proceed with *${current.state}* state when alarm.snoozed is: *${current.snoozed}*")
        if (launched) {
            sl.w("Recursive call detected, process already began. Returning")
            return
        }

        sl.i("all checks has been passed")
        sl.fp("handling alarm with id: *${current.id}*")
        alarm = current
        launched = true
        noiseActivated = true

        ntph.setBasisNotification(this, repo.getTimesInfo(current.id))
        ntph.handler.dispatchMessage(Message().apply {
            arg1 = NotificationTimerPlayerHelper.MSG_START
            obj = info
        })
    }

    companion object{
        const val ACTION_FIRE = "fire"
        const val ACTION_SNOOZE = "snooze"
        const val ACTION_DISMISS = "dismiss"
        const val ACTION_KILL = "kill"

        const val STOP_SERVICE = "stop"
    }
}