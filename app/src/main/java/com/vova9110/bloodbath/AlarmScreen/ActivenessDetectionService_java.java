package com.vova9110.bloodbath.AlarmScreen;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioRecord;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Log;
import android.widget.Toast;

import com.elvishew.xlog.LogLevel;
import com.elvishew.xlog.XLog;
import com.elvishew.xlog.flattener.PatternFlattener;
import com.elvishew.xlog.printer.AndroidPrinter;
import com.elvishew.xlog.printer.file.FilePrinter;
import com.elvishew.xlog.printer.file.naming.FileNameGenerator;

import java.util.Calendar;
import java.util.Timer;
import java.util.TimerTask;

import org.jtransforms.fft.FloatFFT_1D;

/**
 * Calling this service only when activeness-flagged alarm has dismissed in the AlarmActivity.
 * It's not handling repo operations, but receiving current alarm as a Intent extra,
 * and handling it's own notifications.
 */
public class ActivenessDetectionService_java extends Service {
    private static final String TAG = "TAG_Act-de-ser";
    private customHandlerThread looperThread;
    private Handler detectionHandler;
    private final short logLevel = 3;//0 for none of it, 1 for gait(whatever) detection, 2 for noise detection, 3 for both
    private PointsHandler PH;

    public void onCreate() {
        Log.d(TAG, "service Created");
        PH = new PointsHandler();
    }



    private class PointsHandler{
        private PointsHandler(){
        }

        SensorManager sManager;
        SensorEventListener eventListener;
        Sensor sensor;
        final int bounceTime = 6;//peek time in periods
        final float threshold = 2.2f;//peek threshold in m/s
        final long idleTime = 200;//time in which point can't be acquired again, in mS
        final int sampleTime = 25000;//time in the terms of SensorManager class
        Vibrator vibrator;
        PowerManager.WakeLock wakeLock;

        AudioRecord recorder;
        /*
        * Согласно теореме Котельникова, для дискретизации сигнала с частотой не более 4кГц, нам нужно
        * выбрать частоту дескретизации, равную 8кГц
         */
        final int sampleRate = 48000;//in Hz
        int n = sampleRate;
        int k = n/2;
        int s = 0;//just a counter for logging purposes

        int pointsCount;//overall points count
        boolean detectionStarted = false;
        long startTime;
        long lastPeakTime;
        int cnt;//measurements count
        double[] resultMas = new double[1000000];

        void detectPoint(SensorEvent event) {
            long elapsedTime = (event.timestamp - startTime) / 1000000;//time since detection started
            double result = Math.sqrt(Math.pow((double) event.values[0], 2.0d) + Math.pow((double) event.values[1], 2.0d) + Math.pow((double) event.values[2], 2.0d));
            resultMas[cnt] = result;
            double sum = 0.0d;
            float avg = 0.0f;

            if (cnt > bounceTime) {//excluding first 6 results
                for (int i = cnt - bounceTime; i < cnt; i++) {
                    sum += resultMas[i];
                }

                avg = (float)sum / (float)bounceTime;
                if ((avg > threshold) && (elapsedTime >= (lastPeakTime + idleTime))) {
                    pointsCount++;
                    lastPeakTime = elapsedTime;
                    if (logLevel == 1 | logLevel ==3) vibrator.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_HEAVY_CLICK));
                }
            }
            XLog.d(elapsedTime + "," + result + "," + avg);
            this.cnt++;
        }

        void activate(){
            if (!detectionStarted){
                looperThread = new customHandlerThread();
                new Timer().schedule(new TimerTask() {
                    @Override
                    public void run() {
                        detectionHandler = looperThread.handler;
                        if (detectionHandler != null) cancel();
                        Log.d(TAG, "trying to get valid Handler");
                    }
                },10,5);
                looperThread.start();

                vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
                wakeLock = ((PowerManager) getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "BloodBath:DetectionService");

                pointsCount = 0;
                startTime = SystemClock.elapsedRealtimeNanos();
                lastPeakTime = 0L;
                cnt = 0;
                SensorManager sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
                sManager = sensorManager;
                sensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);

                //todo сделать проверку разрешения на микро
//                recorder = new AudioRecord.Builder()
//                        .setAudioSource(MediaRecorder.AudioSource.MIC)
//                        .setAudioFormat(new AudioFormat.Builder()
//                                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
//                                .setSampleRate(sampleRate)
//                                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
//                                .build())
//                        .setBufferSizeInBytes(AudioRecord.getMinBufferSize(sampleRate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_FLOAT)*10)
//                        .build();
//                recorder.startRecording();

                detectionHandler.post(() -> {
                    float sum;
                    short times = 0;
                    do{
                        float[] check = new float[100];
                        recorder.read(check,0,100,AudioRecord.READ_BLOCKING);
                        sum = 0f;
                        for (float each : check){
                            sum += each;
                        }
                        times++;
                    }
                    while (sum == 0.0f);
                    Log.d(TAG, "audioRecorder skipped " + times*100 + " samples approx");
                });



                if (logLevel == 1 | logLevel == 3){
                    Log.d(TAG, "" + getApplicationContext().getExternalFilesDir(null).toString());
                    XLog.init(LogLevel.ALL, new AndroidPrinter(), new FilePrinter.Builder(getApplicationContext().getExternalFilesDir((String) null).toString()).flattener(new PatternFlattener("{m}")).fileNameGenerator(new FileNameGenerator() {
                        public boolean isFileNameChangeable() {
                            return false;
                        }

                        public String generateFileName(int logLevel, long timestamp) {
                            Calendar calendar = Calendar.getInstance();
                            calendar.setTimeInMillis(System.currentTimeMillis());
                            return "_" + calendar.get(11) + ":" + calendar.get(12) + ":" + calendar.get(13);
                        }
                    }).build());
                    XLog.d("time,result,bounceTimeAverage,n,fft");
                }

                eventListener = new SensorEventListener() {

                    public void onSensorChanged(SensorEvent sensorEvent) {//When called, sensor data retrieving and processing occurs
                        PH.detectPoint(sensorEvent);
                    }

                    public void onAccuracyChanged(Sensor sensor, int i) {
                    }
                };

                //sManager.registerListener(eventListener, sensor, sampleTime, detectionHandler);
                wakeLock.acquire(10*60*1000L /*10 minutes*/);
                detectionStarted = true;
                Log.d(TAG, "Detection started");
            }
            else{
                stop();
            }
        }

        private void stop(){
            sManager.unregisterListener(this.eventListener);
            recorder.stop();
            recorder.release();
            detectionStarted = false;
            looperThread.quit();
            wakeLock.release();
            Toast.makeText(getApplicationContext(), "Points collected: " + this.pointsCount, Toast.LENGTH_LONG).show();
            Log.w(TAG, "Points already counting! Stopping detection");
        }

        private void detectNoise(){
            /*
            *a[2*k] = Re[k], 0<=k<n/2
            *a[2*k+1] = Im[k], 0<k<n/2
            *a[1] = Re[n/2]
            * k - это аргумент выходной функции, то есть наша частота.
            * При частоте дескретизации 8кГц, нам потребуется секунда на запись 8 тысяч семплов для преобразования.
            * Дальше всё просто - нужно всего-лишь определить модуль КЧ, что и будет нашей искомой амплитудой
            */

            float[] matrix = new float[n];
            int recorded = recorder.read(matrix,0,n,AudioRecord.READ_BLOCKING);
            float[] raw = matrix.clone();
            float[] transformed = new float[k];

            //Debug.waitForDebugger();
            FloatFFT_1D transformer = new FloatFFT_1D(n);
            transformer.realForward(raw);

            for(int i = 0; i < k; i++){
                float mag = (float)Math.sqrt(Math.pow(matrix[2*i],2) + Math.pow(matrix[2*i+1],2));
                transformed[i] = mag;
                XLog.d(",,," + s + "," + transformed[i]);
                s++;
            }
//            for (int i = k; i < n; i++) {
//                XLog.d(",,," + s + "," + matrix[i]);
//                s++;
//            }
            for (int i = 0; i <= 200; i++) {
                XLog.d(",,," + s + ",0");
                s++;
            }
        }
    }

    private class customHandlerThread extends HandlerThread{

        public customHandlerThread() {
            super("detectionThread");
        }
        Handler handler;

        @Override
        protected void onLooperPrepared() {
            handler = new Handler(getLooper());
        }
    }
    

//    public int onStartCommand(Intent intent, int flags, int startId) {
//        Log.d(TAG, "service Entered");
//        if (intent.getBooleanExtra("activate",false)){
//            PH.activate();
//        }
//        if (intent.getBooleanExtra("detect", false))
//        detectionHandler.post(()->{
//            PH.detectNoise();
//        });
//        return super.onStartCommand(intent, flags, startId);
//    }


    public IBinder onBind(Intent intent) {
        throw new UnsupportedOperationException("Not yet implemented");
    }
}