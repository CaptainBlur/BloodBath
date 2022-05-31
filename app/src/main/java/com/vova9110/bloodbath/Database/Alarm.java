package com.vova9110.bloodbath.Database;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Ignore;

import java.util.Date;

@Entity (tableName = "alarms_table", primaryKeys = {"hour", "minute"})
public class Alarm{

    private int hour;
    private int minute;
    private Date triggerTime;

    private boolean onOffState = false;
    private boolean wasActive = false;
    private boolean wasPassive = false;

    @Ignore
    private boolean prefFlag = false;
    @Ignore
    private int parentPos;
    @Ignore
    private boolean prefBelongsToAdd = false;
    private boolean addFlag = false;

    public Alarm(int hour, int minute, Date triggerTime) {
        this.hour = hour;
        this.minute = minute;
        this.triggerTime = triggerTime;
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

    public Date getTriggerTime() {
        return triggerTime;
    }

    public void setTriggerTime(Date triggerTime) {
        this.triggerTime = triggerTime;
    }

    public boolean isOnOffState() {
        return onOffState;
    }

    public void setOnOffState(boolean onOffState) { this.onOffState = onOffState; }

    public boolean isWasActive() {
        return wasActive;
    }

    public void setWasActive(boolean wasActive) {
        this.wasActive = wasActive;
    }

    public boolean isWasPassive() {
        return wasPassive;
    }

    public void setWasPassive(boolean wasPassive) {
        this.wasPassive = wasPassive;
    }

    public void setPrevStates(boolean wasPassive, boolean wasActive){
        this.wasPassive = wasPassive;
        this.wasActive = wasActive;
    }

    public void setAddFlag(boolean addFlag) { this.addFlag = addFlag; }

    public boolean isAddFlag() { return addFlag; }

    public boolean isPrefFlag() { return prefFlag; }

    public void setPrefFlag() { this.prefFlag = true; }

    public int getParentPos() { return parentPos; }

    public void setParentPos(int parentPos) { this.parentPos = parentPos; }

    public boolean isPrefBelongsToAdd() { return prefBelongsToAdd; }

    public void setPrefBelongsToAdd() { this.prefBelongsToAdd = true; }

}
