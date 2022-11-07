package com.vova9110.bloodbath

import android.app.Application
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.core.app.NotificationCompat
import com.vova9110.bloodbath.AlarmScreenBackground.AlarmExec
import com.vova9110.bloodbath.Database.AlarmRepo
import javax.inject.Inject

//Here se will be scheduling Executor's appointments and emitting notifications
//Executor have no control to this scheduling
class ExecReceiver : BroadcastReceiver() {
    var repo: AlarmRepo? = null
        @Inject set
    override fun onReceive(context: Context, intent: Intent) {
//        DaggerAppComponent.builder().dBModule(DBModule(context.applicationContext as Application)).build().inject(this)
//        context.startService(Intent(context, AlarmExec::class.java)
//            .putExtra("repo", repo))
//        val prefs: SharedPreferences =
//            context.getSharedPreferences(MainActivity.PREFERENCES_NAME, Context.MODE_PRIVATE)

        val manager: NotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, "activeness")
            .setSmallIcon(R.drawable.ic_clock_alarm)
            .setContentTitle("Activeness detection")
            .setShowWhen(false)
//            .setContentText(context.contentResolver.toString())
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
        manager.notify(100, notification.build())
    }
}