package com.vova9110.bloodbath.AlarmScreenBackground

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.*
import android.text.Html
import android.util.Log
import android.util.SparseIntArray
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.util.keyIterator
import com.vova9110.bloodbath.AlarmScreenBackground.StftFilter.NewWindowListener
import com.vova9110.bloodbath.AlarmScreenBackground.StftFilter.Processor
import com.vova9110.bloodbath.Database.TimeSInfo
import com.vova9110.bloodbath.R
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Observer
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.observers.DisposableObserver
import io.reactivex.rxjava3.subjects.AsyncSubject
import io.reactivex.rxjava3.subjects.PublishSubject
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sqrt


private val TAG = "TAG_Act-de-ser"

class ActivenessDetectionService: Service() {
    private lateinit var customThread: Thread
    private var activenessHandler: Handler? = null
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var controller: Controller

    private var launched = false//Setting true only when detection starts, and no manually resetting it
    private var stopped = false

    @SuppressLint("WakelockTimeout")
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
                "BloodBath:DetectionService").also { it.acquire(30 * 60 * 1000) }
    }

    @Suppress("DEPRECATION")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand received")
        val stopCall = intent?.getBooleanExtra("stopCall", false)
        val info = if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.TIRAMISU) intent?.getParcelableExtra("info", TimeSInfo::class.java)
        else intent?.getParcelableExtra("info")
        //todo add demo mode

        if (stopCall==false && !launched){
            info!!
            Log.d(TAG, "onStartCommand, launching with passed info: ${info.firingHour}:${info.firingMinute}")

            controller = Controller (::stopService, applicationContext, activenessHandler!!, info, this)
            launched = true
        }
        else if (!launched) Log.e(TAG, "onStartCommand: trying to stop, but not launched yet")
        else if (stopCall==true) with(controller) {cancelNotifications(); stop()}

        return super.onStartCommand(intent, flags, startId)
    }
    //The thing is, we only call this function from Controller, and not directly from here
    private fun stopService(){
        Log.d(TAG, "stopService")
        stopped = true//In the end of work, Controller will call this function anyway
        customThread.interrupt()
        wakeLock.release()
        stopSelf()
    }

    override fun onDestroy() {//Use it in case of emergency exit
        if (!stopped){
            Log.d(TAG, "stopService")
            stopped = true//In the end of work, Controller will call this function anyway
            customThread.interrupt()
            wakeLock.release()
        }
    }

    override fun onBind(intent: Intent?): IBinder = TODO("Not yet implemented")
}


private class Controller(val stopService: ()->Unit, val context: Context, val handler: Handler, val info: TimeSInfo, val service: Service){
    lateinit var sManager: SensorManager
    lateinit var eventListener: SensorEventListener
    lateinit var recorder: AudioRecord
    var player: MediaPlayer? = null

    val compDis: CompositeDisposable = CompositeDisposable()

    private companion object {
        const val FLAG_STALLED = -1
        const val FLAG_WARNING = -7
        const val FLAG_SNOOZED = -6
        const val FLAG_EXPIRED = -5
        const val FLAG_STEPS_ENDED = -2
        const val FLAG_SOUND_ENDED = -3

        const val NOT_CREATE = 1
        const val NOT_END = 2
        const val NOT_END_EXTERNAL = 8
        const val NOT_STALLED = 3
        const val NOT_SNOOZED = 7
        const val NOT_WARNING = 6
        const val NOT_PROGRESS = 4
    }

    init {
        handleNotifications(NOT_CREATE)
        player = returnPlayer(context, info)

        //Dispatching all terminal messages in one subject,
        //And adding it in our disposables collection
        val endSubj = AsyncSubject.create<Int>()
        compDis.add(endSubj.subscribe {
            Log.d(TAG, "end signal received: $it. Exiting now")
            handleNotifications(NOT_END, it)
            stop()
        })

        lateinit var contSubj: PublishSubject<Int>
        var timerSet = true

        var progressCache = 0
        val contObserver = object: DisposableObserver<Int>() {//Means continuous observer
            override fun onNext(t: Int) {
            handler.post{
                when (t) {
                    FLAG_STALLED -> {
                        if (!timerSet) {
                            setupRemovable(true, contSubj, endSubj); timerSet = true
                        }
                        handleNotifications(NOT_STALLED, progressCache)
                    }
                    FLAG_WARNING -> handleNotifications(NOT_WARNING, progressCache)
                    FLAG_SNOOZED -> handleNotifications(NOT_SNOOZED, progressCache).also { player?.start() }
                    else -> {
                        if (timerSet) {
                            setupRemovable(false, contSubj, endSubj); timerSet = false;
                            if (player?.isPlaying == true) player?.pause()
                        }
                        handleNotifications(NOT_PROGRESS, t); progressCache = t
                    }
                }
                Log.d(TAG, "informing $t")
            }            }
            override fun onError(e: Throwable):Unit = throw e
            override fun onComplete() {}
        }.also { compDis.add(it) }//in case of urgent exit we dispose it along with others

        contSubj = startSteps(contObserver, endSubj)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)
            startSound(endSubj)

        setupRemovable(true, contSubj, endSubj)
    }

    //Here, we just handle user notifications, not performing control for actions, except for the terminal one
    private fun handleNotifications(key: Int, value: Int = -1){
        val manager: NotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, "activeness")
            .setSmallIcon(R.drawable.ic_clock_alarm)
            .setContentTitle("Activeness detection")
            .setOngoing(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
        val warning = NotificationCompat.Builder(context, "warning")
            .setSmallIcon(R.drawable.ic_clock_alarm)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        val notificationID = 20
        val warningID = 30

        when (key){
            NOT_CREATE->{
                manager.cancelAll()
                notification
                    .setContentText("create")
                    .setProgress(100,0, true)
                manager.notify(notificationID, notification.build())
                service.startForeground(notificationID, notification.build())
                Log.d(TAG, "handleNotifications: service set to foreground")
            }
            NOT_PROGRESS->{
                manager.cancel(warningID)
                notification
                    .setContentText("progress")
                    .setProgress(100, value, false)
                manager.notify(notificationID, notification.build())
            }
            NOT_WARNING->{
                warning
                    .setContentTitle("Warning")
                    .setContentText("доигрался сука")
                manager.notify(warningID, warning.build())
            }
            NOT_STALLED->{
                notification
                    .setContentText("stalled")
                    .setProgress(100, value, false)
                manager.notify(notificationID, notification.build())
            }
            NOT_SNOOZED->{
                notification
                    .setContentText("snoozed")
                    .setProgress(100, value, false)
                manager.notify(notificationID, notification.build())
            }
            NOT_END->{
                warning
                    .setChannelId("activeness")
                    .setContentTitle("Activeness detection")
                when (value) {
                    FLAG_STEPS_ENDED -> warning.setContentText("steps ended")
                    FLAG_SOUND_ENDED -> warning.setContentText("sound ended")
                    FLAG_EXPIRED -> warning.setContentText("expired")
                }
                manager.cancelAll()
                manager.notify(warningID, warning.build())
            }
            NOT_END_EXTERNAL-> manager.cancelAll()
        }
    }

    private fun startSteps(observer: Observer<Int>, endSubject: AsyncSubject<Int>): PublishSubject<Int>{
        val subj = PublishSubject.create<Int>()
        subj.subscribeWith(observer)
        subj.takeUntil(endSubject)

        sManager = context.getSystemService(android.hardware.SensorManager::class.java)
        val sensor = sManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

        val bounceTime = 6 //peek time in periods
        val threshold = info.threshold //peek threshold in m/s
        val idleTime = 200L //time in which point can't be acquired again, in mS
        val sampleTime = 25000 //time in the terms of SensorManager class

        fun stepsCapsule(): SensorEventListener{//Just a wrapper around 'detectPoint' and 'stepsSupervisor' functions
            val activeTime = info.duration * 1000 //time in which user needs to be active in order to earn all points, in mS
            var lastPoint = -1
            var lastValue = -1

            val stepsSupervisor: (Int)-> Unit = { pC ->
                if (pC*idleTime < activeTime && pC!=lastPoint && pC!= FLAG_STALLED) {//receiving progress signals
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
                    subj.onComplete()
                    with(endSubject) {
                        onNext(FLAG_STEPS_ENDED)
                        onComplete()
                    }
                }
            }

            var pointsCount = 0
            var debouncerWorking = true //the whole sequence starts with debouncer
            var debouncerPoints = 0
            var waiterCounter = 0

            val debounceWindowDur = 20//number of periods(idle times)
            val debounceWindowPointsCondition = 5//how many points user should acquire in the window
            val debounceDelay = 3 * 1000 * 2//time in which we consider user stalled the process, in mS
            val debouncerBuffer = BooleanArray(debounceWindowDur).toList().toMutableList()
            //This functions represent an intermediate level of controlling points acquiring
            //We filter activeness noise, and detecting whether user stopped moving, by switching between them
            fun debouncer(isThereAProgress: Boolean): Boolean {
                Collections.rotate(debouncerBuffer, 1)
                debouncerBuffer[0] = isThereAProgress
                debouncerPoints = 0
                for (i in debouncerBuffer) if (i) debouncerPoints++
                return if (debouncerPoints==0) false
                else debouncerPoints>=debounceWindowPointsCondition
            }
            fun waiter(isThereAProgress: Boolean): Boolean {//An evil waiter, waits for user to stop moving
                if (isThereAProgress) waiterCounter = 0
                else waiterCounter++
                return waiterCounter>=(debounceDelay.toFloat()/(sampleTime/1000).toFloat())
            }

            var operator: (Boolean)-> Boolean
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
                val avg: Float

                operator = if (debouncerWorking) ::debouncer else ::waiter
                if (cnt > bounceTime) { //excluding first 6 results to avoid error
                    for (i in cnt - bounceTime until cnt) {
                        sum += resultMas[i]
                    }
                    avg = sum.toFloat() / bounceTime.toFloat()
                    if (avg > threshold && elapsedTime >= lastPeakTime + idleTime) {//Point acquiring condition
                        if (debouncerWorking && operator(true)){//If user passed debouncer. If no, we just don't do a thing
                            Log.d(TAG, "\tstepsCapsule: debouncer released")
                            pointsCount += debouncerPoints
                            stepsSupervisor(pointsCount)
                            waiterCounter = 0
                            debouncerWorking = false
                        }
                        else if (!debouncerWorking && !operator(true)) {//If waiter not complaining, we just counting. It just can't when we throw trues to it
                            pointsCount++
                            stepsSupervisor(pointsCount)
                        }
                        lastPeakTime = elapsedTime
                    }
                    else if (elapsedTime >= lastPeakTime + idleTime) {
                        operator(false)
                        if (!debouncerWorking && operator(false)){
                            Log.d(TAG, "\tstepsCapsule: waiter released")
                            debouncerBuffer.replaceAll { false }
                            debouncerWorking=true
                            stepsSupervisor(FLAG_STALLED)
                        }
                    }
                }
                cnt++
            }

            return object: SensorEventListener{
                override fun onSensorChanged(event: SensorEvent) = detectPoint(event)

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int): Unit = Unit
            }
        }

        with (stepsCapsule()){
            eventListener = this
            sManager.registerListener(this, sensor, sampleTime, handler)
        }
        return subj
    }

    @SuppressLint("MissingPermission")
    private fun startSound(endSubject: AsyncSubject<Int>){
        Log.d(TAG, "\tcontroller starting with sound")
        val sampleRate = (44.1 * 1000).toInt()
        val windowSize = 200//in ms

        lateinit var noiseDis: Disposable
        var set = false
        var counter = 0
        var condition = (info.noiseDuration * 1000)/windowSize
        condition = (condition - floor(condition.toFloat()*0.1)).toLong()
        fun timerSetter(lowCondition: Boolean, mediumCondition: Boolean, heightCondition: Boolean){
            if (lowCondition && mediumCondition && heightCondition && !set){ Log.d(TAG, "\t\tSNT activated")
                val observable = Observable.timer(info.noiseDuration, TimeUnit.SECONDS)
                noiseDis = observable.subscribe { if (counter>=condition)
                    with(endSubject){ onNext(FLAG_SOUND_ENDED); onComplete() } }
                set = true
            }
            else if (lowCondition && mediumCondition && heightCondition) counter++
            else if (set) {
                Log.d(TAG, "\t\tSNT unactivated")
                set = false
                counter = 0
                noiseDis.dispose()
            }
        }

        recorder = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.MIC)
            .setAudioFormat(AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .build())
            .setBufferSizeInBytes(AudioRecord.getMinBufferSize(sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_FLOAT) * 1000)
            .build()
        recorder.startRecording()

        handler.postDelayed({
            val bufferSize = ((windowSize.toFloat()/1000) * sampleRate).toInt()

            val samplesCount = 5
            val registerBathLow = FloatArray(samplesCount).toList().toMutableList()
            val registerBathMedium = FloatArray(samplesCount).toList().toMutableList()
            val registerBathHeight = FloatArray(samplesCount).toList().toMutableList()
            val bufferBathLow = BooleanArray(samplesCount).toList().toMutableList()
            val bufferBathMedium = BooleanArray(samplesCount).toList().toMutableList()
            val bufferBathHeight = BooleanArray(samplesCount).toList().toMutableList()

            val runnable = object : Runnable, NewWindowListener {//The whole thing looks like shit

                //Взять более узкое окно
                //Сделать сканер на частоты
                //Заставить юзера зафигачить референс
                override fun onWindowComputed(fftSnapshot: SparseIntArray) {
                    var refSection = 0f
                    var bathroom0Low = 0f
                    var bathroom2Low = 0f
                    var bathroom2Height = 0f
                    for (k in fftSnapshot.keyIterator()){
                        when (k.toFloat()/100){
                            in 4300f..5600f -> bathroom0Low+= fftSnapshot.get(k)
                            in 2500f..3500f -> bathroom2Low+= fftSnapshot.get(k)
                            in 8200f..11800f -> bathroom2Height+=fftSnapshot.get(k)
                            in 19000F..(sampleRate/2).toFloat() -> refSection+= fftSnapshot.get(k)
                        }
                    }
                    val res = (sampleRate/2).toFloat()/512
                    refSection /= floor(((sampleRate/2).toFloat() - 19000F) / res)
                    bathroom0Low /= floor((5600f-4300f)/res)
                    bathroom2Low /= floor((3500f-2500f)/res)
                    bathroom2Height /= floor((11800f-8200f)/res)

                    bathroom0Low = refSection/bathroom0Low
                    bathroom2Low = refSection/bathroom2Low
                    bathroom2Height = refSection/bathroom2Height

                    Collections.rotate(registerBathLow, 1)
                    Collections.rotate(registerBathMedium, 1)
                    Collections.rotate(registerBathHeight, 1)
                    Collections.rotate(bufferBathLow, 1)
                    Collections.rotate(bufferBathMedium, 1)
                    Collections.rotate(bufferBathHeight, 1)

                    registerBathLow[0] = bathroom2Low
                    registerBathMedium[0] = bathroom0Low
                    registerBathHeight[0] = bathroom2Height

                    var avgBathLow = 0f
                    var avgBathMedium = 0f
                    var avgBathHeight = 0f
                    for (i in 0 until samplesCount){
                        avgBathLow+=registerBathLow[i]
                        avgBathMedium+=registerBathMedium[i]
                        avgBathHeight+=registerBathHeight[i]
                    }
                    avgBathLow/=samplesCount
                    avgBathMedium/=samplesCount
                    avgBathHeight/=samplesCount

                    bufferBathLow[0] = avgBathLow>1.16
                    bufferBathMedium[0] = avgBathMedium>1.12
                    bufferBathHeight[0] = avgBathHeight>1.16

                    var debouncerBathLow = 0
                    var debouncerBathMedium = 0
                    var debouncerBathHeight = 0
                    for (i in 0 until samplesCount) {
                        if (bufferBathLow[i]) debouncerBathLow++
                        if (bufferBathMedium[i]) debouncerBathMedium++
                        if (bufferBathHeight[i]) debouncerBathHeight++
                    }
                    timerSetter(debouncerBathLow>=3, debouncerBathMedium>=3, debouncerBathHeight>=4)

//                    var text = "$refSection"
//                    text +="\n\n$avgBathLow\n$avgBathMedium\n$avgBathHeight\n\n"
//                    text += if (debouncerBathLow>=3) "\u2713\n" else "---\n"
//                    text += if (debouncerBathMedium>=3) "\u2713\n" else "---\n"
//                    text += if (debouncerBathHeight>=4) "\u2713\n" else "---\n"
//
//                    val manager: NotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
//                    val notification = NotificationCompat.Builder(context, "activeness")
//                        .setSmallIcon(R.drawable.ic_clock_alarm)
//                        .setContentTitle("Activeness detection")
//                        .setOngoing(true)
//                        .setShowWhen(false)
//                        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
//                        .setOnlyAlertOnce(true)
//                        .setStyle(NotificationCompat.BigTextStyle()
//                            .bigText(text))
//                    val notificationID = 20
//                    manager.notify(notificationID, notification.build())
                }

                override fun onIdlePassed() = Unit

                override fun run() {
                    if (recorder.state==AudioRecord.STATE_INITIALIZED) {
                        val processor = Processor(sampleRate, 9, "blackman", this, true)

                        val readData = FloatArray(bufferSize)
                        recorder.read(readData, 0, bufferSize, AudioRecord.READ_BLOCKING)

                        val convertedData = DoubleArray(bufferSize)
                        for (i in 0 until bufferSize) {
                            convertedData[i] = readData[i].toDouble()
                        }
                        processor.processSingle(convertedData)
                        handler.postDelayed(this, windowSize.toLong()/2)
                    }
                }
            }
            handler.post(runnable)
        }, windowSize.toLong())
    }

    //This observable serves to the 'snoozed' indication
    lateinit var dis: Disposable
    var snoozedDelay = info.timeSnoozed
    private fun setupRemovable(activate: Boolean, subj: PublishSubject<Int>, endSubject: AsyncSubject<Int>){
        if (activate){ Log.d(TAG, "\t\t\tremovable timer activated with delay $snoozedDelay")
            val observable = Observable.intervalRange(0, info.timeLost, 0, 1, TimeUnit.SECONDS)
            dis = observable.subscribe({
                when (it.toInt()){
                   info.timeWarning-> subj.onNext(FLAG_WARNING)
                   snoozedDelay-> {
                       subj.onNext(FLAG_SNOOZED)
                       snoozedDelay = if (snoozedDelay>info.timeWarning) snoozedDelay-(snoozedDelay-info.timeWarning)/2
                       else info.timeWarning
                   }
                }
            },{},{ with(endSubject){ onNext(FLAG_EXPIRED); onComplete() } })
        }
        else {
            Log.d(TAG, "\t\t\tremovable timer unactivated")
            dis.dispose()
        }
    }

    //This observable serves the borderline mechanism. Calls only for one time setup
    private fun setupInterval(endSubject: AsyncSubject<Int>){

    }

    fun stop(){
        sManager.unregisterListener(eventListener)
            recorder.stop()
            recorder.release()
        compDis.dispose()
            player?.release()
            player = null
        stopService()
    }
    fun cancelNotifications() = handleNotifications(NOT_END_EXTERNAL)
}
