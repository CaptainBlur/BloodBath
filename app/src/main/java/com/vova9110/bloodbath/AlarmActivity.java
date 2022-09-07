package com.vova9110.bloodbath;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import com.vova9110.bloodbath.Database.Alarm;
import com.vova9110.bloodbath.Database.AlarmRepo;
import javax.inject.Inject;

public class AlarmActivity extends AppCompatActivity {
    private final String TAG = "TAG_AA";
    Runnable dismissRunnable = () -> {
        Alarm current = AlarmActivity.this.repo.getActives().get(0);
        Log.d(TAG, "Alarm " + current.getHour() + current.getMinute() + " dismissed");
        current.setOnOffState(false);
        AlarmActivity.this.repo.update(current);
    };
    @Inject
    public AlarmRepo repo;

    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "Alarm Activity started");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alarm);
        setShowWhenLocked(true);
        setTurnScreenOn(true);
        DaggerAppComponent.builder().dBModule(new DBModule(getApplication())).build().inject(this);
        new Thread(this.dismissRunnable).start();
        getApplicationContext().startService(new Intent(getApplicationContext(), ActivenessDetectionService.class));
    }
}