package com.vova9110.bloodbath.alarmScreenBackground;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.PowerManager;

import androidx.core.app.NotificationCompat;

import com.vova9110.bloodbath.MainViewModel;
import com.vova9110.bloodbath.R;
import com.vova9110.bloodbath.SplitLogger;
import com.vova9110.bloodbath.database.Alarm;

import java.text.SimpleDateFormat;
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
    private static final int SNOOZE_PREF = 69;
    private static final int MISS_PREF = 29;
    public static final int UNIVERSAL_NOT_ID = 911;

    public static final String INTERLAYER_EXTRA = "interlayer";

    static protected String getGlobalID(Context context){
        SharedPreferences pref = context.getSharedPreferences(MainViewModel.PREFERENCES_NAME, Context.MODE_PRIVATE);
        return pref.getString("global", "null");
    }
    public static void setGlobalID(Context context, AlarmRepo repo){
        SharedPreferences pref = context.getSharedPreferences(MainViewModel.PREFERENCES_NAME, Context.MODE_PRIVATE);
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

        editor.putString("global", id);
        editor.apply();
        sl.fstpc("globalID has set for " + id);
    }

    static protected void scheduleExact(Context context, String id, String state, long time){
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, FiringControlService.class);
        intent.setAction(id);
        intent.setFlags(Intent.FLAG_RECEIVER_FOREGROUND);

        String composedID;
        switch (state){
            case Alarm.STATE_ALL: {
                sl.sp("Cannot proceed with *STATE_ALL* action");
                return;
            }
            case Alarm.STATE_ANTICIPATE: composedID = String.valueOf(ANTICIPATE_PREF); break;
            case Alarm.STATE_DISABLE: composedID = String.valueOf(DISABLE_PREF); break;
            case Alarm.STATE_FIRE: composedID = String.valueOf(FIRE_PREF); break;
            case Alarm.STATE_MISS: composedID = String.valueOf(MISS_PREF); break;
            case Alarm.STATE_PREPARE: composedID = String.valueOf(PREPARE_PREF); break;
            case Alarm.STATE_SNOOZE: composedID = String.valueOf(SNOOZE_PREF); break;
            case Alarm.STATE_SUSPEND: composedID = String.valueOf(SUSPEND_PREF); break;
            default: composedID = "";
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
        switch (state){
            case Alarm.STATE_ALL: {
                sl.sp("Cannot proceed with *STATE_ALL* action");
                return;
            }
            case Alarm.STATE_FIRE: composedID = String.valueOf(FIRE_PREF); break;
            case Alarm.STATE_SNOOZE: composedID = String.valueOf(SNOOZE_PREF); break;
            default: composedID = "";
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

        String composedID;
        switch (state){
            case Alarm.STATE_ANTICIPATE: composedID = String.valueOf(ANTICIPATE_PREF); break;
            case Alarm.STATE_DISABLE: composedID = String.valueOf(DISABLE_PREF); break;
            case Alarm.STATE_FIRE: composedID = String.valueOf(FIRE_PREF); break;
            case Alarm.STATE_MISS: composedID = String.valueOf(MISS_PREF); break;
            case Alarm.STATE_PREPARE: composedID = String.valueOf(PREPARE_PREF); break;
            case Alarm.STATE_SNOOZE: composedID = String.valueOf(SNOOZE_PREF); break;
            case Alarm.STATE_SUSPEND: composedID = String.valueOf(SUSPEND_PREF); break;
            default: composedID = "";
        }
        composedID+=id;

        PendingIntent pending = PendingIntent.getForegroundService(context, Integer.parseInt(composedID), intent,PendingIntent.FLAG_IMMUTABLE + PendingIntent.FLAG_CANCEL_CURRENT);
        manager.cancel(pending);
    }

    static protected void createNotification(Context context, String id, String state){
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, MainViewModel.INFO_CH_ID)
                .setSmallIcon(R.drawable.ic_clock_alarm)
                .setShowWhen(false)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC);
        switch (state){
            case(Alarm.STATE_PREPARE): builder.setContentTitle("Upcoming in: " + id); break;
            case(Alarm.STATE_PREPARE_SNOOZE): builder.setContentTitle("Upcoming snooze for: " + id); break;
            case(Alarm.STATE_MISS): builder.setContentTitle("Missed: " + id); break;
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
            sl.fst("Cancelling alarm for state *" + state + "* for *" + id + "*");
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
