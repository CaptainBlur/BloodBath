package com.vova9110.bloodbath;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.vova9110.bloodbath.Database.Alarm;
import com.vova9110.bloodbath.Database.AlarmRepo;

import java.util.Date;
import java.util.List;

public class AlarmDeployReceiver extends BroadcastReceiver {
    private static String TAG = "TAG_AReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Broadcast received!");
        AlarmRepo repo = (AlarmRepo) intent.getSerializableExtra("repo");
        Log.d (TAG, "oads;" + intent);


        Intent alarmIntent = new Intent(context, AlarmActivity.class);
        alarmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(alarmIntent);

//        Alarm active = repo.getActives().get(0);
//        Log.d(TAG, "disabling: " + Integer.parseInt(String.valueOf(active.getHour()).concat(String.valueOf(active.getMinute()))));
//        active.setOnOffState(false);
//        repo.update(active);
    }
}