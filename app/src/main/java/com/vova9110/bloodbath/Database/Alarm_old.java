package com.vova9110.bloodbath.Database;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;

@Entity (tableName = "alarms_table", primaryKeys = {"hour", "minute"})
public class Alarm_old {
    @NonNull
    private int hour;
    @NonNull
    private int minute;
    private boolean onOffState = false;
    @Ignore
    private boolean prefFlag = false;
    @Ignore
    private int parentPos;
    @Ignore
    private boolean prefBelongsToAdd = false;
    private boolean addFlag = false;

    public Alarm_old(int hour, int minute) {
        this.hour = hour;
        this.minute = minute;
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
