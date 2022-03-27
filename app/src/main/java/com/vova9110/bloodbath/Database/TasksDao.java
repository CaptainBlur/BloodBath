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
    void insert (Tasks task); // В таблицу заносятся целые экземпляры Tasks

    @Query("DELETE FROM tasks_table")
    void deleteAll();

    @Query("DELETE FROM tasks_table WHERE task = :delTask")
    void deleteOne (String delTask);

    @Query("SELECT * FROM tasks_table ORDER BY task ASC")
    LiveData<List<Tasks>> getAllTasks();

}
