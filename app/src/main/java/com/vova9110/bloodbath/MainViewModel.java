package com.vova9110.bloodbath;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.net.Uri;

import androidx.lifecycle.AndroidViewModel;

import com.vova9110.bloodbath.alarmScreenBackground.AlarmRepo;

public class MainViewModel extends AndroidViewModel {
    public final static String PREFERENCES_NAME = "prefs";
    private final Application app;
    private SplitLogger sl;

    public MainViewModel(Application app) {
        super(app);

        this.app = app;
        reassureRepo();
        checkLaunchPreferences();
    }

    private void reassureRepo(){
        ((MyApp) app).component.getRepo().reassureAll();
    }
    private void checkLaunchPreferences(){
        SharedPreferences prefs = app.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        if (!prefs.getBoolean("notificationChannelsSet", false)) {
            sl.fpc("Setting notification channels");
            editor.putBoolean("notificationChannelsSet", true);
            NotificationManager manager = (NotificationManager) app.getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);


            NotificationChannel channel1 = new NotificationChannel(FIRING_CH_ID, app.getString(R.string.firing_notification_channel_name), NotificationManager.IMPORTANCE_HIGH);
            channel1.setImportance(NotificationManager.IMPORTANCE_HIGH);
            channel1.setSound(null, null);
            channel1.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            channel1.enableVibration(false);

            manager.createNotificationChannel(channel1);


            NotificationChannel channel2 = new NotificationChannel(DETECTOR_CH_ID, app.getString(R.string.activeness_detection_notification_channel_name), NotificationManager.IMPORTANCE_HIGH);
            channel2.setImportance(NotificationManager.IMPORTANCE_HIGH);
            AudioAttributes attr = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build();
            channel2.setSound(Uri.parse("android.resource://" + app.getPackageName() + "/" + R.raw.ariel), attr);
            channel2.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            channel2.enableVibration(true);

            manager.createNotificationChannel(channel2);


            NotificationChannel channel3 = new NotificationChannel(INFO_CH_ID, app.getString(R.string.firing_info_notification_channel_name), NotificationManager.IMPORTANCE_DEFAULT);
            channel3.setImportance(NotificationManager.IMPORTANCE_LOW);
            channel3.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            channel3.enableVibration(true);

            manager.createNotificationChannel(channel3);


            NotificationChannel channel4 = new NotificationChannel(DETECTOR_INFO_CH_ID, app.getString(R.string.activeness_detection_notification_info_channel_name), NotificationManager.IMPORTANCE_DEFAULT);
            channel4.setImportance(NotificationManager.IMPORTANCE_HIGH);
            channel4.setSound(Uri.parse("android.resource://" + app.getPackageName() + "/" + R.raw.high_intensity_alert), attr);
            channel4.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            channel4.enableVibration(true);

            manager.createNotificationChannel(channel4);
        }

        if (!prefs.getBoolean("appExitedProperly", false) || !prefs.getBoolean("firstLaunch", true)){
            sl.i("###urgent app exit detected###");
        }

        editor.putBoolean("appExitedProperly", false);
        editor.putBoolean("firstLaunch", false);
//        editor.putBoolean("notificationChannelsSet", false);
        editor.apply();

    }

    public static final String FIRING_CH_ID = "firing";
    public static final String INFO_CH_ID = "info";
    public static final String DETECTOR_CH_ID = "detector";
    public static final String DETECTOR_INFO_CH_ID = "detector_warning";
}

