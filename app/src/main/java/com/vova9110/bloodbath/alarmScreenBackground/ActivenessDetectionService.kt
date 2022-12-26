package com.vova9110.bloodbath.alarmScreenBackground

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
import android.util.Log
import android.util.SparseIntArray
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.util.keyIterator
import com.vova9110.bloodbath.MainViewModel
import com.vova9110.bloodbath.R
import com.vova9110.bloodbath.alarmScreenBackground.StftFilter.NewWindowListener
import com.vova9110.bloodbath.alarmScreenBackground.StftFilter.Processor
import com.vova9110.bloodbath.SplitLogger
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Observer
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import io.reactivex.rxjava3.observers.DisposableObserver
import io.reactivex.rxjava3.subjects.AsyncSubject
import io.reactivex.rxjava3.subjects.PublishSubject
import uk.me.berndporr.iirj.Butterworth
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.floor
import kotlin.math.pow



class ActivenessDetectionService: Service() {
    /*
    Let's explain something about working in background. I use this handler to post
    low level controlling logic of step counter and sound detector in it.
    Also, overall controller logic use it. Basically, all the callbacks moved to Handler
     */
    private var activenessHandler: Handler? = null
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var controller: Controller
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
        val stopCall = intent?.getBooleanExtra("stopCall", false)

        //todo add demo mode

        if (stopCall==false && !launched){
            info = if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.TIRAMISU) intent.getParcelableExtra("info", SubInfo::class.java)!!
            else intent.getParcelableExtra("info")!!
            sl.i("launching with passed id: *${info.id}*")

            controller = Controller (::stopService, applicationContext, activenessHandler!!, info, this, intent.getBooleanExtra("testMode", false), intent.getBooleanExtra("fileOutput", false))
            launched = true
        }
        else if (!launched) sl.w("trying to stop, but not launched yet")
        else if (stopCall==true) with(controller) {cancelNotifications(); stop()}
        else {
            val intersected = if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.TIRAMISU) intent!!.getParcelableExtra("info", SubInfo::class.java)!!
            else intent!!.getParcelableExtra("info")!!
            sl.w("Already launched. Decline incoming with id: **")
            AlarmExecutionDispatch.helpAfterDetection(applicationContext, intersected.id)
        }

        return super.onStartCommand(intent, flags, startId)
    }
    //The thing is, we only call this function from Controller, and not directly from here
    private fun stopService(){
        AlarmExecutionDispatch.helpAfterDetection(applicationContext, info.id)

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {//Use it in case of emergency exit
        sl.ex()
        if (wakeLock.isHeld) wakeLock.release()
    }

    override fun onBind(intent: Intent?): IBinder = TODO("Not yet implemented")
}


private class Controller(val stopService: ()->Unit, val context: Context, val handler: Handler, val info: SubInfo, val service: Service, val testMode: Boolean, val fileOutput: Boolean){
    lateinit var sManager: SensorManager
    lateinit var eventListener: SensorEventListener
    lateinit var recorder: AudioRecord
    var player: MediaPlayer? = null
    val compDis: CompositeDisposable = CompositeDisposable()
    //Dedicated flag to keep showing endless progress bar when activeness haven't been committed
    var firstStep = false
    //Informing receiving observables about the end of detection
    var stopped = false


    private companion object {
        const val FLAG_STALLED = -1
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
    }

    init {
        handleInforming(NOT_CREATE)
        player = returnPlayer(context, info)
        sl.f("Controller: initialized. Test mode: $testMode, output: $fileOutput")

        //Dispatching all terminal messages in one subject,
        //And adding it in our disposables collection
        val endSubj = AsyncSubject.create<Int>()
        compDis.add(endSubj.subscribe {
            sl.i("end signal received: $it. Exiting after delay")
            stopped = true
            handleInforming(NOT_END, it)
            player!!.stop()
            sManager.unregisterListener(eventListener)

            handler.postDelayed({
                handleInforming(NOT_END_EXTERNAL)
                stop()
            }, 12000)
        })

        lateinit var contSubj: PublishSubject<Int>
        var timerSet = true

        var progressCache = 0
        val contObserver = object: DisposableObserver<Int>() {//Means continuous observer
            override fun onNext(t: Int) {
                if (stopped) return

                handler.post{
                    when (t) {
                        FLAG_STALLED -> {
                            if (!timerSet) {
                                setupRemovable(true, contSubj, endSubj); timerSet = true
                            }
                            handleInforming(NOT_STALLED, progressCache)
                        }
                        FLAG_WARNING -> handleInforming(NOT_WARNING, progressCache)
                        FLAG_OUT -> handleInforming(NOT_TIME_OUT, progressCache).also { player?.start() }
                        else -> {
                            firstStep = true
                            if (timerSet) {
                                setupRemovable(false, contSubj, endSubj); timerSet = false;
                                if (player?.isPlaying == true) player?.pause()
                            }
                            if (t!=0) handleInforming(NOT_PROGRESS, t); progressCache = t
                        }
                    }
                    sl.f("informing $t")
                }
            }
            override fun onError(e: Throwable):Unit = throw e
            override fun onComplete() {}
        }.also { compDis.add(it) }//in case of urgent exit we dispose it along with others

        contSubj = startSteps(contObserver, endSubj)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)
            startSound(endSubj)

        if (!testMode) setupRemovable(true, contSubj, endSubj)
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
                    .setContentText("create")
                    .setProgress(100,0, true)
                else notification
                    .setContentText("points count: not detected yet")
                manager.notify(notificationID, notification.build())
                service.startForeground(notificationID, notification.build())
                sl.frp("service have been set to foreground")
            }
            NOT_PROGRESS->{
                manager.cancel(warningID)
                if (!testMode) notification
                    .setContentText("progress")
                    .setProgress(100, value, false)
                else notification
                    .setContentText("points count: $value")
                manager.notify(notificationID, notification.build())
            }
            NOT_WARNING->{
                warning
                    .setContentTitle("Warning")
                    .setContentText("доигрался сука")
                manager.notify(warningID, warning.build())
            }
            NOT_STALLED->{
                notification.setContentText("stalled")
                if (firstStep) notification.setProgress(100, value, false)
                else notification.setProgress(100, value, true)
                manager.notify(notificationID, notification.build())
            }
            NOT_TIME_OUT->{
                warning
                    .setContentTitle("Snoozed")
                    .setContentText("готовь жопу")
                    .setOnlyAlertOnce(true)
                manager.notify(warningID, warning.build())
            }
            NOT_END->{
                with (notification) {
                    setContentTitle("Detection is over")
                    setOnlyAlertOnce(false)
                    when (value) {
                        FLAG_STEPS_ENDED ->
                            setContentText("steps ended")
                        FLAG_SOUND_ENDED ->
                            setContentText("sound ended")
                        FLAG_EXPIRED ->
                            setContentText("expired")
                        else -> {}
                    }
                }
                manager.cancel(warningID)
                manager.notify(notificationID, notification.build())
            }
            NOT_END_EXTERNAL-> manager.cancelAll()
        }
    }

    //Now it detects steps plus approximately 1/3 of inaccuracy on top. Can't do better for now
    private fun startSteps(observer: Observer<Int>, endSubject: AsyncSubject<Int>): PublishSubject<Int>{
        val subj = PublishSubject.create<Int>()
        subj.subscribeWith(observer)
        subj.takeUntil(endSubject)

        sManager = context.getSystemService(android.hardware.SensorManager::class.java)
        val sensor = sManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

        val sampleTime = 20000//this is the target sample time for the given sensor in uS. The fastest I can get from mine
        val bufferTargetPeriodMillis = 2000//in mS
        val bufferTargetPeriod = (bufferTargetPeriodMillis.toDouble() * 10.0.pow(6).toInt()).toLong()//in nS


        //Just a wrapper around 'detectPoint' and 'stepsSupervisor' functions
        //All logic belongs to here
        fun stepsCapsule(): SensorEventListener{

            var output: BufferedOutputStream? = null
            val isTestEnabled = testMode
            val activeTime = info.duration //time in which user needs to be active in order to earn all points, in mS
            var pointsCount = 0

            fun supervisor(points: Int) {
                val percent: Int
                pointsCount+=points*2

                if (points>0 && !isTestEnabled) {
                    percent = (pointsCount.toDouble() / activeTime * 100).toInt()

                    if (percent < 100) subj.onNext(percent)
                    else {
                        subj.onComplete()
                        with(endSubject) {
                            onNext(FLAG_STEPS_ENDED)
                            onComplete()
                            if (output!=null) output!!.close()
                        }
                    }
                }
                else if (!isTestEnabled) subj.onNext(FLAG_STALLED)
                else subj.onNext(pointsCount)
            }

            val isOutputEnabled = fileOutput
            var outputEstablished = false
            var timeCounter = 0f

            fun outputController(xMass: DoubleArray, yMass: DoubleArray, zMass: DoubleArray, crossArray: DoubleArray = DoubleArray(xMass.size), thresholdArray: DoubleArray = DoubleArray(xMass.size), conditionArray: DoubleArray = DoubleArray(xMass.size), counterArray: DoubleArray = DoubleArray(xMass.size)){
                if (!outputEstablished && isOutputEnabled) {
                    val c = Calendar.getInstance().apply { time = Date(System.currentTimeMillis()) }
                    val fileName = "CSV_${c.get(Calendar.DATE)}_${c.get(Calendar.HOUR_OF_DAY)}:${c.get(Calendar.MINUTE)}:${c.get(Calendar.SECOND)}.txt"
                    val parentDir = File(context.getExternalFilesDir(null), "step_counter_logs")
                    val outputFile = File(parentDir, fileName)
                    println("\t" + SplitLogger.manageDirectory(parentDir))

                    try {
                        if (!outputFile.createNewFile()){
                            sl.fstp("file already exists. Replacing")
                            outputFile.delete()
                            outputFile.createNewFile()
                        }
                        else sl.fstp("file created, name: $fileName")
                    } catch (e: Exception) {
                        sl.sp(e)
                    }
                    output = BufferedOutputStream(FileOutputStream(outputFile))

                    val titlesString = "timestamp,xVal,yVal,zVal,null-cross,threshold,condition,bounce\n"
                    output!!.write(titlesString.toByteArray())
                    output!!.flush()

                    outputEstablished = true
                }
                if (isOutputEnabled){
                    val builder = StringBuilder()
                    val timeStep = bufferTargetPeriodMillis.toFloat() / xMass.size

                    for (i in xMass.indices){
                        builder.append(timeCounter.toInt().toString(), ",")
                        builder.append(xMass[i].toString(), ",")
                        builder.append(yMass[i].toString(), ",")
                        builder.append(zMass[i].toString(), ",")
                        if (crossArray[i]!=0.0 && counterArray[i]==0.0) builder.append(crossArray[i].toString(), ","); else builder.append(",")
                        if (thresholdArray[i]!=0.0 && conditionArray[i]==0.0 && counterArray[i]==0.0) builder.append(thresholdArray[i].toString(), ","); else builder.append(",")
                        if (conditionArray[i]!=0.0 && counterArray[i]==0.0) builder.append(conditionArray[i].toString(), ","); else builder.append(",")
                        if (counterArray[i]!=0.0) builder.append(counterArray[i])

                        builder.append("\n")
                        timeCounter+=timeStep
                    }
                    output!!.write(builder.toString().toByteArray())
                    output!!.flush()
                }
            }


            /*
            This function corresponds directly to the Supervisor
            And with output function, after detection performed
            */
            var prevValX = 0.0
            var prevValY = 0.0
            var prevValZ = 0.0

            val flagsBufferX = BooleanArray(6)
            val flagsBufferY = BooleanArray(6)
            val flagsBufferZ = BooleanArray(6)

            var idleCountX = 0
            var idleCountY = 0
            var idleCountZ = 0

            //This function called every time we receive buffered data from refiner
            fun detect(xMass: DoubleArray, yMass: DoubleArray, zMass: DoubleArray) {

                //This function used to scan every sample one by one. It doesn't know about which axis it scans
                fun detectAxis(
                    iterator: Int,
                    prevVal: Double,
                    currVal: Double,
                    crossArray: DoubleArray,
                    thresholdArray: DoubleArray,
                    conditionArray: DoubleArray,
                    counterArray: DoubleArray,
                    flagsBuffer: BooleanArray,
                    iC: Int
                ): Int {
                    /*
                    prevHalfWaveCounted = flagsBuffer[0]
                    currHalfWaveCounted = flagsBuffer[1]
                    cross = flagsBuffer[2]
                    bounceCache3 = flagsBuffer[3]
                    bounceCache2 = flagsBuffer[4]
                    writePrevPoint = flagsBuffer[5]
                    */
                    //Looks like hell, yeah. Fuck off, I'm tired after work
                    var idleCount = iC

                    fun bounce(currWaveCounted: Boolean = false){
                        if (!currWaveCounted) {flagsBuffer[4] = false; flagsBuffer[3] = false}
                        else if (!flagsBuffer[3] && !flagsBuffer[4]) flagsBuffer[4] = true
                        else if (!flagsBuffer[3]){
                            flagsBuffer[3] = true
                            flagsBuffer[4] = true
                            flagsBuffer[5] = true

                            counterArray[iterator] = currVal
                        }
                        else if (flagsBuffer[4]) counterArray[iterator] = currVal
                    }

                    if (0.0 in prevVal..currVal || 0.0 in currVal..prevVal) {
                        crossArray[iterator] = currVal
                        if (!flagsBuffer[2]) flagsBuffer[2] = true
                        else flagsBuffer[0] = false
                        if (idleCount > 4) bounce(); else idleCount++

                        flagsBuffer[1] = false
                        if (flagsBuffer[5]){ counterArray[iterator] = currVal; flagsBuffer[5]=false}
                    }
                    if (info.threshold in prevVal..currVal || info.threshold in currVal..prevVal || -info.threshold in prevVal..currVal || -info.threshold in currVal..prevVal) {
                        if (!flagsBuffer[0] && !flagsBuffer[1]) {//Threshold exceeded on first halfWave
                            flagsBuffer[2] = false;
                            flagsBuffer[0] = true
                        } else {
                            if (flagsBuffer[2]) {//Condition happened
                                conditionArray[iterator] = currVal
                                flagsBuffer[0] = false
                                flagsBuffer[1] = true
                                flagsBuffer[2] = false

                                idleCount = 0
                                bounce(true)
                            }
                        }
                        thresholdArray[iterator] = currVal
                    }
                    return idleCount
                }

                //Before we start scan, we need to initialize input and output data first
                val crossArrayX = DoubleArray(xMass.size)
                val crossArrayY = DoubleArray(xMass.size)
                val crossArrayZ = DoubleArray(xMass.size)
                val thresholdArrayX = DoubleArray(xMass.size)
                val thresholdArrayY = DoubleArray(xMass.size)
                val thresholdArrayZ = DoubleArray(xMass.size)
                val conditionArrayX = DoubleArray(xMass.size)
                val conditionArrayY = DoubleArray(xMass.size)
                val conditionArrayZ = DoubleArray(xMass.size)
                val counterArrayX = DoubleArray(xMass.size)
                val counterArrayY = DoubleArray(xMass.size)
                val counterArrayZ = DoubleArray(xMass.size)

                for (i in xMass.indices){
                    idleCountX = detectAxis(i, prevValX, xMass[i], crossArrayX, thresholdArrayX, conditionArrayX, counterArrayX, flagsBufferX, idleCountX)
                    prevValX = xMass[i]
                }
                for (i in yMass.indices){
                    idleCountY = detectAxis(i, prevValY, yMass[i], crossArrayY, thresholdArrayY, conditionArrayY, counterArrayY, flagsBufferY, idleCountY)
                    prevValY = yMass[i]
                }
                for (i in zMass.indices){
                    idleCountZ = detectAxis(i, prevValZ, zMass[i], crossArrayZ, thresholdArrayZ, conditionArrayZ, counterArrayZ, flagsBufferZ, idleCountZ)
                    prevValZ = zMass[i]
                }

                val countX = counterArrayX.count {it!=0.0}
                val countY = counterArrayY.count {it!=0.0}
                val countZ = counterArrayZ.count {it!=0.0}
                when (maxOf(countX, countY, countZ)){
                    0 ->{
                        sl.fstp("not detected")
                        outputController(xMass,yMass,zMass)
                        supervisor(0)
                    }
                    countX -> {
                        sl.fstp("X chosen")
                        outputController(xMass,yMass,zMass, crossArrayX, thresholdArrayX, conditionArrayX, counterArrayX)
                        supervisor(countX)
                    }
                    countY -> {
                        sl.fstp("Y chosen")
                        outputController(xMass,yMass,zMass, crossArrayY, thresholdArrayY, conditionArrayY, counterArrayY)
                        supervisor(countY)
                    }
                    countZ -> {
                        sl.fstp("Z chosen")
                        outputController(xMass,yMass,zMass, crossArrayZ, thresholdArrayZ, conditionArrayZ, counterArrayZ)
                        supervisor(countZ)
                    }
                }
            }

            /*
            This function combines all the logic defined before
            It collects data from the Sensor and refines it to pass in the detect function consequently
            It also relies on one subsidiary function to establish and close CSV file output of refined (but still undetected) data if needed
            */
            var bufferStartTime = -1L//time in which writing to buffer started
            lateinit var rawBufferX: DoubleArray
            lateinit var rawBufferY: DoubleArray
            lateinit var rawBufferZ: DoubleArray
            val initialSize = (bufferTargetPeriodMillis * 10.0.pow(3) / sampleTime) * 1.5
            var samplesWrote = 0

            val refine: (SensorEvent)-> Unit = {
                if (bufferStartTime==-1L){
                    bufferStartTime = SystemClock.elapsedRealtimeNanos()
                    rawBufferX = DoubleArray(initialSize.toInt())
                    rawBufferY = DoubleArray(initialSize.toInt())
                    rawBufferZ = DoubleArray(initialSize.toInt())
                }
                //how many nanos passed since writing started
                val bufferElapsedTime: Long = it.timestamp - bufferStartTime

                if (bufferElapsedTime <= bufferTargetPeriod){//During writing
                    rawBufferX[samplesWrote] = it.values[0].toDouble()
                    rawBufferY[samplesWrote] = it.values[1].toDouble()
                    rawBufferZ[samplesWrote] = it.values[2].toDouble()
                    samplesWrote++
                }
                else {
                    val clearedBufferX = DoubleArray(samplesWrote) {i -> if (i==0) it.values[0].toDouble() else 0.0}
                    val clearedBufferY = DoubleArray(samplesWrote) {i -> if (i==0) it.values[1].toDouble() else 0.0}
                    val clearedBufferZ = DoubleArray(samplesWrote) {i -> if (i==0) it.values[2].toDouble() else 0.0}

                    for (i in 0 until samplesWrote){
                        if (rawBufferX[i]!=0.0) clearedBufferX[i] = rawBufferX[i]
                        if (rawBufferY[i]!=0.0) clearedBufferY[i] = rawBufferY[i]
                        if (rawBufferZ[i]!=0.0) clearedBufferZ[i] = rawBufferZ[i]
                    }

                    bufferStartTime = it.timestamp//period of time since writing to buffer started
                    rawBufferX = DoubleArray(initialSize.toInt())
                    rawBufferY = DoubleArray(initialSize.toInt())
                    rawBufferZ = DoubleArray(initialSize.toInt())
                    samplesWrote = 1

                    val samplingFreq = (1.0 / (sampleTime.toDouble() * 10.0.pow(-6)))
                    val filter = Butterworth()
                    filter.bandPass(6, samplingFreq, 1.5, 1.4)

                    val refinedBufferX = DoubleArray(clearedBufferX.size) {i -> filter.filter(clearedBufferX[i])}
                    val refinedBufferY = DoubleArray(clearedBufferY.size) {i -> filter.filter(clearedBufferY[i])}
                    val refinedBufferZ = DoubleArray(clearedBufferZ.size) {i -> filter.filter(clearedBufferZ[i])}

                    detect(refinedBufferX, refinedBufferY, refinedBufferZ)
                }
            }

            return object: SensorEventListener{
                override fun onSensorChanged(event: SensorEvent) = refine(event)

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
    var dis: Disposable? = null
    var outDelay = info.timeOut
    private fun setupRemovable(activate: Boolean, subj: PublishSubject<Int>, endSubject: AsyncSubject<Int>){
        if (activate){ sl.fstp("activated with delay $outDelay")
            val observable = Observable.intervalRange(0, info.globalLost * 60, 0, 1, TimeUnit.SECONDS)
            dis = observable.subscribe({
                when (it.toInt()){
                   info.timeWarning -> subj.onNext(FLAG_WARNING)
                   outDelay-> {
                       subj.onNext(FLAG_OUT)
                       outDelay = if (outDelay>info.timeWarning) outDelay-(outDelay-info.timeWarning)/2
                       else info.timeWarning
                   }
                }
            },{},{ with(endSubject){ onNext(FLAG_EXPIRED); onComplete() } })
        }
        else {
            sl.fstp("unactivated")
            dis?.dispose()
        }
    }

    fun stop(){
        if (this::recorder.isInitialized) {
            recorder.stop()
            recorder.release()
        }
        compDis.dispose()
            player?.release()
            player = null
        stopService()
    }
    fun cancelNotifications() = handleInforming(NOT_END_EXTERNAL)
}
