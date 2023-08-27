package com.foxstoncold.youralarm.alarmScreenBackground

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.*
import android.util.SparseIntArray
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.util.keyIterator
import com.foxstoncold.youralarm.MainViewModel
import com.foxstoncold.youralarm.R
import com.foxstoncold.youralarm.alarmScreenBackground.StftFilter.NewWindowListener
import com.foxstoncold.youralarm.alarmScreenBackground.StftFilter.Processor
import com.foxstoncold.youralarm.alarmsUI.InterfaceUtils
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
import kotlin.math.roundToInt


class ActivenessDetectionService: Service() {
    /*
    Let's explain something about working in background. I use this handler to post
    low level controlling logic of step counter and sound detector in it.
    Also, overall controller logic use it. Basically, all the callbacks moved to Handler
     */
    private val binder: AEDBinder = AEDBinder()
    private var activenessHandler: Handler? = null
    private lateinit var wakeLock: PowerManager.WakeLock
    lateinit var controller: Controller
    private lateinit var info: SubInfo

    private var launched = false//Setting true only when detection starts, and no manually resetting it

    @SuppressLint("WakelockTimeout")
    override fun onCreate() {
        sl.en()
        activenessHandler = BackgroundUtils.getHandler("detectionService")
        wakeLock =
            (getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "${applicationContext.packageName}:DetectionService").also { it.acquire() }
    }
    @Suppress("DEPRECATION")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        sl.fp("received")
        val delay = intent!!.getBooleanExtra("delay", false)
        val stopCall = intent.getBooleanExtra("stopCall", false)
        val testMode = intent.getBooleanExtra("testMode", false)
        val fileOutput = intent.getBooleanExtra("fileOutput", false)

        if (!stopCall && !launched){
            info = if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.TIRAMISU) intent.getParcelableExtra("info", SubInfo::class.java)!!
            else intent.getParcelableExtra("info")!!
            sl.i("launching with passed id: *${info.id}*")

            controller = Controller (::stopService, applicationContext, activenessHandler!!, info, this, testMode, fileOutput)
            launched = true
        }
        else if (stopCall && !launched) sl.w("trying to stop, but not launched yet")
        else if (delay) controller.makeDelay()
        else if (stopCall)
            with(controller) {
                cancelNotifications()
                stopAltogether()
            }
        else {
            val intersectedInfo = if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.TIRAMISU) intent.getParcelableExtra("info", SubInfo::class.java)!!
            else intent.getParcelableExtra("info")!!

            //We send the same info entities for the test ones
            if (!testMode && info != intersectedInfo){
                sl.w("Requesting to start non-test entity instead of existed")
                with(controller){
                    stopController()
                    cancelNotifications()

                    info = intersectedInfo
                    controller = Controller (::stopService, applicationContext, activenessHandler!!, info, this@ActivenessDetectionService, false, fileOutput)
                }
            }
            else{
                sl.w("Already launched. Decline incoming with id: *${intersectedInfo.id}*")
                if (!testMode) AlarmExecutionDispatch.helpAfterDetection(applicationContext, intersectedInfo.id)
            }
        }

        return super.onStartCommand(intent, flags, startId)
    }
    //We never call this function from the Service, because it's only supplemental when Controller stops the whole thing
    private fun stopService(testMode: Boolean){
        if (!testMode) AlarmExecutionDispatch.helpAfterDetection(applicationContext, info.id)

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {//Use it in case of emergency exit
        sl.ex()
        if (wakeLock.isHeld) wakeLock.release()
    }

    inner class AEDBinder: Binder() {
        fun getService(): ActivenessDetectionService = this@ActivenessDetectionService
    }

    fun checkIdValidity(id: String): Boolean = id == info.id

    override fun onBind(intent: Intent?): IBinder = binder
}


class Controller(val stopService: (Boolean)->Unit,
                 val context: Context,
                 val handler: Handler,
                 private val info: SubInfo,
                 private val service: Service,
                 private val testMode: Boolean,
                 fileOutput: Boolean){

    private val sManager = context.getSystemService(android.hardware.SensorManager::class.java)
    private lateinit var countEventListener: SensorEventListener
    private lateinit var detectEventListener: SensorEventListener

    private lateinit var recorder: AudioRecord
    private var player: MediaPlayer? = null
    private val compDis: CompositeDisposable = CompositeDisposable()
    //Dedicated flag to keep showing endless progress bar when activeness haven't been committed
    private var firstStep = true
    //Need to indicate user that we found out them cheating
    private var again = false
    private var delayAvailable = true
    //Informing receiving observables about the end of detection
    private var stopped = false


    private companion object {
        const val FLAG_WARNING = -2
        const val FLAG_OUT = -3
        const val FLAG_EXPIRED = -4
        const val FLAG_STEPS_ENDED = -5
        const val FLAG_SOUND_ENDED = -6

        const val NOT_CREATE = 1
        const val NOT_END = 2
        const val NOT_END_EXTERNAL = 3
        const val NOT_STALLED = 4
        const val NOT_TIME_OUT = 5
        const val NOT_WARNING = 6
        const val NOT_PROGRESS = 7
        const val NOT_REMIND_DELAYED = 8
    }

    init {
        handleInforming(NOT_CREATE)
        if (!InterfaceUtils.checkActivityPermission(context) &&
                !InterfaceUtils.checkStepSensors(context)){
            sl.s("Started without permission or available sensors!")
            handleInforming(NOT_END)
            stopService(testMode)
        }

        player = BackgroundUtils.returnPlayer(context, info)
        sl.f("Controller: initialized. Test mode: $testMode, output: $fileOutput")

        //Dispatching all terminal messages to the endSubj,
        //And all Observers to our disposables collection
        val endSubj = AsyncSubject.create<Int>()
        compDis.add(endSubj.subscribe {
            sl.i("end signal received: $it. Exiting after delay")
            stopped = true
            handleInforming(NOT_END, it)
            player!!.stop()
            sManager.unregisterListener(countEventListener)

            handler.postDelayed({
                handleInforming(NOT_END_EXTERNAL)
                stopAltogether()
            }, 12000)
        })

        lateinit var contSubj: PublishSubject<Int>

        var progressCache = 0
        val contObserver = object: DisposableObserver<Int>() {//Means continuous observer
            override fun onNext(t: Int) {
                if (stopped) return
                volumeControl()

                handler.post{
                    when (t) {
                        FLAG_WARNING -> handleInforming(NOT_WARNING, progressCache)

                        FLAG_OUT -> handleInforming(NOT_TIME_OUT, progressCache).also { player?.start() }
                        //informing of progress
                        else -> {
                            firstStep = false
                            if (t!=0) handleInforming(NOT_PROGRESS, t); progressCache = t
                        }
                    }
                }
            }
            override fun onError(e: Throwable):Unit = throw e
            override fun onComplete() {}
        }.also { compDis.add(it) }//in case of urgent exit we dispose it along with others

        //basically, the controlling subject for steps detection
        contSubj = startSteps(contObserver, endSubj)

        if (info.noiseDetection && InterfaceUtils.checkNoisePermission(context))
            startSound(endSubj)

        if (!testMode)
            startStalled(contSubj, endSubj)
    }

    private val streamType = AudioManager.STREAM_ALARM
    private val audioManager = service.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var volIndex = if (info.volume!=-1) info.volume else audioManager.getStreamVolume(streamType)

    fun volumeControl(){
        if (info.volumeLock){
            if (audioManager.getStreamVolume(streamType) != volIndex){
                sl.fst("vol mismatch (${audioManager.getStreamVolume(streamType)} against $volIndex)")

                player!!.pause()
                audioManager.setStreamVolume(streamType, volIndex, AudioManager.FLAG_SHOW_UI)
                /* AudioManager won't allow us to programmatically set volume below some threshold,
                so here we adjust to avoid pointless looping */
                volIndex = audioManager.getStreamVolume(streamType)
                player!!.start()
            }
        }
    }

    lateinit var delayDis: Disposable
    fun makeDelay(){
        sl.fr("delay requested")
        handleInforming(NOT_REMIND_DELAYED)
        player?.pause()
        delayAvailable = false

        val timer = Observable.timer(1L, TimeUnit.MINUTES)
        delayDis = timer.subscribe {
            sl.fr("delay expired")
            player?.start()
            handleInforming(NOT_TIME_OUT)
        }
        compDis.add(delayDis)
    }

    private fun createButton(case: String): NotificationCompat.Action?{
        val intent = Intent(context, ActivenessDetectionService::class.java)
        intent.putExtra("info", info)

        when (case){
            "test"-> intent.putExtra("stopCall", true)
            "delay"-> intent.putExtra("delay", true)
        }
        val pi = PendingIntent.getService(service, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        return when (case){
            "test"-> NotificationCompat.Action(null, "Cancel", pi)
            "delay"-> NotificationCompat.Action(null, "1 m. delay", pi)
            else-> null
        }

    }

    //Here, we just handle user notifications, not performing control for actions, except for the terminal one
    private fun handleInforming(key: Int, value: Int = -1){
        val manager: NotificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val notification = NotificationCompat.Builder(context, MainViewModel.DETECTOR_CH_ID)
            .setSmallIcon(R.drawable.ic_clock_alarm)
            .setContentTitle("Activeness detection")
            .setOngoing(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
        if (testMode) notification.addAction(createButton("test"))

        val warning = NotificationCompat.Builder(context, MainViewModel.DETECTOR_INFO_CH_ID)
            .setSmallIcon(R.drawable.ic_clock_alarm)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        val notificationID = 20
        val warningID = 30

        when (key){
            NOT_CREATE->{
                manager.cancelAll()
                if (!testMode) notification
                    .setContentText("Please, start moving")
                    .setProgress(100,0, true)
                    .setOnlyAlertOnce(false)
                else notification
                    .setContentText("No steps detected yet")
                manager.notify(notificationID, notification.build())
                service.startForeground(notificationID, notification.build())
                sl.frp("service has been set to foreground")
            }
            NOT_PROGRESS->{
                manager.cancel(warningID)
                if (!testMode) notification
                    .setContentText("Detection may take time")
                    .setProgress(100, value, false)
                else notification
                    .setContentText("Steps counted: $value")
                manager.notify(notificationID, notification.build())
                sl.fr("progress $value")
            }
            NOT_WARNING->{
                warning
                    .setContentTitle("Warning")
                    .setContentText("Attention! You have to move!")
                manager.notify(warningID, warning.build())
                sl.fr("warning signal")
            }
            NOT_REMIND_DELAYED-> {
                warning
                    .setContentTitle("Still snoozed")
                    .setContentText("But you can have a little bit of silence")
                    .setOnlyAlertOnce(true)
                manager.notify(warningID, warning.build())
            }
            NOT_STALLED->{
                if (firstStep){
                    notification
                        .setContentText("Proceed to moving, please")
                        .setProgress(100,0, true)
                }
                else{
                    notification
                        .setContentText("Not gaining Points")
                        .setProgress(100, value, false)
                }
                manager.notify(notificationID, notification.build())
            }
            NOT_TIME_OUT->{
                warning
                    .setContentTitle("Snoozed")
                    .setContentText("Launching noise")
                    .setOnlyAlertOnce(true)
                if (delayAvailable) warning.addAction(createButton("delay"))
                manager.notify(warningID, warning.build())
                sl.fr("timeOut signal")
            }
            NOT_END->{
                with (notification) {
                    setContentTitle("Detection is over")
                    setOnlyAlertOnce(false)
                    when (value) {
                        FLAG_STEPS_ENDED -> {
                            setContentText("Ended by steps")
                            setSmallIcon(R.drawable.ic_activeness, 1)
                            sl.fr("steps ended")
                        }
                        FLAG_SOUND_ENDED -> {
                            setContentText("Ended by shower sound")
                            setSmallIcon(R.drawable.ic_shower, 1)
                            sl.fr("sound ended")
                        }
                        FLAG_EXPIRED -> {
                            setContentText("You've missed it")
                            setSmallIcon(R.drawable.ic_hourglass, 1)
                            sl.fr("expired")
                        }
                    }
                }
                manager.cancel(warningID)
                manager.notify(notificationID, notification.build())
            }
            NOT_END_EXTERNAL-> manager.cancelAll()
        }
    }

    @SuppressLint("CheckResult")
    private fun startSteps(observer: Observer<Int>, endSubject: AsyncSubject<Int>): PublishSubject<Int>{
        /*
        Basically, we make a new subject, binding it to the endSubject and make it send messages to to the given MAIN observer.
        Returning it just to add to the composite Disposable
        */
        val subj = PublishSubject.create<Int>()
        subj.subscribeWith(observer)
        subj.takeUntil(endSubject)

        val sensor = sManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        val sampleTime = 500f.pow(3f).toInt()//mS converted to uS

        fun stepsCapsule(): SensorEventListener{

            val targetSteps = info.steps
            var stepsCount: Int

            var firstCall = true
            var startVal = 0

            //This method will be called to register points on every buffer detection
            fun counter(event: SensorEvent) {
                val count = event.values[0].toInt()
                stepsCount = count - startVal

                if (firstCall){
                    startVal = count
                    firstCall = false
                }

                else if (testMode) subj.onNext(stepsCount)

                else {
                    val percent = (stepsCount.toDouble() / targetSteps * 100).toInt()

                    if (percent < 100) subj.onNext(percent)
                    else{
                        subj.onComplete()
                        with(endSubject){
                            onNext(FLAG_STEPS_ENDED)
                            onComplete()
                        }
                    }
                }
            }

            return object: SensorEventListener{
                override fun onSensorChanged(event: SensorEvent) = counter(event)

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int): Unit = Unit
            }
        }

        with (stepsCapsule()){
            countEventListener = this
            sManager.registerListener(this, sensor, sampleTime, handler)
        }
        return subj
    }

    private fun startStalled(contSubj: PublishSubject<Int>, endSubject: AsyncSubject<Int>){
        val sensor = sManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        val sampleTime = 2f.pow(6f).toInt()
        var timerSet = false

        val removableSetter = object: Runnable {
            override fun run() {
                handler.postDelayed ({
                    if (!timerSet){
                        setupRemovable(true, contSubj, endSubject)
                        timerSet = true
                    }
                }, 2f.pow(3f).toLong())
                handler.postDelayed(this, 2f.pow(3f).toLong())
            }
        }
        removableSetter.run()

        detectEventListener = object: SensorEventListener{
            override fun onSensorChanged(event: SensorEvent) {
                setupRemovable(false, contSubj, endSubject)
                timerSet = false
                if (this@Controller::delayDis.isInitialized) delayDis.dispose()
                if (player?.isPlaying == true) player?.pause()
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }

        sManager.registerListener(detectEventListener, sensor, sampleTime, handler)
    }

    @SuppressLint("MissingPermission")
    private fun startSound(endSubject: AsyncSubject<Int>){
        sl.frp("starting")
        val sampleRate = (44.1 * 1000).toInt()
        val windowSize = 200//in ms

        lateinit var noiseDis: Disposable
        var set = false
        var counter = 0
        var condition = (info.noiseDuration * 1000)/windowSize
        condition = (condition - floor(condition.toFloat()*0.1)).toLong()
        fun timerSetter(lowCondition: Boolean, mediumCondition: Boolean, heightCondition: Boolean){
            if (lowCondition && mediumCondition && heightCondition && !set){ sl.fstp("SNT activated")
                val observable = Observable.timer(info.noiseDuration, TimeUnit.SECONDS)
                noiseDis = observable.subscribe { if (counter>=condition)
                    with(endSubject){ onNext(FLAG_SOUND_ENDED); onComplete() } }
                set = true
            }
            else if (lowCondition && mediumCondition && heightCondition) counter++
            else if (set) {
                sl.fstp("SNT unactivated")
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
    private var dis: Disposable? = null
    private var outDelay = info.timeOut.toFloat()

    private fun setupRemovable(activate: Boolean, subj: PublishSubject<Int>, endSubject: AsyncSubject<Int>){
        val warningTime = (outDelay/2f)
        val outMin = 15f

        if (activate){
            val observable = Observable.intervalRange(0, info.globalLost * 60, 0, 1, TimeUnit.SECONDS)
            dis = observable.subscribe({ time ->
                when (time.toFloat()){
                   warningTime -> subj.onNext(FLAG_WARNING).also { sl.fst("warning worked: $warningTime") }
                   outDelay-> {
                       sl.fst("out delay worked: $outDelay")
                       subj.onNext(FLAG_OUT)
                       outDelay = if (outDelay > (outMin * 4f/3f)) (outDelay * 2f/3f).also { sl.fst("new: $time") }
                       else outMin
                   }
                }
            },{},{ with(endSubject){ onNext(FLAG_EXPIRED); onComplete() } })
        }
        else dis?.dispose()
    }

    fun stopController(){
        if (this::recorder.isInitialized) {
            recorder.stop()
            recorder.release()
        }
        compDis.dispose()

        player?.stop()
        player?.release()
        player = null

        sManager.unregisterListener(countEventListener)
        if (this::detectEventListener.isInitialized) sManager.unregisterListener(detectEventListener)
    }

    fun stopAltogether(){
        stopController()
        stopService(testMode)
    }
    fun cancelNotifications() = handleInforming(NOT_END_EXTERNAL)
}
