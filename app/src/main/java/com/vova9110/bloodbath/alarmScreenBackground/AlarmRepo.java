package com.vova9110.bloodbath.alarmScreenBackground;

import android.content.Context;

import com.vova9110.bloodbath.SplitLogger;
import com.vova9110.bloodbath.database.Alarm;
import com.vova9110.bloodbath.database.AlarmDao;

import java.util.Comparator;
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
    private final Context c;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Inject 
    public AlarmRepo (AlarmDao dao, Context context){ alarmDao = dao; c = context; }

    //This is a group of access methods, they don't perform control actions, but just provide access to the database for the Handlers and for other's needs
    //todo make protected
    public SubInfo getTimesInfo(String id){
        Alarm instance = getOne(id);

        return new SubInfo(
                instance.getTriggerTime(),
                instance.getId(),
                instance.getSnoozed(),
                null,
                instance.getVibrate(),
                true,
                -1,
                15,
                2.2f,
                0,
                5,
                40,
                90,
                8,
                10);
    }
    protected Alarm getOne(String id){
        int[] a = Alarm.getHM(id);
        Callable<Alarm> callable = ()-> alarmDao.getOne(a[0], a[1]);
        Future<Alarm> future = executor.submit(callable);
        Alarm result = null;
        try{
            result = future.get();
        } catch (CancellationException | ExecutionException | InterruptedException e){
            e.printStackTrace();
            SplitLogger.sp( "EXECUTION FAILED!");
        }
        assert result!=null;
        return result;
    }

    public List<Alarm> getAll() {
        Callable<List<Alarm>> callable = alarmDao::getAll;
        Future<List<Alarm>> future = executor.submit(callable);
        List<Alarm> result = null;

        try{
            result = future.get();
        } catch (CancellationException | ExecutionException | InterruptedException e){
            e.printStackTrace();
            SplitLogger.sp( "EXECUTION FAILED!");
        }

        if (result==null) return new LinkedList<Alarm>();
        return result;
    }
    protected List<Alarm> getActives(){
        Callable<List<Alarm>> callable = alarmDao::getActives;
        Future<List<Alarm>> future = executor.submit(callable);
        List<Alarm> result = null;
        try{
            result = future.get();
        } catch (CancellationException | ExecutionException | InterruptedException e){
            e.printStackTrace();
            SplitLogger.sp( "EXECUTION FAILED!");
        }
        if (result != null) SplitLogger.fst( "actives count: " + result.size());
        return result;
    }

    //These methods require backing scheduler class to handle alarms with just changed states.
    //Also they helps other classes to change properties of instances

    private void setGlobalID(){
        List<Alarm> repoActives = this.getActives();
        if (repoActives.isEmpty()){
            SplitLogger.frpc("no actives in repo to calculate new globalID");
            BackgroundUtils.setGlobalID(c, "null");
            return;
        }
        LinkedList<Alarm> actives = new LinkedList<>(repoActives);
        actives.sort(Comparator.comparing(Alarm::getTriggerTime));

        BackgroundUtils.setGlobalID(c, actives.get(0).getId());
    }

    /**
     * Use this method to insert newly created instance. If instance with the same ID already exists in the DB,
     * the passed one will be discarded. Also, it starts initial States calculation for new Alarm
     * and recalculation of globalID depending on on/off state of passed alarm -
     * *for Handler use
     *
     * @param alarm new instance
     */
    public void insert (Alarm alarm){
        if (!alarm.component3()) {
            SplitLogger.fpc("passed alarm hadn't been enabled");
            executor.execute(() -> alarmDao.insert(alarm));
            return;
        }

        alarm.setEnabled(true);
        alarm.calculateTriggerTime();
        executor.execute(() -> alarmDao.insert(alarm));
        setGlobalID();
        AlarmExecutionDispatch.defineNewState(c, alarm, this);

    }
    public void testInsert (Alarm alarm){
        executor.execute(() -> alarmDao.insert(alarm));
        setGlobalID();
    }

    /**
     * Use this method to update fields of an existing instance in DB (all except of hours and minutes),
     * globalID and optionally start States recalculation
     * of sole Alarm depending on power state of passed alarm -
     * *for Handler, FCR and AED use
     *
     * @param alarm updated instance
     */
    public void update (Alarm alarm, boolean recalculateStates){
        if (recalculateStates){
            if (alarm.getEnabled()) alarm.calculateTriggerTime();
            else alarm.setTriggerTime(null);
            AlarmExecutionDispatch.defineNewState(c, alarm, this);//Kinda recursive thing
            setGlobalID();
        }
        else executor.execute(() -> alarmDao.update(alarm));
    }

    /**
     * Use this method to discard all existing instances (except of the *addAlarm*) in DB and cancel all appointments
     */
    public void deleteAll() {
        AlarmExecutionDispatch.wipeAll(c, getAll());
        executor.execute(alarmDao::deleteAll);
    }

    /**
     * Use this method to delete existing instance in DB and recalculate States for the rest *actives*
     * @param alarm instance to discard
     */
    public void deleteOne(Alarm alarm){
        executor.execute(() -> alarmDao.deleteOne(alarm.getHour(), alarm.getMinute()));
        AlarmExecutionDispatch.wipeOne(c, alarm);
        if (alarm.getEnabled()) setGlobalID();
    }
}
