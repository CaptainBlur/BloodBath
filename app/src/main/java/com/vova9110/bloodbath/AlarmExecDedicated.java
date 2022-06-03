package com.vova9110.bloodbath;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import com.vova9110.bloodbath.Database.AlarmRepo;

import java.util.Calendar;
import java.util.Date;

import javax.inject.Inject;

/*
Этого сервиса, по хорошему, вообще не должно было существовать,
но покуда инженеры Ведроида сделали AlarmManager, по которому через Интент нельзя передать Extras,
мне придётся каждый раз инициализироавать БД, чтобы обновить в ней данные и состояние будильника, такие дела
 */
public class AlarmExecDedicated extends Service {
    private final String TAG = "TAG_A-ExecDe";
    public AlarmRepo repo;

    private AlarmManager AManager;
    private AlarmManager.AlarmClockInfo info;
    private Intent broadcastI;
    private PendingIntent activePI;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d (TAG, "Dedicated service started");
        AManager = (android.app.AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);
        broadcastI = new Intent(getApplicationContext(), AlarmDeployReceiver.class);
        Calendar currentCalendar = Calendar.getInstance();
        repo = (AlarmRepo) intent.getSerializableExtra("repo");
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onDestroy() {
        Log.d (TAG, "Dedicated service stopped");
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
