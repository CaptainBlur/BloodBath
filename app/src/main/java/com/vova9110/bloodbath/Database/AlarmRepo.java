package com.vova9110.bloodbath.Database;

import android.util.Log;

import androidx.lifecycle.LiveData;

import java.io.Serializable;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.inject.Inject;

public class AlarmRepo implements Serializable {

    private final String TAG = "TAG_AR";
    private static AlarmDao alarmDao;
    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Inject
    public AlarmRepo (AlarmDao dao){
        alarmDao = dao;
    }

    public void insert (Alarm alarm){
        executor.execute(() -> alarmDao.insert(alarm));
    }
    public void update (Alarm alarm){
        executor.execute(() -> alarmDao.update(alarm));
    }
    public void deleteAll() {
        executor.execute(alarmDao::deleteAll);
    }
    public void deleteOne(int hour, int minute){
        executor.execute(() -> alarmDao.deleteOne(hour, minute));
    }
    public LiveData<List<Alarm>> getLD(){
        return alarmDao.getLD();
    }

    public Alarm findPrevPassive(){
        Callable<Alarm> callable = () -> alarmDao.getWasPassive();
        Future<Alarm> future = executor.submit(callable);
        Alarm result = null;
        try{
            result = future.get();
        } catch (CancellationException | ExecutionException | InterruptedException e){
            e.printStackTrace();
            Log.d (TAG, "EXECUTION FAILED!");
        }
        if (result != null) Log.d (TAG, "wasPassive: " + result.getHour() + result.getMinute());
        return result;
    }

    public Alarm findPrevActive(){
        Callable<Alarm> callable = () -> alarmDao.getWasActive();
        Future<Alarm> future = executor.submit(callable);
        Alarm result = null;
        try{
            result = future.get();
        } catch (CancellationException | ExecutionException | InterruptedException e){
            e.printStackTrace();
            Log.d (TAG, "EXECUTION FAILED!");
        }
        if (result != null) Log.d (TAG, "wasActive: " + result.getHour() + result.getMinute());
        return result;
    }

    public List<Alarm> getActives(){
        Callable<List<Alarm>> callable = () -> alarmDao.getActives();
        Future<List<Alarm>> future = executor.submit(callable);
        List<Alarm> result = null;
        try{
            result = future.get();
        } catch (CancellationException | ExecutionException | InterruptedException e){
            e.printStackTrace();
            Log.d (TAG, "EXECUTION FAILED!");
        }
        if (result != null) Log.d (TAG, "active alarms count: " + result.size());
        return result;
    }
}
