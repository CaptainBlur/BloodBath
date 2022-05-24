package com.vova9110.bloodbath;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;

/*
Главной функцией является установка ближайшего будильника и его снятие, при необходимости. Вызывается вручную в ряде случаев, и автоматически только после старта системы
Дополнительная функция - вывод уведомления о приближающемся будильнике
Должен самостоятельно определять, стоит ли будильник уже, и сравнивать его с ближайшим включённым из БД
 */
public class AlarmExec extends Service {
    private final String TAG = "TAG_AEserv";
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {//для будущих поколений: флаги означют то, на каких условиях система запустила сервис,
        //а стартИД обозначает идентификатор хоста, запустившего сервис. Чтобы остановить сервис, вызванный конкретным хостом, используют stopSelfResult.
        //Там гемора выше крыши с параллельным запуском сервисов, так что ну его нахрен пока что
        Log.d (TAG, "Service started, start id: " + startId);
        stopSelf();
        return super.onStartCommand(intent, flags, startId);
    }
}
