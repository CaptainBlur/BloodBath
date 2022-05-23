package com.vova9110.bloodbath.Database;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Ignore;

import java.util.Date;

@Entity (tableName = "alarms_table", primaryKeys = {"hour", "minute"})
public class Alarm{
    @NonNull
    private int hour;
    @NonNull
    private int minute;

    private long time;
    private boolean onOffState = false;
    @Ignore
    private boolean prefFlag = false;
    @Ignore
    private int parentPos;
    @Ignore
    private boolean prefBelongsToAdd = false;
    private boolean addFlag = false;

    public Alarm(int hour, int minute, long time) {
        this.hour = hour;
        this.minute = minute;
        this.time = time;
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

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public boolean isOnOffState() {
        return onOffState;
    }

    public void setOnOffState(boolean onOffState) { this.onOffState = onOffState; }

    public void setAddFlag(boolean addFlag) { this.addFlag = addFlag; }

    public boolean isAddFlag() { return addFlag; }

    public boolean isPrefFlag() { return prefFlag; }

    public void setPrefFlag() { this.prefFlag = true; }

    public int getParentPos() { return parentPos; }

    public void setParentPos(int parentPos) { this.parentPos = parentPos; }
    public boolean isPrefBelongsToAdd() { return prefBelongsToAdd; }

    public void setPrefBelongsToAdd() { this.prefBelongsToAdd = true; }

}
