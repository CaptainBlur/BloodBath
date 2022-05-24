package com.vova9110.bloodbath.Database;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import androidx.lifecycle.LiveData;

import java.io.Serializable;
import java.util.List;

import javax.inject.Inject;

public class AlarmRepo implements Serializable {

    private final String TAG = "TAG_AR";
    private static AlarmDao alarmDao;

    @Inject
    public AlarmRepo (AlarmDao dao){
        alarmDao = dao;
    }

    public void insert (Alarm alarm){
        AlarmDatabase.databaseWriteExecutor.execute(() -> alarmDao.insert(alarm));
    }
    public void update (Alarm alarm){
        AlarmDatabase.databaseWriteExecutor.execute(() -> alarmDao.update(alarm));
    }
    public void deleteAll() {
        AlarmDatabase.databaseWriteExecutor.execute(alarmDao::deleteAll);
    }
    public void deleteOne(int hour, int minute){
        AlarmDatabase.databaseWriteExecutor.execute(() -> alarmDao.deleteOne(hour, minute));
    }
    public LiveData<List<Alarm>> getLD(){
        return alarmDao.getLD();
    }
}
