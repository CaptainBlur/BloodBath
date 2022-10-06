package com.vova9110.bloodbath

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.activity.result.ActivityResultCallback
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationCompat.Action
import com.vova9110.bloodbath.AlarmSupervisor.Constants
import com.vova9110.bloodbath.Database.Alarm
import com.vova9110.bloodbath.Database.AlarmRepo
import javax.inject.Inject

class NotificationService : Service() {
    private val TAG = "TAG_ANotificationServ"
    var repo: AlarmRepo? = null
        @Inject set
    private var current: Alarm? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")
        val manager: NotificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        DaggerAppComponent.builder().dBModule(DBModule(application)).build().inject(this)
        val actives = repo!!.actives
        if (actives.isNotEmpty()) current = actives[0] else{
            Log.d(TAG, "no actives found. False run!")
            return super.onStartCommand(intent, flags, startId)
        }

        manager.notify(firingNotificationID, createFiringNotification(applicationContext).build())
        Log.d(TAG, "Firing notification")

        return super.onStartCommand(intent, flags, startId)
        }
    
    private fun createFiringNotification(applicationContext: Context): NotificationCompat.Builder {

        val supervisorIntent = Intent(applicationContext, AlarmSupervisor::class.java)
        with(supervisorIntent) {
            this.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK + Intent.FLAG_ACTIVITY_CLEAR_TASK + Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
            this.putExtra("repo", repo)
            if (current?.isDelayed == false) this.putExtra("action", Constants.MAIN_CALL)
            else this.putExtra("action", Constants.DELAYED)
        }

        val fullscreenIntent = PendingIntent.getActivity(applicationContext, 0, supervisorIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val dismiss = Action(null, "Dismiss", PendingIntent.getActivity(applicationContext, 1, supervisorIntent.putExtra("action", Constants.DISMISSED),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        val delay = Action(null, "Delay", PendingIntent.getActivity(applicationContext, 2, supervisorIntent.putExtra("action", Constants.DELAYED_N),
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
        if (current?.isDelayed == false) builder.addAction(delay)
        return builder
    }
    companion object{
        const val firingNotificationID = 10
    }

    override fun onBind(intent: Intent): IBinder {
        TODO("Return the communication channel to the service.")
    }
}
