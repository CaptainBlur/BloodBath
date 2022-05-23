package com.vova9110.bloodbath;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.vova9110.bloodbath.Database.Alarm;
import com.vova9110.bloodbath.Database.AlarmDao;
import com.vova9110.bloodbath.Database.AlarmDatabase;
import com.vova9110.bloodbath.RecyclerView.AlarmListAdapter;

import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

public class AlarmRepo implements RepoCallback { // Репозиторий предоставляет абстрактный доступ к базе данных, то есть представлен в роли API (так они советуют делать)
    private final String TAG = "TAG_AR";
    private AlarmDao alarmDao; // Создаём поле, которое будет представлять переменную интерфейса Дао
    private RecyclerView recycler;
    private AlarmListAdapter adapter;
    private MainActivity.LDObserver observer;
    private Context context;

    private LiveData<List<Alarm>> roomLD;
    private List<Alarm> bufferList = new LinkedList<>();
    private List<Alarm> oldList;

    private final Alarm addAlarm = new Alarm(66,66, 0);
    private Alarm prefAlarm;
    private RLMCallback rlmCallback;
    private int prefPos;

    private Calendar currentCalendar;
    private AlarmManager AManager;
    private Intent testScreenActivityIntent;
    private PendingIntent testPendingIntent;

    AlarmRepo(AlarmDao Dao){//Можно и здесь добавить аннотацию Inject, чтобы Даггер обращался к этому конструктору для создания и сам передавал в него Дао
        addAlarm.setAddFlag(true);
        alarmDao = Dao;
        roomLD = alarmDao.getLD();//При создани репозитория мы передаём этот список в MA,


        Log.d(TAG, "Repo instance created");
    }

    public void pass(RecyclerView recyclerView, AlarmListAdapter adapter, MainActivity.LDObserver observer, Context applicationContext ) {
        recycler = recyclerView;
        this.adapter = adapter;
        this.observer = observer;
        context = applicationContext;
    }
    public LiveData<List<Alarm>> getInitialList() { return roomLD; }
    private void prepare(){
        roomLD.removeObserver(observer);
        if (!bufferList.isEmpty()) bufferList.clear();
        oldList = adapter.getCurrentList();

        currentCalendar = Calendar.getInstance();
        currentCalendar.setTimeInMillis(System.currentTimeMillis());
        currentCalendar.set(Calendar.SECOND, 0);
        currentCalendar.set(Calendar.MILLISECOND, 0);
    }


    public RepoCallback pullRepoCallback(){
        return this;
    }
    public void passRLMCallback (RLMCallback callback){
        this.rlmCallback = callback;
    }
    public RLMCallback pullRLMCallback(){
        return rlmCallback;
    }


    void fill (){
        prepare();
        AlarmDatabase.databaseWriteExecutor.execute(() -> alarmDao.deleteAll());

        for (int i = 0; i<28; i++){
            Alarm alarm = new Alarm(0, i,0);
            bufferList.add(alarm);
            AlarmDatabase.databaseWriteExecutor.execute(() -> alarmDao.insert(alarm));
        }
        bufferList.add(addAlarm);
        AlarmDatabase.databaseWriteExecutor.execute(() -> alarmDao.insert(addAlarm));
        submitList(oldList, bufferList);
    }

    void clear () {
        prepare();
        AlarmDatabase.databaseWriteExecutor.execute(() -> alarmDao.deleteAll());
        bufferList.add(addAlarm);
        AlarmDatabase.databaseWriteExecutor.execute(() -> alarmDao.insert(addAlarm));
        submitList(oldList, bufferList);
    }

    @Override
    public void deleteItem(int pos) {
        int currentPos;
        boolean prefRemoved = false;
        prepare();
        bufferList.addAll(oldList);
        Alarm current = bufferList.get(pos);

        prefPos = bufferList.indexOf(prefAlarm);
        if (bufferList.remove(prefAlarm)) prefRemoved = true;

        currentPos = bufferList.indexOf(current);
        bufferList.remove(current);
        AlarmDatabase.databaseWriteExecutor.execute(() -> alarmDao.deleteOne(current.getHour(), current.getMinute()));

        adapter.submitList(bufferList);
        if (prefRemoved) recycler.post(()-> adapter.notifyItemRemoved(prefPos));
        recycler.post(()-> adapter.notifyItemRemoved(currentPos));
    }

    @Override
    public void addItem(int hour, int minute) {
        int currentPos;
        boolean prefRemoved = false;
        prepare();
        bufferList.addAll(oldList);

        currentCalendar.set(Calendar.HOUR_OF_DAY, hour);
        currentCalendar.set(Calendar.MINUTE, minute);

        int i = 0; Alarm req;
        while (i < bufferList.size()){
            req = bufferList.get(i);
            if (req.getHour() == hour & req.getMinute() == minute){
                throw new UnsupportedOperationException("Alarm already exist, can't add");
            }
            else i++;
        }

        Alarm current = new Alarm(hour, minute, currentCalendar.getTimeInMillis());
        bufferList.add(current);
        AlarmDatabase.databaseWriteExecutor.execute(() -> alarmDao.insert(current));
        bufferList.sort((o1, o2) -> {
            if (o1.getHour() != o2.getHour()) return o1.getHour() - o2.getHour();
            else return o1.getMinute() - o2.getMinute();
        });
        currentPos = bufferList.indexOf(current);

        prefPos = bufferList.indexOf(prefAlarm);
        if (bufferList.remove(prefAlarm)) prefRemoved = true;

        adapter.submitList(bufferList);
        if (prefRemoved) recycler.post(()-> adapter.notifyItemRemoved(prefPos));
        recycler.post(()-> adapter.notifyItemInserted(currentPos));
    }

    @Override
    public void changeItem(int oldPos, int hour, int minute) {
        int currentPos;
        boolean prefRemoved = false;
        prepare();
        bufferList.addAll(oldList);

        int i = 0; Alarm req;
        while (i < bufferList.size()){
            req = bufferList.get(i);
            if (req.getHour() == hour & req.getMinute() == minute){
                throw new UnsupportedOperationException("Alarm already exist, can't add");
            }
            else i++;
        }

        prefPos = bufferList.indexOf(prefAlarm);
        if (bufferList.remove(prefAlarm)) prefRemoved = true;

        Alarm current = bufferList.get(oldPos);
        AlarmDatabase.databaseWriteExecutor.execute(() -> alarmDao.deleteOne(current.getHour(), current.getMinute()));

        currentCalendar.set(Calendar.HOUR_OF_DAY, hour);
        currentCalendar.set(Calendar.MINUTE, minute);
        current.setHour(hour);
        current.setMinute(minute);
        current.setTime(currentCalendar.getTimeInMillis());

        AlarmDatabase.databaseWriteExecutor.execute(() -> alarmDao.insert(current));
        bufferList.sort((o1, o2) -> {
            if (o1.getHour() != o2.getHour()) return o1.getHour() - o2.getHour();
            else return o1.getMinute() - o2.getMinute();
        });
        currentPos = bufferList.indexOf(current);

        adapter.submitList(bufferList);
        if (prefRemoved) recycler.post(()-> adapter.notifyItemRemoved(prefPos));
        recycler.post(()-> adapter.notifyItemRemoved(oldPos));
        recycler.post(()-> adapter.notifyItemInserted(currentPos));
    }

    public void updateItem(int parentPos, boolean switcherState) {//каким-то хером левый код вызывает метод
        Log.d (TAG, "Updating item " + parentPos + ", state: " + switcherState);
        prepare();
        bufferList.addAll(oldList);
        Alarm current = bufferList.get(parentPos);
        current.setOnOffState(switcherState);
        bufferList.set(parentPos, current);
        AlarmDatabase.databaseWriteExecutor.execute(() -> alarmDao.updateState(current.getHour(), current.getMinute(), switcherState));
        adapter.submitList(bufferList);

        Intent execIntent = new Intent(context, AlarmExec.class);//todo разобраться, чего там можно напихать в интент
        context.startService(execIntent);

//        currentCalendar.set(Calendar.HOUR_OF_DAY, current.getHour());
//        currentCalendar.set(Calendar.MINUTE, current.getMinute());
//        AManager = (android.app.AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
//        testScreenActivityIntent = new Intent(context, NewTaskActivity.class);
//        testPendingIntent = PendingIntent.getActivity(context, 0, testScreenActivityIntent, PendingIntent.FLAG_IMMUTABLE);
//        AlarmManager.AlarmClockInfo info = new AlarmManager.AlarmClockInfo(currentCalendar.getTimeInMillis(), testPendingIntent);

    }

    private void submitList(List<Alarm> oldList, List<Alarm> newList) {
        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new ListDiff(oldList, newList));
        adapter.submitList(newList);
        result.dispatchUpdatesTo(adapter);
    }

    @Override
    public void passPrefToAdapter(int parentPos, int prefPos) {
        //Log.d (TAG, "" + parentPos + prefPos);
        prepare();
        bufferList.addAll(oldList);
        Alarm parent = bufferList.get(parentPos);

        int addAlarmPos; int i = 0; boolean flag;
        do{
            addAlarmPos = i;
            flag = bufferList.get(i).isAddFlag();
            i++;
        }
        while (!flag);

        Alarm pref = new Alarm(parent.getHour(), parent.getMinute(), 0);//Здесь мы берём информацию из материнского элемента, согласно его переданной позиции
        pref.setPrefFlag();
        pref.setParentPos(parentPos);
        pref.setOnOffState(parent.isOnOffState());
        if (parentPos == addAlarmPos) pref.setPrefBelongsToAdd();
        prefAlarm = pref;

        bufferList.add(prefPos, pref);
        submitList(oldList, bufferList);
    }

    @Override
    public void removePref() {
        prepare();
        bufferList.addAll(oldList);
        bufferList.remove(prefAlarm);
        submitList(oldList, bufferList);
    }

    @Override
    public void removeNPassPrefToAdapter(int parentPos, int prefPos) {
        this.prefPos = prefPos;
        Log.d (TAG, "" + parentPos + prefPos);
        prepare();
        bufferList.addAll(oldList);
        bufferList.remove(prefAlarm);
        Alarm parent = bufferList.get(parentPos);


        int addAlarmPos; int i = 0; boolean flag;
        do{
            addAlarmPos = i;
            flag = bufferList.get(i).isAddFlag();
            i++;
        }
        while (!flag);

        Alarm pref = new Alarm(parent.getHour(), parent.getMinute(), 0);//Здесь мы берём информацию из материнского элемента, согласно его переданной позиции
        pref.setPrefFlag();
        pref.setParentPos(parentPos);
        pref.setOnOffState(parent.isOnOffState());
        if (parentPos == addAlarmPos) pref.setPrefBelongsToAdd();
        prefAlarm = pref;

        bufferList.add(prefPos, pref);
        submitList(oldList, bufferList);
        //Log.d (TAG, bufferList.get(prefPos).isPrefFlag() + "");
    }


    private class ListDiff extends DiffUtil.Callback {//Эту лабуду оставим для оповещения о настройках
        private List<Alarm> oldList;
        private List<Alarm> newList;

        public ListDiff(List<Alarm> oldList, List<Alarm> newList) {
            this.oldList = oldList;
            this.newList = newList;
        }

        @Override
        public int getOldListSize() {
            return oldList.size();
        }

        @Override
        public int getNewListSize() {
            return newList.size();
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            Alarm oldAlarm = oldList.get(oldItemPosition);
            Alarm newAlarm = newList.get(newItemPosition);

            return newAlarm.isPrefFlag()==oldAlarm.isPrefFlag() &&
                    newAlarm.isAddFlag()==oldAlarm.isAddFlag();
            //У нас либо окно времени, либо настройки
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {//Сравниваем на предмет изменения данных в будильнике
            Alarm oldAlarm = oldList.get(oldItemPosition);
            Alarm newAlarm = newList.get(newItemPosition);
            return newAlarm.getHour()==oldAlarm.getHour() &&
                    newAlarm.getMinute()==oldAlarm.getMinute() &&
                    newAlarm.isOnOffState()==oldAlarm.isOnOffState();
        }
    }
}

