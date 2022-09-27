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
import java.util.List;

/*
Главной функцией является занос будильников в пул AlarmManager
Вызывается после обновления состояния одного будильника или после перезагрузки/перезапуска приложения,
Дополнительная функция - вывод уведомления о приближающемся будильнике
 */
public class AlarmExec extends Service {
    private final String TAG = "TAG_A-Exec";
    private AlarmRepo repo;

    private AlarmManager AManager;
    private AlarmManager.AlarmClockInfo info;
    private Intent broadcastI;
    private PendingIntent activePI;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /*для будущих поколений: флаги означют то, на каких условиях система запустила сервис,
        а стартИД обозначает идентификатор хоста, запустившего сервис. Чтобы остановить сервис, вызванный конкретным хостом, используют stopSelfResult.
        Там гемора выше крыши с параллельным запуском сервисов, так что ну его нахрен пока что*/
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        AManager = (android.app.AlarmManager) getApplicationContext().getSystemService(Context.ALARM_SERVICE);
        broadcastI = new Intent(getApplicationContext(), AlarmDeployReceiver.class);
        Log.d (TAG, "Starting service. NEXT ALARM AT: " + new Date (AManager.getNextAlarmClock().getTriggerTime()));
        Calendar currentCalendar = Calendar.getInstance();
        repo = (AlarmRepo) intent.getSerializableExtra("repo");

        Alarm prevPassive = repo.findPrevPassive();
        Alarm prevActive = repo.findPrevActive();
        List<Alarm> actives = repo.getActives();
        AlarmManager.AlarmClockInfo NCInfo = AManager.getNextAlarmClock();

        //todo add formatter
        if (prevPassive != null){//Если сервис был вызван для обновления состояния одного будильника, то проверяются первые два условия
            prevPassive.setWasPassive(false);
            repo.update(prevPassive);

            int ID = Integer.parseInt(String.valueOf(prevPassive.getHour()).concat(String.valueOf(prevPassive.getMinute())));

            activePI = PendingIntent.getBroadcast(getApplicationContext(), ID, broadcastI, PendingIntent.FLAG_IMMUTABLE);
            info = new AlarmManager.AlarmClockInfo(prevPassive.getTriggerTime().getTime(), activePI);
            AManager.setAlarmClock(info, activePI);
            Log.d (TAG, "Setting changed alarm with id: " + ID);
        }
        else if (prevActive != null){
            prevActive.setWasActive(false);
            repo.update(prevActive);

            int ID = Integer.parseInt(String.valueOf(prevActive.getHour()).concat(String.valueOf(prevActive.getMinute())));

            activePI = PendingIntent.getBroadcast(getApplicationContext(), ID, broadcastI, PendingIntent.FLAG_NO_CREATE + PendingIntent.FLAG_IMMUTABLE);
            AManager.cancel(activePI);
            Log.d (TAG, "Cancelling changed alarm with id: " + ID);
        }
        else if (actives.size()!=0 & !NCInfo.getShowIntent().getCreatorPackage().matches(getApplicationContext().getPackageName())){//Если непосредственно перед вызовом не меняли состояний будильников, значит, все будильники нужно выставить заново
            actives = repo.getActives();
            for (Alarm active : actives){
                int ID = Integer.parseInt(String.valueOf(active.getHour()).concat(String.valueOf(active.getMinute())));

                Log.d (TAG, "setting erased: " + ID);
                PendingIntent PI = PendingIntent.getBroadcast(getApplicationContext(), ID, broadcastI, PendingIntent.FLAG_IMMUTABLE);
                AlarmManager.AlarmClockInfo info = new AlarmManager.AlarmClockInfo(active.getTriggerTime().getTime(), PI);
                AManager.setAlarmClock(info, PI);
            }
        }
        Log.d (TAG, "NEXT ALARM AT: " + new Date (AManager.getNextAlarmClock().getTriggerTime()));

        stopSelf();
        return super.onStartCommand(intent, flags, startId);
    }
}
