package com.vova9110.bloodbath.Database;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Database(entities = {Alarm.class}, version = 1, exportSchema = false)
public abstract class AlarmDatabase extends RoomDatabase {
    public abstract AlarmDao alarmDao ();
    private static volatile AlarmDatabase INSTANCE; //создаём переменную, которая хранит в себе весь экземпляр базы данных. Соответственно, она остаётся в памяти после выхода из приложения
    private static final int NUMBER_OF_THREADS = 4;
    public static final ExecutorService databaseWriteExecutor = Executors.newFixedThreadPool(NUMBER_OF_THREADS); // создаём executor service с фиксированным пулом потоков
    //для  работы с базой данных. Как я понимаю, его можно создать и в другом месте, но тут для него наиболее подходящее место, плюс его удобно вызывать как статическое поле через обращение к БД
    public static AlarmDatabase getDatabase(final Context context){// при обращении к методу getDatabase, он работает как singleton и в любом случае возвращает объект БД.
        if (INSTANCE == null){ // проверяем, не записывалась ли эта переменная прежде
            synchronized (AlarmDatabase.class){ // запускаем создание базы данных
                if (INSTANCE == null) { // провереяем, не записалась ли эта переменная в период после предыдущей проверки
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),AlarmDatabase.class, "alarms_database").build();
                    Log.d("TAG", "CREATING DB");
                }
            }
        }
        return INSTANCE;
    }
}
