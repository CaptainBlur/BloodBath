package com.vova9110.bloodbath.Database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface AlarmDao {
    @Insert (onConflict = OnConflictStrategy.IGNORE)
    void insert (Alarm alarm); // В таблицу заносятся целые экземпляры Tasks

    @Query("DELETE FROM alarms_table WHERE hour < 25")
    void deleteAll();

    @Query("DELETE FROM alarms_table WHERE hour = :hour AND minute = :minute")
    void deleteOne (int hour, int minute);

    @Query("SELECT * FROM alarms_table")
    LiveData<List<Alarm>> getLD();
}
