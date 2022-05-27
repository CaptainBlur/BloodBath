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

    @Query("DELETE FROM alarms_table WHERE hour < 25")
    void deleteAll();

    @Query("DELETE FROM alarms_table WHERE hour = :hour AND minute = :minute")
    void deleteOne (int hour, int minute);

    @Query("SELECT * FROM alarms_table")
    LiveData<List<Alarm>> getLD();

    @Query("SELECT * FROM alarms_table WHERE onOffState ORDER BY initialTime ASC LIMIT 1")
    Alarm getFirstActive ();

    @Query("SELECT * FROM alarms_table WHERE wasPassive LIMIT 1")
    Alarm getWasPassive ();

    @Query("SELECT * FROM alarms_table WHERE wasActive LIMIT 1")
    Alarm getWasActive ();
}
