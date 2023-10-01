package com.foxstoncold.youralarm.alarmScreenBackground;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.foxstoncold.youralarm.SplitLogger;
import com.foxstoncold.youralarm.database.Alarm;
import com.foxstoncold.youralarm.database.AlarmDao;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import javax.inject.Inject;

public class AlarmRepo {
    private final AlarmDao alarmDao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static SplitLogger sl;

    @Inject 
    public AlarmRepo (AlarmDao dao){
        alarmDao = dao;
    }

    //This is a group of access methods, they don't perform control actions, but just provide access to the database for the Handlers and for other's needs

    /**
     *
     * @param id
     * @return nullable result if entity not found
     */
    public Alarm getOne(String id){
        int[] a = Alarm.getHM(id);
        Callable<Alarm> callable = ()-> alarmDao.getOne(a[0], a[1]);
        Future<Alarm> future = executor.submit(callable);
        Alarm result = null;
        try{
            result = future.get();
        } catch (CancellationException | ExecutionException | InterruptedException e){
            sl.sp("EXECUTION FAILED", e);
        }
        return result;
    }

    public List<Alarm> getAll() {
        Callable<List<Alarm>> callable = alarmDao::getAll;
        Future<List<Alarm>> future = executor.submit(callable);
        List<Alarm> result = null;

        try{
            result = future.get();
        } catch (CancellationException | ExecutionException | InterruptedException e){
            sl.sp("EXECUTION FAILED", e);
        }

        if (result==null) return new LinkedList<Alarm>();
        return result;
    }
    public LiveData<List<Alarm>> getAllLD() {
        Callable<LiveData<List<Alarm>>> callable = alarmDao::getAllLD;
        Future<LiveData<List<Alarm>>> future = executor.submit(callable);
        LiveData<List<Alarm>> result = null;

        try{
            result = future.get();
        } catch (CancellationException | ExecutionException | InterruptedException e){
            sl.sp("EXECUTION FAILED", e);
        }

        return result;
    }
    public List<Alarm> getActives(){
        Callable<List<Alarm>> callable = alarmDao::getActives;
        Future<List<Alarm>> future = executor.submit(callable);
        List<Alarm> result = null;
        try{
            result = future.get();
        } catch (CancellationException | ExecutionException | InterruptedException e){
            sl.sp("EXECUTION FAILED", e);
        }
        if (result != null) sl.fst( "actives count: " + result.size());
        return result;
    }
    public LiveData<List<Alarm>> getActivesLD() {
        Callable<LiveData<List<Alarm>>> callable = alarmDao::getActivesLD;
        Future<LiveData<List<Alarm>>> future = executor.submit(callable);
        LiveData<List<Alarm>> result = null;

        try{
            result = future.get();
        } catch (CancellationException | ExecutionException | InterruptedException e){
            sl.sp("EXECUTION FAILED", e);
        }

        return result;
    }

    //These methods require backing scheduler class to handle alarms with just changed states.
    //Also they helps other classes to change properties of instances

    /**
     * Use this method to insert newly created instance. If instance with the same ID already exists in the DB,
     * the passed one will be discarded. Also, it starts initial States calculation for new Alarm
     * and recalculation of globalID depending on on/off state of passed alarm -
     * *for Handler use
     *
     * @param alarm new instance
     */
    public void insert (Alarm alarm, Context c){
        sl.i("inserting alarm: " + alarm.getId());
        if (!alarm.component3()) {
            sl.fpc("passed alarm hadn't been enabled");
            executor.execute(() -> alarmDao.insert(alarm));
        }
        else {
            alarm.setPreliminaryTime(c);
            alarm.calculateTriggerTime(false);
            executor.execute(() -> alarmDao.insert(alarm));
            AlarmExecutionDispatch.defineNewState(c, alarm, this);
        }
    }

    /**
     * Use this method to update fields of an existing instance in DB (all except of hours and minutes),
     * globalID and optionally start States recalculation
     * of sole Alarm depending on power state of passed alarm -
     * *for Handler, FCR and AED use
     *
     * @param alarm updated instance
     */
    public void update (Alarm alarm, boolean recalculateStates, Context c){
        if (recalculateStates){
            sl.i("updating alarm: " + alarm.getId());
            if (alarm.getEnabled()){
                alarm.setPreliminaryTime(c);
                alarm.calculateTriggerTime(false);
            }
            else alarm.setTriggerTime(null);

            //AED not always erasing PIs and notifications, so we just make sure it's nothing left of an old one
            BackgroundUtils.cancelNotification(c, alarm.getId(), Alarm.STATE_ALL);
            BackgroundUtils.cancelPI(c, alarm.getId(), Alarm.STATE_ALL);
            //Kinda recursive thing, because AED will eventually call this method, but without recalculating
            AlarmExecutionDispatch.defineNewState(c, alarm, this);
        }
        else executor.execute(() ->{
            sl.fst("updating alarm: " + alarm.getId());
            alarmDao.update(alarm);
        });
    }

    /**
     * Use this method to discard all existing instances (except of the *addAlarm*) in DB and cancel all appointments
     */
    public void deleteAll(Context c) {
        sl.i("deleting all");
        AlarmExecutionDispatch.wipeAll(c, getAll());
        executor.execute(alarmDao::deleteAll);
    }

    /**
     * Use this method to delete existing instance in DB and recalculate States for the rest *actives*
     * @param alarm instance to discard
     */
    public void deleteOne(Alarm alarm, Context c){
        sl.i("deleting alarm: " + alarm.getId());
        executor.execute(() -> alarmDao.deleteOne(alarm.getHour(), alarm.getMinute()));
        AlarmExecutionDispatch.wipeOne(c, alarm);
    }

    public void reassureAll(Context c){
        AlarmExecutionDispatch.checkAll(c, this, getAll());
    }

    public void updateActives(Context c){
        List<Alarm> actives = getActives();
        if (actives==null) return;

        for (Alarm active : actives) update(active, true, c);
    }
}
