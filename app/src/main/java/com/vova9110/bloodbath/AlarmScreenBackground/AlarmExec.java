package com.vova9110.bloodbath.AlarmScreenBackground;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.vova9110.bloodbath.AlarmDeployReceiver;
import com.vova9110.bloodbath.Database.Alarm;
import com.vova9110.bloodbath.Database.AlarmRepo;
import com.vova9110.bloodbath.ExecReceiver;
import com.vova9110.bloodbath.R;

import java.text.DecimalFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/*
This service should be idempotent, so we can call it anytime
After Delete, Change, Update actions in the interface, we have to call Exec to modify appointments. And there is no need for Repo instance for it.
All firing alarms remain on state until finally shut down. Supervisor changes delay and preliminary flags, and Executor sets them on a new time, pulling that data from the alarms themselves.
Finally, there we need to handle casual process of rescheduling alarms after each app start
 */
public class AlarmExec extends Service {
    public static final String IntentAction = "com.vova9110.bloodbath.START_EXEC";
    private final String TAG = "TAG_A-Exec";
    private AlarmRepo repo;
    private AlarmManager AManager;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        AManager = (android.app.AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);
        Log.d (TAG, "Starting service. NEXT ALARM AT: " + new Date (AManager.getNextAlarmClock().getTriggerTime()));
        repo = (AlarmRepo) intent.getSerializableExtra("repo");

        List<Alarm> actives = repo.getActives();

        Intent broadcastI = new Intent(getApplicationContext(), AlarmDeployReceiver.class);
        Intent execI = new Intent(getApplicationContext(), ExecReceiver.class);
        execI.setAction(IntentAction);
        PendingIntent activePI;
        PendingIntent execPI;

        if (intent.getBooleanExtra("prevActive", false)){
            if (intent.getLongExtra("triggerTime", -1)==-1) Log.d(TAG, "\ttrigger time not found, returning");
            else {
                Calendar calendar = Calendar.getInstance();
                calendar.setTimeInMillis(intent.getLongExtra("triggerTime", -1L));
                Calendar scheduledCalendar = getCalendar(calendar.getTime());

                int ID = getID(calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE));
                int execID = getID(scheduledCalendar.get(Calendar.HOUR_OF_DAY), scheduledCalendar.get(Calendar.MINUTE));

                activePI = PendingIntent.getBroadcast(getApplicationContext(), ID, broadcastI, PendingIntent.FLAG_IMMUTABLE);
                execPI = PendingIntent.getBroadcast(getApplicationContext(), execID, execI, PendingIntent.FLAG_IMMUTABLE);

                AManager.cancel(execPI);
                AManager.cancel(activePI);
                Log.d(TAG, "\tControl cancelling appointments on: " + execID + ", " + ID);

                if (actives.size()!=0)
                    setFromActives(actives, broadcastI, execI);
            }
        }
        //Here we check first active in the list and treat it depending on current time
        //And we are interested only in the first until it's out of the list
        //Like it's some sort of protection from interfering
        else {
            if (intent.getBooleanExtra("prevPassive", false) && actives.size()!=0) {//If both are presented
                Alarm active = actives.get(0);
                long intentTime = intent.getLongExtra("triggerTime", -1L);

                if (intentTime!=-1L && intentTime < active.getTriggerTime().getTime())
                    setFromIntent(intentTime, broadcastI, execI);

                else//Received triggerTime is invalid or triggerTime is later
                    setFromActives(actives, broadcastI, execI);
            }

            else if (intent.getBooleanExtra("prevPassive", false)){//If don't have actives in the repo
                long intentTime = intent.getLongExtra("triggerTime", -1L);
                if (intentTime!=-1L)
                    setFromIntent(intentTime, broadcastI, execI);
            }

            else if (actives.size()!=0)
                setFromActives(actives, broadcastI, execI);

            else Log.d(TAG, "\tNo actives found in repo and no received either. Exiting");
        }
        Log.d (TAG, "NEXT ALARM AT: " + new Date (AManager.getNextAlarmClock().getTriggerTime()));

        stopSelf();
        return super.onStartCommand(intent, flags, startId);
    }

    public static int getID(int hour, int minute){
        String str = "969";
        str += String.format("%02d", hour);
        str += String.format("%02d", minute) ;
        return Integer.parseInt(str);
    }
    private Calendar getCalendar (Date triggerTime){
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(triggerTime.getTime() - 10 * 60 * 60 * 1000);
        return calendar;
    }
    private void setFromActives(List<Alarm> actives, Intent broadcastI, Intent execI){
        AlarmManager.AlarmClockInfo NCInfo = AManager.getNextAlarmClock();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(actives.get(0).getTriggerTime());
        Calendar scheduledCalendar = getCalendar(calendar.getTime());

        if (NCInfo.getShowIntent().getCreatorPackage().matches(getApplicationContext().getPackageName()) && NCInfo.getTriggerTime()==calendar.getTimeInMillis()){
            Log.d(TAG, "\tNearest alarm set up right, exiting");
            //Means, if we have the nearest alarm already set up, we don't need to control cancel the rest of them
        }
        else {
            Log.d(TAG, "\tCan't match nearest firing appointment. Control cancelling all actives");
            for (Alarm alarm : actives) {
                Calendar calendar_ = Calendar.getInstance();
                calendar_.setTime(alarm.getTriggerTime());
                Calendar scheduledCalendar_ = getCalendar(calendar_.getTime());

                int ID = getID(calendar_.get(Calendar.HOUR_OF_DAY), calendar_.get(Calendar.MINUTE));
                int execID = getID(scheduledCalendar_.get(Calendar.HOUR_OF_DAY), scheduledCalendar_.get(Calendar.MINUTE));

                PendingIntent activePI = PendingIntent.getBroadcast(getApplicationContext(), ID, broadcastI, PendingIntent.FLAG_IMMUTABLE);
                PendingIntent execPI = PendingIntent.getBroadcast(getApplicationContext(), execID, execI, PendingIntent.FLAG_IMMUTABLE);

                AManager.cancel(execPI);
                AManager.cancel(activePI);
            }

            int ID = getID(calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE));
            int execID = getID(scheduledCalendar.get(Calendar.HOUR_OF_DAY), scheduledCalendar.get(Calendar.MINUTE));

            PendingIntent activePI = PendingIntent.getBroadcast(getApplicationContext(), ID, broadcastI, PendingIntent.FLAG_IMMUTABLE);
            PendingIntent execPI = PendingIntent.getBroadcast(getApplicationContext(), execID, execI, PendingIntent.FLAG_IMMUTABLE);

            if (System.currentTimeMillis() >= scheduledCalendar.getTimeInMillis()) {
                AlarmManager.AlarmClockInfo info = new AlarmManager.AlarmClockInfo(calendar.getTimeInMillis(), activePI);
                AManager.setAlarmClock(info, activePI);
                Log.d(TAG, "\tSetting alarm on: " + ID);
            } else {
                AManager.setExact(AlarmManager.RTC, scheduledCalendar.getTimeInMillis(), execPI);
                Log.d(TAG, "\tScheduling exec on " + execID);
            }
        }
    }
    private void setFromIntent(long intentTime, Intent broadcastI, Intent execI){
        Log.d(TAG, "\tSetting up alarm changed in the interface");
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(intentTime);
        Calendar scheduledCalendar = getCalendar(calendar.getTime());

        int ID = getID(calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE));
        int execID = getID(scheduledCalendar.get(Calendar.HOUR_OF_DAY), scheduledCalendar.get(Calendar.MINUTE));

        if (System.currentTimeMillis() >= scheduledCalendar.getTimeInMillis()) {
            PendingIntent activePI = PendingIntent.getBroadcast(getApplicationContext(), ID, broadcastI, PendingIntent.FLAG_IMMUTABLE);
            AlarmManager.AlarmClockInfo info = new AlarmManager.AlarmClockInfo(calendar.getTimeInMillis(), activePI);
            AManager.setAlarmClock(info, activePI);
            Log.d(TAG, "\tSetting alarm on: " + ID);
        } else {
            PendingIntent execPI = PendingIntent.getBroadcast(getApplicationContext(), execID, execI, PendingIntent.FLAG_IMMUTABLE);
            AManager.setExact(AlarmManager.RTC, scheduledCalendar.getTimeInMillis(), execPI);
            Log.d(TAG, "\tScheduling exec on " + execID);
        }
    }
}
