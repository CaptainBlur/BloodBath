package com.vova9110.bloodbath.Database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface TasksDao {
    @Insert (onConflict = OnConflictStrategy.IGNORE)
    void insert (Tasks task);

    @Query("DELETE FROM tasks_table")
    void deleteAll();

    @Query("SELECT * FROM tasks_table WHERE task = :selTask")
    Tasks selectByTask (String selTask);

    @Query("DELETE FROM tasks_table WHERE id = :did")
    void deleteById (int did);

    @Query("SELECT * FROM tasks_table ORDER BY id ASC")
    LiveData<List<Tasks>> getAllTasks();
}
