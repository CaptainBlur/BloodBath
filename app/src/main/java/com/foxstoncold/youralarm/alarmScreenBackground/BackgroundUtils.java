package com.foxstoncold.youralarm.alarmScreenBackground;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.HandlerThread;

import androidx.core.app.NotificationCompat;

import com.foxstoncold.youralarm.MainViewModel;
import com.foxstoncold.youralarm.R;
import com.foxstoncold.youralarm.SplitLogger;
import com.foxstoncold.youralarm.database.Alarm;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class BackgroundUtils {
    private static SplitLogger sl;
    static IntentSchedulerListener testListener = new IntentSchedulerListener() {
        @Override
        public void onInfoPassed(String state, long time, String targetName) {}
        @Override
        public void onInfoCancelled(String state, String targetName) {}

        @Override
        public void onNotificationCreated(String state) {}
        @Override
        public void onNotificationCancelled(String state) {}
    };

    private static final int DISABLE_PREF = 35;
    private static final int SUSPEND_PREF = 79;
    private static final int ANTICIPATE_PREF = 46;
    private static final int PREPARE_PREF = 56;
    private static final int FIRE_PREF = 11;
    public static final int PRELIMINARY_PREF = 89;
    private static final int SNOOZE_PREF = 69;
    private static final int MISS_PREF = 29;
    public static final int UNIVERSAL_NOT_ID = 911;


    static protected String getGlobalID(Context context){
        SharedPreferences pref = context.getSharedPreferences(MainViewModel.PREFERENCES_SP, Context.MODE_PRIVATE);
        String global = pref.getString("global", "null");
        if (global.equals("null")) throw new IllegalStateException("Cannot proceed with *null* globalID");

        return global;
    }

    public static void setGlobalID(Context context, AlarmRepo repo){
        SharedPreferences pref = context.getSharedPreferences(MainViewModel.PREFERENCES_SP, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();

        List<Alarm> repoActives = repo.getActives();
        if (repoActives.isEmpty()){
            SplitLogger.frpc("no actives in repo to calculate new globalID");
            editor.putString("global", "null");
            editor.apply();
            return;
        }

        LinkedList<Alarm> actives = new LinkedList<>(repoActives);
        actives.sort(Comparator.comparing(Alarm::getTriggerTime));
        String id = actives.get(0).getId();
        boolean pr = actives.get(0).getInfo(context).getPreliminary();

        editor.putString("global", id);
        if (editor.commit()) sl.fstpc("globalID has set for " + id + ((pr) ? " (preliminary)" : ""));
        else sl.spc("error setting globalID for " + id);
    }

    //Implying we need to show notification wneh Main interface is loaded,
    //and also reassure FCS on new firing event
    static protected void requestErrorNotification(Context context){
        SharedPreferences pref = context.getSharedPreferences(MainViewModel.PREFERENCES_SP, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();

        editor.putBoolean("firing_error", true);
//        editor.putBoolean("reassure_repo", true);
        editor.apply();
        sl.w("error request passed");
    }

    static protected void putReloadRequest(Context context){
        SharedPreferences pref = context.getSharedPreferences(MainViewModel.PREFERENCES_SP, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = pref.edit();

        editor.putBoolean("rv_reload", true);
        editor.apply();
    }

    static protected MediaPlayer returnPlayer(Context context, SubInfo info) throws IOException {
        AudioAttributes attributes = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        MediaPlayer r = new MediaPlayer();
            r.setDataSource(context, info.getSoundPath());
            r.setAudioAttributes(attributes);
            r.setLooping(true);
            r.prepare();
        return r;
    }

    static protected void scheduleExact(Context context, String id, String state, long time){
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, FiringControlService.class);
        intent.setAction(id);
        intent.setFlags(Intent.FLAG_RECEIVER_FOREGROUND);

        String composedID;
        switch (state) {
            case Alarm.STATE_ALL -> {
                sl.sp("Cannot proceed with *STATE_ALL* action");
                return;
            }
            case Alarm.STATE_ANTICIPATE -> composedID = String.valueOf(ANTICIPATE_PREF);
            case Alarm.STATE_DISABLE -> composedID = String.valueOf(DISABLE_PREF);
            case Alarm.STATE_PRELIMINARY -> composedID = String.valueOf(PRELIMINARY_PREF);
            case Alarm.STATE_FIRE -> composedID = String.valueOf(FIRE_PREF);
            case Alarm.STATE_MISS -> composedID = String.valueOf(MISS_PREF);
            case Alarm.STATE_PREPARE -> composedID = String.valueOf(PREPARE_PREF);
            case Alarm.STATE_SNOOZE -> composedID = String.valueOf(SNOOZE_PREF);
            case Alarm.STATE_SUSPEND -> composedID = String.valueOf(SUSPEND_PREF);
            default -> {
                composedID = "";
                throw new IllegalArgumentException("Cannot operate without proper state");
            }
        }
        composedID+=id;

        PendingIntent pending = PendingIntent.getForegroundService(context, Integer.parseInt(composedID), intent, PendingIntent.FLAG_IMMUTABLE);
        manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pending);

        sl.fst("Scheduling exact with id *" + composedID + "* (state *" + state + "*) on " + new SimpleDateFormat("*d,EEE,HH:mm*", Locale.getDefault()).format(new Date(time)));
        testListener.onInfoPassed(state, time, AlarmExecutionDispatch.class.getName());
    }

    static protected void scheduleAlarm(Context context, String id, String state, long time){
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, FiringControlService.class);
        intent.setAction(id);
        intent.setFlags(Intent.FLAG_RECEIVER_FOREGROUND);

        String composedID;
        switch (state) {
            case Alarm.STATE_ALL -> {
                sl.sp("Cannot proceed with *STATE_ALL* action");
                return;
            }
            case Alarm.STATE_FIRE -> composedID = String.valueOf(FIRE_PREF);
            case Alarm.STATE_PRELIMINARY -> composedID = String.valueOf(PRELIMINARY_PREF);
            case Alarm.STATE_SNOOZE -> composedID = String.valueOf(SNOOZE_PREF);
            default -> {
                composedID = "";
                throw new IllegalArgumentException("Cannot operate without proper state");
            }
        }
        composedID+=id;

        PendingIntent pending = PendingIntent.getForegroundService(context, Integer.parseInt(composedID), intent, PendingIntent.FLAG_IMMUTABLE);
        AlarmManager.AlarmClockInfo info = new AlarmManager.AlarmClockInfo(time, pending);
        manager.setAlarmClock(info, pending);

        sl.fst("Scheduling alarm with id *" + composedID + "* on " + new SimpleDateFormat("*d,EEE,HH:mm*", Locale.getDefault()).format(new Date(time)));
        testListener.onInfoPassed(state, time, AlarmExecutionDispatch.class.getName());
    }

    static protected void cancelPI(Context context, String id, String state){
        if (state.equals(Alarm.STATE_ALL)){
            sl.fr("Control cancelling all possible PI for *" + id + "*");
            testListener.onInfoCancelled(state, AlarmExecutionDispatch.class.getName());

            cancelParticular(context, id, Alarm.STATE_DISABLE);
            cancelParticular(context, id, Alarm.STATE_SUSPEND);
            cancelParticular(context, id, Alarm.STATE_ANTICIPATE);
            cancelParticular(context, id, Alarm.STATE_PREPARE);
            cancelParticular(context, id, Alarm.STATE_FIRE);
            cancelParticular(context, id, Alarm.STATE_PRELIMINARY);
            cancelParticular(context, id, Alarm.STATE_SNOOZE);
            cancelParticular(context, id, Alarm.STATE_MISS);
            return;
        }

        testListener.onInfoCancelled(state, AlarmExecutionDispatch.class.getName());
        cancelParticular(context, id, state);

        sl.fst("Cancelling alarm for state *" + state + "* with id *" + id + "*");
    }
    static private void cancelParticular (Context context, String id, String state){
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, FiringControlService.class);
        intent.setAction(id);
        intent.setFlags(Intent.FLAG_RECEIVER_FOREGROUND);

        String composedID = switch (state) {
            case Alarm.STATE_ANTICIPATE -> String.valueOf(ANTICIPATE_PREF);
            case Alarm.STATE_DISABLE -> String.valueOf(DISABLE_PREF);
            case Alarm.STATE_FIRE -> String.valueOf(FIRE_PREF);
            case Alarm.STATE_PRELIMINARY -> String.valueOf(PRELIMINARY_PREF);
            case Alarm.STATE_MISS -> String.valueOf(MISS_PREF);
            case Alarm.STATE_PREPARE -> String.valueOf(PREPARE_PREF);
            case Alarm.STATE_SNOOZE -> String.valueOf(SNOOZE_PREF);
            case Alarm.STATE_SUSPEND -> String.valueOf(SUSPEND_PREF);
            default -> "";
        };
        composedID+=id;

        PendingIntent pending = PendingIntent.getForegroundService(context, Integer.parseInt(composedID), intent,PendingIntent.FLAG_IMMUTABLE + PendingIntent.FLAG_CANCEL_CURRENT);
        manager.cancel(pending);
    }
    static private String getClockString(String id){
        int h = Integer.parseInt(id.substring(0,2));
        int m = Integer.parseInt(id.substring(2,4));
        return getClockString(h, m);
    }
    static private String getClockString(int h, int m){
        return String.format(Locale.ENGLISH, "%02d:%02d", h, m);
    }
    static private PendingIntent composeButtonPI(Context context, String id, String targetState, int requestCode){
        Intent intent = new Intent(context, AlarmExecutionDispatch.class);
        intent.putExtra(AlarmExecutionDispatch.extraID, id);
        intent.putExtra(AlarmExecutionDispatch.extraState, targetState);

        String composedRequest = String.valueOf(requestCode);
        composedRequest+=id;

        return PendingIntent.getBroadcast(
                context,
                Integer.parseInt(composedRequest),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }
    static protected void createPreliminaryNotification(Context context, String id, Date preliminaryTime){
        Calendar pC = Calendar.getInstance();
        pC.setTime(preliminaryTime);

        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, MainViewModel.INFO_CH_ID)
                .setSmallIcon(R.drawable.ic_clock_alarm)
                .setShowWhen(false)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentTitle("Preliminary alarm for " + getClockString(pC.get(Calendar.HOUR_OF_DAY), pC.get(Calendar.MINUTE)))
                .setContentText("Main in " + getClockString(id))
                .addAction(new NotificationCompat.Action(null, "Dismiss preliminary",
                        composeButtonPI(context, id, AlarmExecutionDispatch.targetStateDismissPreliminary, 1)))
                .addAction(new NotificationCompat.Action(null, "Dismiss",
                        composeButtonPI(context, id, AlarmExecutionDispatch.targetStateDismiss, 2)));

        manager.notify(id + Alarm.STATE_PREPARE, UNIVERSAL_NOT_ID, builder.build());
    }

    static protected void createNotification(Context context, String id, String state){
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, MainViewModel.INFO_CH_ID)
                .setSmallIcon(R.drawable.ic_clock_alarm)
                .setShowWhen(false)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        switch (state) {
            case (Alarm.STATE_PREPARE) ->
                    builder.setContentTitle("Upcoming alarm")
                            .setContentText("for " + getClockString(id))
                            .addAction(new NotificationCompat.Action(null, "Dismiss",
                                    composeButtonPI(context, id, AlarmExecutionDispatch.targetStateDismiss, 1)));
            case (Alarm.STATE_PREPARE_SNOOZE) ->
                    builder.setContentTitle("Snoozed alarm")
                            .setContentText("in " + getClockString(id))
                            .addAction(new NotificationCompat.Action(null, "Dismiss",
                                    composeButtonPI(context, id, AlarmExecutionDispatch.targetStateDismiss, 1)));
            case (Alarm.STATE_MISS) -> builder.setContentTitle("Missed alarm")
                    .setContentText("in " + getClockString(id));
            default -> throw new IllegalArgumentException("Cannot operate without proper state");
        }

        manager.notify(id + state, UNIVERSAL_NOT_ID, builder.build());

        testListener.onNotificationCreated(state);
    }

    static protected void cancelNotification(Context context, String id, String state){
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Objects.equals(state, Alarm.STATE_ALL)){
            sl.fr("Control cancelling all possible notifications for *" + id + "*");
            testListener.onNotificationCancelled(state);

            manager.cancel(id + Alarm.STATE_MISS, UNIVERSAL_NOT_ID);
            manager.cancel(id + Alarm.STATE_PREPARE, UNIVERSAL_NOT_ID);
            manager.cancel(id + Alarm.STATE_PREPARE_SNOOZE, UNIVERSAL_NOT_ID);
        }
        else {
            manager.cancel(id + state, UNIVERSAL_NOT_ID);
            sl.fst("Cancelling alarm notification, state *" + state + "*, id *" + id + "*");
            testListener.onNotificationCancelled(state);
        }
    }

    static protected void createBroadcast(Context context, String id){
        Intent intent = new Intent(context, FiringControlService.class);
        intent.setAction(id);
        sl.fst("Starting FCR with id *" + id + "*");

        context.startForegroundService(intent);
    }

    static protected Handler getHandler(String name){
        HandlerThread thread = new HandlerThread(name);
        thread.start();
        return new Handler(thread.getLooper());
    }


    interface IntentSchedulerListener{
        void onInfoPassed(String state, long time, String targetName);
        void onInfoCancelled(String state, String targetName);
        void onNotificationCreated(String state);
        void onNotificationCancelled(String state);
    }
}
