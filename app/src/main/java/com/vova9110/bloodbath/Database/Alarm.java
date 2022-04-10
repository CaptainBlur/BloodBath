package com.vova9110.bloodbath.Database;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

@Entity (tableName = "alarms_table", primaryKeys = {"hour", "minute"})
public class Alarm {
    @NonNull
    private int hour;
    @NonNull
    private int minute;
    private boolean onOffState = false;
    private boolean prefFlag = false;
    private boolean prefVisible = false;
    private int prefItemContainer;
    private boolean addFlag = false;

    public Alarm(int hour, int minute) {
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

    public void setOnOffState(boolean onOffState) {
        this.onOffState = onOffState;
    }

    public void setAddFlag(boolean addFlag) { this.addFlag = addFlag; }

    public boolean isAddFlag() { return addFlag; }

    public boolean isPrefFlag() { return prefFlag; }

    public void setPrefFlag(boolean prefFlag) { this.prefFlag = prefFlag; }

    public int getPrefItemContainer() { return prefItemContainer; }

    public void setPrefItemContainer(int prefItemContainer) { this.prefItemContainer = prefItemContainer; }

    public boolean isPrefVisible() { return prefVisible; }

    public void setPrefVisible(boolean prefVisible) { this.prefVisible = prefVisible; }
}
