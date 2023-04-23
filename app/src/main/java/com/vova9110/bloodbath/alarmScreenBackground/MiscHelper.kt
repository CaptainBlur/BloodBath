package com.vova9110.bloodbath.alarmScreenBackground

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.os.*
import androidx.core.app.NotificationCompat
import com.vova9110.bloodbath.MainViewModel
import com.vova9110.bloodbath.R
import com.vova9110.bloodbath.database.Alarm
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable
import java.util.concurrent.TimeUnit

class MiscHelper(context: Context, expireRunnable: Runnable) {
    val handler: Handler
    var started = false
    private lateinit var nManager: NotificationManager
    private lateinit var dis: Disposable
    private var player: MediaPlayer? = null
    private val vManager: Vibrator

    val id = 732

    init {
        vManager = (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)

        val thread = HandlerThread("playerHandler")
        thread.start()
        started = true

        handler = object: Handler(thread.looper){
            override fun handleMessage(msg: Message) {
                super.handleMessage(msg)

                when(msg.arg1){
                    MSG_START->{
                        val info = msg.obj as SubInfo

                        player = returnPlayer(context, info)
                        player?.start()

                        if (info.vibrate){
                            val effect = VibrationEffect.createWaveform(arrayOf(500L, 500L).toLongArray(), 0)
                            vManager.vibrate(effect)
                        }

                        dis = Observable.timer(info.globalLost, TimeUnit.MINUTES).subscribe{ this.post(expireRunnable) }

//                        postDelayed({ dispatchMessage(Message().apply { arg1 = MSG_CHECK }) }, 100)
                    }
                    MSG_CHECK->{
                        if (started) postDelayed({ dispatchMessage(Message().apply { arg1 = MSG_CHECK }) }, 100)
                    }

                    MSG_STOP->{
                        started = false
                        vManager.cancel()
                        player?.stop()
                        player?.release()
                        player = null
                        dis.dispose()
                    }
                }
            }
        }
    }

    fun setBasisNotification(service: Service, info: SubInfo) {
        nManager = service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder =
            NotificationCompat.Builder(service, MainViewModel.FIRING_CH_ID)
                .setSmallIcon(R.drawable.ic_clock_alarm)
                .setContentTitle("Alarm in ${info.id}")
                .setContentText("Time to wake up!")
                .setAutoCancel(false)
                .setOngoing(true)
                .setShowWhen(false)
                .setCategory(NotificationCompat.CATEGORY_ALARM)

        if (!info.snoozed){
            val snoozeIntent = Intent(FiringControlService.ACTION_SNOOZE).apply {
                putExtra("notifCall", true)
            }
            val snoozePI = PendingIntent.getBroadcast(service, 1, snoozeIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(NotificationCompat.Action(null, "Snooze", snoozePI))
        }

        val dismissIntent = Intent(FiringControlService.ACTION_DISMISS).apply {
            putExtra("notifCall", true)
        }
        val dismissPI = PendingIntent.getBroadcast(service, 2, dismissIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        builder.addAction(NotificationCompat.Action(null, "Dismiss", dismissPI))

        val fullScreenIntent = Intent(service, AlarmActivity::class.java)
        fullScreenIntent.action =
            if (!info.snoozed) FiringControlService.ACTION_FIRE
            else FiringControlService.ACTION_SNOOZE
        fullScreenIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_NO_USER_ACTION
        fullScreenIntent.putExtra("info", info)
        val fullScreenPI = PendingIntent.getActivity(service, 3, fullScreenIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        builder.setFullScreenIntent(fullScreenPI, true)

        nManager.notify(id, builder.build())
        service.startForeground(this.id, builder.build())
    }

    companion object{
        const val MSG_START = 0
        private const val MSG_CHECK = 1
        const val MSG_STOP = 2
    }
}