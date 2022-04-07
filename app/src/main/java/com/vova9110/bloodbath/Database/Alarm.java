package com.vova9110.bloodbath.Database;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity (tableName = "alarms_table", primaryKeys = {"hour", "minute"})
public class Alarm {
    @NonNull
    private int hour;
    @NonNull
    private int minute;
    private boolean onOffState;

    public Alarm(int hour, int minute) {
        this.hour = hour;
        this.minute = minute;
        onOffState = false;
    }

    public int getHour() {
        return hour;
    }

    public void setHour(int hour) {
        this.hour = hour;
    }

    public int getMinute() {
        return minute;
    }

    public void setMinute(int minute) {
        this.minute = minute;
    }

    public boolean isOnOffState() {
        return onOffState;
    }

    public void setOnOffState(boolean onOffState) {
        this.onOffState = onOffState;
    }
}
