package com.vova9110.bloodbath;

import android.app.AlarmManager;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AppCompatActivity;

import com.vova9110.bloodbath.Database.Alarm;
import com.vova9110.bloodbath.Database.AlarmRepo;

import javax.inject.Inject;

public class AlarmActivity extends AppCompatActivity {
    private final String TAG = "TAG_AA";
    @Inject
    public AlarmRepo repo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "Alarm Activity started");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alarm);
        setShowWhenLocked(true);
        setTurnScreenOn(true);
        DaggerAppComponent.builder().dBModule(new DBModule(getApplication())).build().inject(this);

        new Thread(dismissRunnable).start();

    }

    Runnable dismissRunnable = new Runnable() {
        @Override
        public void run() {
            Alarm current = repo.getActives().get(0);
            Log.d (TAG, "Alarm " + current.getHour() + current.getMinute() + " dismissed");
            current.setOnOffState(false);
            repo.update(current);
        }
    };
}