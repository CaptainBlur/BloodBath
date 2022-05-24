package com.vova9110.bloodbath.Database;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverter;
import androidx.room.TypeConverters;

import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Database(entities = {Alarm.class}, version = 1, exportSchema = false)
@TypeConverters({Converter.class})
public abstract class AlarmDatabase extends RoomDatabase {
    public abstract AlarmDao alarmDao ();
    private static final int NUMBER_OF_THREADS = 4;
    public static final ExecutorService databaseWriteExecutor = Executors.newFixedThreadPool(NUMBER_OF_THREADS); // создаём executor service с фиксированным пулом потоков
}
