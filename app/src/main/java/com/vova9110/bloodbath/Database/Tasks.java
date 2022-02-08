package com.vova9110.bloodbath.Database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity (tableName = "tasks_table")
public class Tasks {
    @PrimaryKey (autoGenerate = true)
    private int id;
    private String task;

    public Tasks (String task) {this.task = task;}
    public String getTask() {return this.task;}

    public int getId() {
        return id;
    }
    public void setId(int id) {
        this.id = id;
    }
}
