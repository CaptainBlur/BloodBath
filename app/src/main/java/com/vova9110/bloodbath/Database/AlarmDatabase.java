package com.vova9110.bloodbath.Database;

import androidx.room.Database;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

@Database(entities = {Alarm.class}, version = 1, exportSchema = false)
@TypeConverters({Converter.class})
public abstract class AlarmDatabase extends RoomDatabase {
    public abstract AlarmDao alarmDao ();
}
