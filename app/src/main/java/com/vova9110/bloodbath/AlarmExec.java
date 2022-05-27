package com.vova9110.bloodbath;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

import com.vova9110.bloodbath.Database.Alarm;
import com.vova9110.bloodbath.Database.AlarmRepo;

import java.util.Calendar;
import java.util.Date;

/*
Главной функцией является установка ближайшего будильника и его снятие, при необходимости. Вызывается вручную в ряде случаев, и автоматически только после старта системы
Дополнительная функция - вывод уведомления о приближающемся будильнике
Должен самостоятельно определять, стоит ли будильник уже, и сравнивать его с ближайшим включённым из БД
 */
public class AlarmExec extends Service {
    private final String TAG = "TAG_AEserv";
    private AlarmRepo repo;

    private AlarmManager AManager;
    private AlarmManager.AlarmClockInfo info;
    private Intent activeI;
    private PendingIntent activePI;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        AManager = (android.app.AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);
        activeI = new Intent(getApplicationContext(), AlarmActivity.class);
        Log.d (TAG, "NEXT ALARM AT: " + new Date (AManager.getNextAlarmClock().getTriggerTime()));
    }

    /*для будущих поколений: флаги означют то, на каких условиях система запустила сервис,
        а стартИД обозначает идентификатор хоста, запустившего сервис. Чтобы остановить сервис, вызванный конкретным хостом, используют stopSelfResult.
        Там гемора выше крыши с параллельным запуском сервисов, так что ну его нахрен пока что*/
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //Log.d (TAG, "Service started, start id: " + startId);
        Calendar currentCalendar = Calendar.getInstance();
        repo = (AlarmRepo) intent.getSerializableExtra("repo");

        Alarm prevPassive = repo.findPrevPassive();
        Alarm prevActive = repo.findPrevActive();

        if (prevPassive != null){//todo перевести АМ на бродкаст и ресивер, иначе хрен там заработает
            prevPassive.setWasPassive(false);
            repo.update(prevPassive);

            String hour = String.valueOf(prevPassive.getHour());
            int ID = Integer.parseInt(hour.concat(String.valueOf(prevPassive.getMinute())));

            activePI = PendingIntent.getActivity(getApplicationContext(), ID, activeI, PendingIntent.FLAG_IMMUTABLE);
            info = new AlarmManager.AlarmClockInfo(prevPassive.getInitialTime().getTime(), activePI);
            AManager.setAlarmClock(info, activePI);
            Log.d (TAG, "Setting alarm with id: " + ID);
        }
        else if (prevActive != null){
            prevActive.setWasActive(false);
            repo.update(prevActive);

            String hour = String.valueOf(prevActive.getHour());
            int ID = Integer.parseInt(hour.concat(String.valueOf(prevActive.getMinute())));

            activePI = PendingIntent.getActivity(getApplicationContext(), ID, activeI, PendingIntent.FLAG_IMMUTABLE);
            AManager.cancel(activePI);
            Log.d (TAG, "Cancelling alarm with id: " + ID);
        }
        Log.d (TAG, "NEXT ALARM AT: " + new Date (AManager.getNextAlarmClock().getTriggerTime()));

        stopSelf();
        return super.onStartCommand(intent, flags, startId);
    }
}
