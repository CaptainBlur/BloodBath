package com.vova9110.bloodbath.Database;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity (tableName = "tasks_table")
public class Tasks {
    @NonNull
    @PrimaryKey
    private String task;

    public Tasks (String task) {this.task = task;}
    public String getTask() {return this.task;}
}
