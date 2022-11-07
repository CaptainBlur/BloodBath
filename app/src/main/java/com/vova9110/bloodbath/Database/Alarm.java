package com.vova9110.bloodbath.Database;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Ignore;

import java.util.Date;

@Entity (tableName = "alarms_table", primaryKeys = {"hour", "minute"})
public class Alarm {

    private int hour;
    private int minute;
    private Date triggerTime;
    private Date extraTime;

    private boolean onOffState = false;

    private boolean delayed = false;
    private boolean preliminaryFired = false;
    private boolean preliminaryPermit = false;

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

    public void setAddFlag(boolean addFlag) { this.addFlag = addFlag; }

    public boolean isAddFlag() { return addFlag; }

    public boolean isPrefFlag() { return prefFlag; }

    public void setPrefFlag() { this.prefFlag = true; }

    public int getParentPos() { return parentPos; }

    public void setParentPos(int parentPos) { this.parentPos = parentPos; }

    public boolean isPrefBelongsToAdd() { return prefBelongsToAdd; }

    public void setPrefBelongsToAdd() { this.prefBelongsToAdd = true; }

    public boolean isDelayed() {return delayed;}

    public void setDelayed(boolean delayed) {this.delayed = delayed;}

    public boolean isPreliminaryFired() {return preliminaryFired;}

    public void setPreliminaryFired(boolean preliminaryFired) {this.preliminaryFired = preliminaryFired;}

    public boolean isPreliminaryPermit() {return preliminaryPermit;}

    public void setPreliminaryPermit(boolean preliminaryPermit) {this.preliminaryPermit = preliminaryPermit;}

    public Date getExtraTime() {return extraTime;}

    public void setExtraTime(Date extraTime) {this.extraTime = extraTime;}
}
