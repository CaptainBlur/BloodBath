package com.vova9110.bloodbath.AlarmScreen

import android.Manifest
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioRecord
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.vova9110.bloodbath.R
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.core.Observer
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.observers.DisposableObserver
import io.reactivex.rxjava3.subjects.AsyncSubject
import io.reactivex.rxjava3.subjects.PublishSubject
import java.util.*
import kotlin.math.pow
import kotlin.math.sqrt


private val TAG = "TAG_Act-de-ser"

class ActivenessDetectionService: Service() {
    private lateinit var customThread: Thread
    private var activenessHandler: Handler? = null
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var controller: Controller

    private var launched = false
    private var currentID = -1

    override fun onCreate() {
        Log.d(TAG, "onCreate")
        customThread = Thread {
            Looper.prepare()
            activenessHandler = Handler(Looper.myLooper()!!)
            Looper.loop()
        }.apply { start() }
        Timer().schedule(object : TimerTask() {
            override fun run() { if (activenessHandler != null) cancel() }
        }, 10, 5)
        wakeLock =
            (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                //todo add maximum detecting time here
                "BloodBath:DetectionService").also { it.acquire(10 * 60 * 1000L /*10 minutes*/) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand received")
        //todo add check on different received enum
        val stopCall = intent?.getBooleanExtra("stopCall", false)
        if (stopCall==false && !launched){
            Log.d(TAG, "onStartCommand: launching")
            controller = Controller (::stopService, applicationContext, activenessHandler)
            launched = true
        }
        else if (!launched) Log.e(TAG, "onStartCommand: trying to stop, but not launched yet")
        else if (stopCall==true) controller.stop()

        return super.onStartCommand(intent, flags, startId)
    }
    //The thing is, we only call this function from Controller, and not directly from here
    private fun stopService(){
        Log.d(TAG, "stopService")
        customThread.interrupt()
        wakeLock.release()
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder = TODO("Not yet implemented")
}


private class Controller(val stopService: ()->Unit, val context: Context, val handler: Handler?){
    lateinit var sManager: SensorManager
    lateinit var eventListener: SensorEventListener
    lateinit var recorder: AudioRecord

    val maximalTime = 10 * 60 * 1000L

    val compDis: CompositeDisposable = CompositeDisposable()

    private companion object Constants {
        const val FLAG_STALLED = -1
        const val FLAG_STEPS_ENDED = -2
        const val FLAG_SOUND_ENDED = -3

        const val NOT_CREATE = 1
        const val NOT_END = 2
        const val NOT_PROGRESS = 4
        const val NOT_DELETE = 5
    }

    init {
        handleNotification(NOT_CREATE, null)

        //We will create all of them in order to unsubscribe without NPE
        //Besides, complete doesn't mean, we can't receive messages in observer, so we need to dispose it anyway
        val contObserver = object: DisposableObserver<Int>()
        {//Means continuous observer
            override fun onNext(t: Int) {
                if (t!= FLAG_STALLED) handleNotification(NOT_PROGRESS, t)
            println(t)
            }
            override fun onError(e: Throwable):Unit = throw e
            override fun onComplete() {}
        }.also { compDis.add(it) }//in case of urgent exit we dispose it along with others

        //Collecting all terminal observables in one
        val endObservable = startSteps(contObserver)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)
            startSound().also { }

        endObservable.subscribe {
            println("detection ended!")
            handleNotification(NOT_END, it)

            contObserver.dispose()
        }
    }

    //Here, we just handle user notifications, not performing control for actions at all
    private fun handleNotification(key: Int, value: Int?){
        val manager: NotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, "firing")
            .setSmallIcon(R.drawable.ic_clock_alarm)
            .setContentTitle("Activeness started")
            .setContentText("text to be edited")
            .setProgress(100,0, true)
            .setOngoing(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
        val notificationID = 20

        when (key){
            NOT_CREATE->{
                manager.notify(notificationID, notification.build())
            }
            NOT_PROGRESS->{
                notification
                    .setProgress(100, value!!, false)
                manager.notify(notificationID, notification.build())
            }
            NOT_END->{
                notification
                    .setProgress(100,100,false)
                    .setOnlyAlertOnce(false)
                manager.notify(notificationID, notification.build())
                handler?.postDelayed({ stop() }, 6000)
            }
            NOT_DELETE->{
                manager.cancel(notificationID)
            }
        }
    }

    private fun startSteps(observer: Observer<Int>): AsyncSubject<Int>{
        val returnSubj = AsyncSubject.create<Int>()
        val subj = PublishSubject.create<Int>()
        subj.subscribeWith(observer)
        subj.takeUntil(returnSubj)

        sManager = context.getSystemService(android.hardware.SensorManager::class.java)
        val sensor = sManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

        val bounceTime = 6 //peek time in periods
        val threshold = 2.2f //peek threshold in m/s
        val idleTime = 200L //time in which point can't be acquired again, in mS
        val sampleTime = 25000 //time in the terms of SensorManager class

        fun stepsCapsule(): SensorEventListener{//Just a wrapper around 'detectPoint' and 'stepsSupervisor' functions
            val activeTime = 10 * 1000 //time in which user needs to be active in order to earn all points, in mS
            var lastPoint = -1
            var lastValue = -1
            val stepsSupervisor: (Int)-> Unit = { pC ->
                if (pC*idleTime < activeTime && pC!=lastPoint && pC!= FLAG_STALLED) {//if not stalled
                    val pointsTotal = activeTime/idleTime
                    val percent = (((pC.toFloat())/pointsTotal)*100).toInt()
                    if (lastValue!=percent) subj.onNext(percent)
                    lastValue = percent
                    lastPoint = pC
                }
                else if (pC==FLAG_STALLED && lastValue!= FLAG_STALLED){//activeness stalled
                    subj.onNext(FLAG_STALLED)
                    lastValue = FLAG_STALLED
                }
                else if (pC*idleTime >= activeTime){
                    returnSubj.onNext(FLAG_STEPS_ENDED)
                    returnSubj.onComplete()
                }
            }

            var pointsCount = 0
            val startTime = SystemClock.elapsedRealtimeNanos()
            var lastPeakTime = 0L
            var cnt = 0
            val resultMas = DoubleArray(1000000)
            val detectPoint: (SensorEvent)-> Unit = {
                val elapsedTime: Long =
                    (it.timestamp - startTime) / 1000000 //time since detection started

                val result = sqrt(
                    it.values[0].toDouble().pow(2.0)
                        + it.values[1].toDouble().pow(2.0)
                        + it.values[2].toDouble().pow(2.0))
                resultMas[cnt] = result
                var sum = 0.0
                var avg = 0.0f

                if (cnt > bounceTime) { //excluding first 6 results to avoid error
                    for (i in cnt - bounceTime until cnt) {
                        sum += resultMas[i]
                    }
                    avg = sum.toFloat() / bounceTime.toFloat()
                    if (avg > threshold && elapsedTime >= lastPeakTime + idleTime) {//Point acquiring condition
                        pointsCount++
                        lastPeakTime = elapsedTime
                        stepsSupervisor(pointsCount)
                    }
                    else if (elapsedTime >= lastPeakTime + idleTime) {
                        stepsSupervisor(FLAG_STALLED)
                    }
                }
                cnt++
            }

            return object: SensorEventListener{
                override fun onSensorChanged(event: SensorEvent) = detectPoint(event)

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int): Unit = TODO("Not yet implemented")

            }
        }

        with (stepsCapsule()){
            eventListener = this
            sManager.registerListener(this, sensor, sampleTime, handler)
        }

        return returnSubj
    }

    private fun startSound(){
        Log.d(TAG, "\tcontroller starting with sound")
    }

    fun stop(){
        compDis.dispose()
        sManager.unregisterListener(eventListener)
        handleNotification(NOT_DELETE, null)
        stopService()
    }
}
