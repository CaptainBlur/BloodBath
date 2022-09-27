package com.vova9110.bloodbath

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService

//It constructs Notification Channel here and posts initial notification just in order to launch Supervisor activity.
//Also, here we need to handle two cases of 'contexts', in which receiver can find itself when receiving broadcast
class AlarmDeployReceiver : BroadcastReceiver() {
    private val TAG = "TAG_AScreenReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Alarm broadcast received!")
        context.startService(Intent(context, NotificationService::class.java))
    }
}