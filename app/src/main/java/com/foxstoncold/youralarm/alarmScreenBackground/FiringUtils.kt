package com.foxstoncold.youralarm.alarmScreenBackground

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import com.foxstoncold.youralarm.MainViewModel
import com.foxstoncold.youralarm.R
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.Disposable
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.ln

internal class FiringUtils(private val service: Service) {
    private val exHandler = CoroutineExceptionHandler { _, throwable ->
        run {
            sl.s(throwable)
            BackgroundUtils.requestErrorNotification(service)
        }
    }
    private val alarmingScope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob() + exHandler)

    lateinit var info: SubInfo
    private val id = 901


    fun setInterlayerNotification(){
        val nManager = service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder =
            NotificationCompat.Builder(service, MainViewModel.FIRING_CH_ID)
                .setSmallIcon(R.drawable.ic_clock_alarm)
                .setAutoCancel(false)
                .setOngoing(true)
                .setShowWhen(false)
                .setCategory(NotificationCompat.CATEGORY_ALARM)

        nManager.notify(id, builder.build())
        service.startForeground(this.id, builder.build())
    }


    fun setBasisNotification(info: SubInfo) {
        this.info = info

        //Values are assigned dynamically depending on current trigerTime
        val h = info.firingHour
        val m = info.firingMinute
        val title = (if (info.preliminary) "Preliminary" else "Alarm") +
                " for ${String.format(Locale.ENGLISH, "%02d:%02d", h, m)}"

        val nManager = service.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder =
            NotificationCompat.Builder(service, MainViewModel.FIRING_CH_ID)
                .setSmallIcon(R.drawable.ic_clock_alarm)
                .setContentTitle(title)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(false)
                .setOngoing(true)
                .setShowWhen(false)
                .setCategory(NotificationCompat.CATEGORY_ALARM)

        //Dismiss will be shown anyway
        val dismissIntent = Intent(FiringControlService.ACTION_DISMISS).apply {
            putExtra(notifCallExtra, true)
        }
        val dismissPI = PendingIntent.getBroadcast(
            service,
            1,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(NotificationCompat.Action(null, "Dismiss", dismissPI))

        //Snoozed relies on field placed inside Alarm entity
        if (!info.snoozed && !info.preliminary) {
            val snoozeIntent = Intent(FiringControlService.ACTION_SNOOZE).apply {
                putExtra(notifCallExtra, true)
            }
            val snoozePI = PendingIntent.getBroadcast(
                service,
                2,
                snoozeIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(NotificationCompat.Action(null, "Snooze", snoozePI))
        }

        val fullScreenIntent = Intent(service, AlarmActivity::class.java)
        fullScreenIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_NO_USER_ACTION
        fullScreenIntent.putExtra(infoExtra, info)
        val fullScreenPI = PendingIntent.getActivity(
            service,
            3,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.setFullScreenIntent(fullScreenPI, true)

        nManager.notify(id, builder.build())
        service.startForeground(this.id, builder.build())
    }

    private lateinit var expireDis: Disposable
    private lateinit var vibrator: Vibrator
    private var player: MediaPlayer? = null

    @SuppressLint("CheckResult")
    fun launchRest(expire: () -> Unit) {
        expireDis = Observable.timer(info.globalLost, TimeUnit.MINUTES).subscribe { expire() }
        sl.fst("expire time is set for ${info.globalLost} min")

        val streamType = AudioManager.STREAM_ALARM
        val audioManager = service.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        var volIndex = if (info.volume!=-1) info.volume else audioManager.getStreamVolume(streamType)

        val initialSetup = alarmingScope.launch {

            if (info.volumeLock) audioManager.setStreamVolume(streamType, volIndex, AudioManager.FLAG_SHOW_UI)
            player = BackgroundUtils.returnPlayer(service, info)
            player!!.start()
            sl.fst("player set up")
        }

        if (info.volumeLock)
            alarmingScope.launch {
                initialSetup.join()
                sl.fst("vol lock set up: $volIndex")

                while (true){
                    delay(500)
                    if (audioManager.getStreamVolume(streamType) != volIndex){
                        sl.fst("vol mismatch (${audioManager.getStreamVolume(streamType)} against $volIndex)")
                        delay(5000)

                        player!!.pause()
                        audioManager.setStreamVolume(streamType, volIndex, AudioManager.FLAG_SHOW_UI)
                        /* AudioManager won't allow us to programmatically set volume below some threshold,
                        so here we adjust to avoid pointless looping */
                        volIndex = audioManager.getStreamVolume(streamType)
                        player!!.start()
                    }
                }
            }

        if (info.rampUpVolume && info.rampUpVolumeTime!=0)
            alarmingScope.launch {
                initialSetup.join()
                sl.fst("ramp up volume set up: ${info.rampUpVolumeTime}")

                val maxVolume = info.rampUpVolumeTime.toFloat()
                for (currVolume in maxVolume.toInt()-1 downTo  0){
                    val log1 = (ln(maxVolume - currVolume) / ln(maxVolume))

                    player!!.pause()
                    player!!.setVolume(log1, log1)
                    player!!.start()
                    delay(1000)
                }
            }

        if (info.vibrate) {
            vibrator = service.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

            val timings: LongArray = longArrayOf(450, 50, 50, 50, 50, 150, 120, 50, 50, 50, 50, 200)
            val amplitudes: IntArray =
                intArrayOf(0, 27, 45, 68, 104, 145, 0, 52, 79, 134, 198, 255)

            vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, 0))
            sl.fst("vibration set up")
        }
    }

    fun stopAll(){
        if (this::expireDis.isInitialized) expireDis.dispose()
        if (this::vibrator.isInitialized) vibrator.cancel()
        player?.stop()
        player?.release()
        player = null

        alarmingScope.coroutineContext.cancelChildren()
    }



    companion object{
        const val notifCallExtra = "notif_call"
        const val infoExtra = "info"
    }
}