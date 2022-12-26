package com.vova9110.bloodbath.alarmScreenBackground

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.Action
import com.vova9110.bloodbath.database.Alarm
import com.vova9110.bloodbath.R
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable
import java.util.concurrent.TimeUnit

class NotificationService : Service() {
    private val TAG = "TAG_ANotificationServ"
    private lateinit var repo: AlarmRepo
    private var current: Alarm? = null
    private lateinit var manager: NotificationManager

    private var player: MediaPlayer? = null
    private lateinit var wakeLock: PowerManager.WakeLock

    private lateinit var dis: Disposable

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")
        if (intent?.getBooleanExtra("stop", false)==false) {
            manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            @Suppress("DEPRECATION")
            repo
            val actives = repo.actives

            if (actives.isNotEmpty()) current = actives[0] else {
                Log.d(TAG, "no actives found. False run!")
                return super.onStartCommand(intent, flags, startId)
            }

            manager.notify(firingNotificationID,
                createFiringNotification(applicationContext).build())
            Log.d(TAG, "Firing notification")

//            val info = SubInfo(null,,
//                null,
//                volumeLock = true,
//                timeWarning = 40,
//                timeOut = 90,
//                globalSnoozed = 8,
//                globalLost = 15
//            )
//            createMusic(info)
//            createCountdown(info)
        }
        else stop()//In the Stop case, execution stops there

        return super.onStartCommand(intent, flags, startId)
        }
    
    private fun createFiringNotification(applicationContext: Context): NotificationCompat.Builder {

        val supervisorIntent = Intent(applicationContext, AlarmSupervisor::class.java)
        with(supervisorIntent) {
            this.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK + Intent.FLAG_ACTIVITY_CLEAR_TASK + Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
//            this.putExtra("repo", repo)//We'll pass repo in the Supervisor anyway
//            if (current?.isDelayed == false) this.putExtra("action", AlarmSupervisor.MAIN_CALL)
//            else this.putExtra("action", AlarmSupervisor.DELAYED)
        }

        val fullscreenIntent = PendingIntent.getActivity(applicationContext, 0, supervisorIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val dismiss = Action(null, "Dismiss", PendingIntent.getActivity(applicationContext, 1, supervisorIntent.putExtra("action", AlarmSupervisor.DISMISSED),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        val delay = Action(null, "Delay", PendingIntent.getActivity(applicationContext, 2, supervisorIntent.putExtra("action", AlarmSupervisor.DELAYED_N),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))

        val builder = NotificationCompat.Builder(applicationContext, "firing")
            .setSmallIcon(R.drawable.ic_clock_alarm)
            .setContentTitle("Alarm in some time")
            .setContentText("Time to wake up!")
            .setOngoing(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(fullscreenIntent, true)
            .addAction(dismiss)
//        if (current?.isDelayed == false) builder.addAction(delay)
        return builder
    }
    companion object{
        const val firingNotificationID = 10
    }

    private fun createMusic(info: SubInfo){
        wakeLock =
            (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "BloodBath:MusicService").also { it.acquire(info.globalLost * 1000 + 5000) }
        player = returnPlayer(applicationContext, info).apply { start() }
    }

    //We call the supervisor here in order for hih to call this service and stop the music and countdown
    //Because we either need to update the repo
    private fun createCountdown(info: SubInfo){
        val observable: Observable<Long> = Observable.timer(info.globalLost, TimeUnit.SECONDS)
        dis = observable.subscribe{
            val supervisorIntent = Intent(applicationContext, AlarmSupervisor::class.java)
            with(supervisorIntent) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK + Intent.FLAG_ACTIVITY_CLEAR_TASK + Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
//                putExtra("repo", repo)
                putExtra("action", AlarmSupervisor.DISMISSED)
            }
            applicationContext.startActivity(supervisorIntent)
        }
    }

    private fun stop(){
        Log.d(TAG, "stopping firing indication")
        manager.cancel(firingNotificationID)
        dis.dispose()
        player?.release()
        player = null
        wakeLock.release()
        stopSelf()
    }


    override fun onBind(intent: Intent): IBinder {
        TODO("Return the communication channel to the service.")
    }
}
