package com.vova9110.bloodbath.alarmScreenBackground

import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.*
import android.os.PowerManager.WakeLock
import androidx.core.app.NotificationCompat
import com.vova9110.bloodbath.MainViewModel
import com.vova9110.bloodbath.MyApp
import com.vova9110.bloodbath.R
import com.vova9110.bloodbath.database.Alarm


/**
 * Kinda head executive service for AED. It means that all of AlarmManager's appointments set in the AED,
 * will be aiming this service. When handling state change, the controls will be brought back to AED
 */
class FiringControlService: Service() {

    private lateinit var wakeLock: WakeLock
    private lateinit var repo: AlarmRepo
    private lateinit var alarm: Alarm
    private lateinit var ntph: MiscHelper
    private var activityBound = false
    private var receiverRegistered = false
    private var launched = false
    private var noiseActivated = false

    private val expire = Runnable {
        AED.helpMiss(applicationContext, alarm.id, repo)
    }

    @SuppressLint("WakelockTimeout")
    override fun onCreate() {
        super.onCreate()
        repo = (applicationContext as MyApp).component.repo

        wakeLock =
            (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "${applicationContext.packageName}:${this::class.java.name}").also { it.acquire() }
        sl.en()
        ntph = MiscHelper(this, expire)

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
            arg1 = MiscHelper.MSG_STOP
        })
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    @SuppressLint("SuspiciousIndentation")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent!!.action== STOP_SERVICE){
            stopService()
            return START_NOT_STICKY
        }

        if (!checkInterlayer(intent)) processIntent(intent)
            try {
            processIntent(intent)
            }
            catch (e: Exception) {sl.s(e.message!!)}
        return START_NOT_STICKY
    }

    private val receiver = object: BroadcastReceiver(){
        override fun onReceive(context: Context?, intent: Intent?) {
            sl.fr("broadcast received")
            intent!!

            if (activityBound) return

            sl.fp("handling alarm with id: *${alarm.id}*")
            when(intent.action){
                ACTION_SNOOZE-> AED.helpSnooze(applicationContext, alarm.id, repo)
                ACTION_DISMISS-> AED.helpDismiss(applicationContext, alarm.id, repo)
            }
        }
    }
    private val filter = IntentFilter().apply {
        addAction(ACTION_SNOOZE)
        addAction(ACTION_DISMISS)
    }

    private fun checkInterlayer(intent: Intent): Boolean {
        val current = repo.getOne(intent.action)

        return if (current.state!=Alarm.STATE_FIRE && current.state!=Alarm.STATE_SNOOZE){
            val builder = NotificationCompat.Builder(this, MainViewModel.INFO_CH_ID)
                .setSmallIcon(R.drawable.ic_clock_alarm)
                .setShowWhen(false)
                .setContentText("you get lucky if you read this")
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            this.startForeground(BackgroundUtils.UNIVERSAL_NOT_ID, builder.build())

            sl.f("interlayer call detected")
            val newIntent = Intent(this, AED::class.java).apply { this.action = intent.action }
            this.sendBroadcast(newIntent)
            this.stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()

            true
        } else{
            sl.f("NO interlayer call detected")
            false
        }
    }

    private fun processIntent(intent: Intent){
        val current = repo.getOne(intent.action)
        if (current.state==Alarm.STATE_FIRE) AED.handleFire(this, current)
        if (current.state==Alarm.STATE_SNOOZE) AED.handleSnooze(this, current)

        val info = repo.getTimesInfo(current.id)
        val globalID = BackgroundUtils.getGlobalID(applicationContext)
        sl.f("globalID is: *${BackgroundUtils.getGlobalID(applicationContext)}* now")

        if (globalID == "null") throw IllegalStateException("Cannot proceed with *null* globalID")
        if (current.id != globalID){
            AED.helpNotPassed(this, current.id, repo)
            return
        }
        if (current.triggerTime==null) throw IllegalArgumentException("Cannot proceed without triggerTime")
        if (current.state!=Alarm.STATE_FIRE && current.state!=Alarm.STATE_SNOOZE) throw IllegalArgumentException("Cannot proceed with *${current.state}* state")
        if ((current.state==Alarm.STATE_FIRE && current.snoozed) || (current.state==Alarm.STATE_SNOOZE && !current.snoozed)) throw IllegalArgumentException("Cannot proceed with *${current.state}* state when alarm.snoozed is: *${current.snoozed}*")
        if (launched) {
            sl.w("Recursive call detected, process already began. Returning")
            return
        }

        sl.ip("all checks has been passed")
        sl.fp("handling alarm with id: *${current.id}*")
        alarm = current
        launched = true
        noiseActivated = true

        ntph.setBasisNotification(this, repo.getTimesInfo(current.id))
        ntph.handler.dispatchMessage(Message().apply {
            arg1 = MiscHelper.MSG_START
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