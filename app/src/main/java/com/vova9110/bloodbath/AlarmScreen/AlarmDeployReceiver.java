package com.vova9110.bloodbath.AlarmScreen;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.vova9110.bloodbath.AlarmActivity;
import com.vova9110.bloodbath.Database.Alarm;
import com.vova9110.bloodbath.Database.AlarmRepo;

import java.util.Date;
import java.util.List;

public class AlarmDeployReceiver extends BroadcastReceiver {
    private static String TAG = "TAG_AScreenReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Alarm broadcast received!");

        Intent alarmIntent = new Intent(context, AlarmSupervisor.class);
        alarmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(alarmIntent);


//        Alarm active = repo.getActives().get(0);
//        Log.d(TAG, "disabling: " + Integer.parseInt(String.valueOf(active.getHour()).concat(String.valueOf(active.getMinute()))));

    }
}