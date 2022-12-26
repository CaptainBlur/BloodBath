package com.vova9110.bloodbath.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface AlarmDao {
    @Insert (onConflict = OnConflictStrategy.IGNORE)
    void insert (Alarm alarm);

    @Update (onConflict = OnConflictStrategy.REPLACE)
    void update (Alarm alarm);

    @Query("DELETE FROM alarms_table WHERE hour < 25")
    void deleteAll();

    @Query("DELETE FROM alarms_table WHERE hour = :hour AND minute = :minute")
    void deleteOne (int hour, int minute);

    @Query("SELECT * FROM alarms_table WHERE hour = :hour AND minute = :minute")
    Alarm getOne(int hour, int minute);

    @Query("SELECT * FROM alarms_table ORDER BY hour AND minute")
    List<Alarm> getAll();

    @Query("SELECT * FROM alarms_table WHERE enabled ORDER BY triggerTime ASC")
    List<Alarm> getActives ();
}
