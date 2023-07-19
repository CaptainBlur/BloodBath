package com.vova9110.bloodbath.alarmScreenBackground

import android.annotation.SuppressLint
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.*
import android.os.PowerManager.WakeLock
import com.vova9110.bloodbath.MainActivity
import com.vova9110.bloodbath.MyApp
import com.vova9110.bloodbath.database.Alarm


/**
 * Kinda head executive service for AED. It means that all of AlarmManager's appointments set in the AED,
 * will be aiming this service. When handling state change, the controls will be brought back to AED
 */
class FiringControlService: Service() {

    private lateinit var wakeLock: WakeLock
    private lateinit var repo: AlarmRepo
    private lateinit var alarm: Alarm
    private var activityBound = false
    private var receiverRegistered = false
    private var launched = false
    private var noiseActivated = false

    private val expire = {
        //AED will order FCS shortly after it stops defining state
        AED.helpMiss(applicationContext, alarm.id, repo)
        sendBroadcast(Intent(MainActivity.RELOAD_RV_REQUEST))
    }
    private val utils = FiringUtils(this, expire)

    @SuppressLint("WakelockTimeout")
    override fun onCreate() {
        super.onCreate()
        sl.en()
        repo = (applicationContext as MyApp).component.repo

        wakeLock =
            (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "${applicationContext.packageName}:${this::class.java.name}").also { it.acquire() }

        BackgroundUtils.putReloadRequest(this)
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
        sl.f("ordered to stop")
        utils.stopRest()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
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

            sendBroadcast(Intent(MainActivity.RELOAD_RV_REQUEST))
        }
    }
    private val filter = IntentFilter().apply {
        addAction(ACTION_SNOOZE)
        addAction(ACTION_DISMISS)
    }

    @SuppressLint("SuspiciousIndentation")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent!!.action == STOP_SERVICE){
            stopService()
            return START_NOT_STICKY
        }

        try {
            /*
            If interlayer call is confirmed, service will be stopped
            after AED is launched. Also, on true reassure, serviceStop expected
            */
            if (!checkInterlayer(intent)) {
                //Basically, the main purpose of this service
                registerReceiver(receiver, filter)
                receiverRegistered = true
                processIntent(intent)
            }
        }
        catch (e: Exception) {
            sl.s(e)
            BackgroundUtils.requestErrorNotification(this)
        }

        return START_NOT_STICKY
    }

    /**
     * Detecting interlayer call and posting core Service notification
     */
    private fun checkInterlayer(intent: Intent): Boolean {
        val current = repo.getOne(intent.action)

        return if (current.state!=Alarm.STATE_FIRE && current.state!=Alarm.STATE_SNOOZE && current.state!=Alarm.STATE_PRELIMINARY){
            sl.f("interlayer call detected")

            utils.setInterlayerNotification()
            AED.handleStateChange(this.applicationContext, current)
            stopService()

            true
        }
        else{
            sl.f("NO interlayer call detected")
            false
        }
    }

    private fun processIntent(intent: Intent){

        val current = repo.getOne(intent.action)
        if (current.state==Alarm.STATE_FIRE || current.state==Alarm.STATE_PRELIMINARY) AED.killFireNotification(this, current)
        if (current.state==Alarm.STATE_SNOOZE) AED.killSnoozeNotification(this, current)

        val globalID = BackgroundUtils.getGlobalID(applicationContext)
        sl.f("globalID is: *$globalID* now")

        if (current.id != globalID){
            AED.helpNotPassed(this, current.id, repo)
            return
        }

        if (current.triggerTime==null) throw IllegalArgumentException("Cannot proceed without triggerTime")
        if (current.state!=Alarm.STATE_FIRE &&
            current.state!=Alarm.STATE_SNOOZE &&
            current.state!=Alarm.STATE_PRELIMINARY)
                throw IllegalArgumentException("Cannot proceed with *${current.state}* state")
        if (((current.state==Alarm.STATE_FIRE || current.state==Alarm.STATE_PRELIMINARY) && current.snoozed) ||
            (current.state==Alarm.STATE_SNOOZE && !current.snoozed))
                throw IllegalArgumentException("Cannot proceed with *${current.state}* state when alarm.snoozed is: *${current.snoozed}*")
        if (launched) {
            sl.s("Recursive call detected, process already began. Returning")
            return
        }

        sl.ip("all checks has been passed")
        sl.fp("handling alarm with id: *${current.id}*")
        alarm = current
        launched = true
        noiseActivated = true

        utils.setBasisNotification(alarm.getInfo(this))
        utils.launchRest()
    }

    companion object{
        const val ACTION_FIRE = "fire"
        const val ACTION_SNOOZE = "snooze"
        const val ACTION_DISMISS = "dismiss"
        const val ACTION_KILL = "kill"

        const val STOP_SERVICE = "stop"
    }
}