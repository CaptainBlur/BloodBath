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
    }

    /*для будущих поколений: флаги означют то, на каких условиях система запустила сервис,
        а стартИД обозначает идентификатор хоста, запустившего сервис. Чтобы остановить сервис, вызванный конкретным хостом, используют stopSelfResult.
        Там гемора выше крыши с параллельным запуском сервисов, так что ну его нахрен пока что*/
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d (TAG, "Service started, start id: " + startId);
        Calendar currentCalendar = Calendar.getInstance();
        repo = (AlarmRepo) intent.getSerializableExtra("repo");

        AlarmManager.AlarmClockInfo info = AManager.getNextAlarmClock();
        Alarm firstActive = repo.getFirstActive();

        try {
            if (info == null & firstActive != null){//Такой вариант возможен, если другие приложения (при их наличии) не запланировали своих будильников
                currentCalendar.setTime(firstActive.getInitialTime());
                Log.d (TAG, "Setting alarm on: " + currentCalendar.getTime());

                activePI = PendingIntent.getActivity(getApplicationContext(), 0, activeI, PendingIntent.FLAG_IMMUTABLE);
                info = new AlarmManager.AlarmClockInfo(currentCalendar.getTimeInMillis(), activePI);
                AManager.setAlarmClock(info, activePI);

            }

            else if (firstActive != null) {
                Log.d (TAG, "Found planned alarm: " + new Date(info.getTriggerTime()));

                activePI = PendingIntent.getActivity(getApplicationContext(), 0, activeI, PendingIntent.FLAG_IMMUTABLE);
                AManager.cancel(activePI);
                }
        }
        catch (NullPointerException e){
            e.printStackTrace();
        }

        stopSelf();
        return super.onStartCommand(intent, flags, startId);
    }
}
