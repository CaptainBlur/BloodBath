package com.vova9110.bloodbath.Database;

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

    @Query("UPDATE alarms_table SET onOffState = :switcherState WHERE hour = :hour AND minute = :minute")
    void updateState (int hour, int minute, boolean switcherState);

    @Query("DELETE FROM alarms_table WHERE hour < 25")
    void deleteAll();

    @Query("DELETE FROM alarms_table WHERE hour = :hour AND minute = :minute")
    void deleteOne (int hour, int minute);

    @Query("SELECT * FROM alarms_table")
    LiveData<List<Alarm>> getLD();
}
