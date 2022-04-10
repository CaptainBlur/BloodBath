package com.vova9110.bloodbath;

import android.app.Application;
import android.content.Context;
import android.util.Log;
import android.view.View;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.recyclerview.widget.DiffUtil;

import com.vova9110.bloodbath.Database.AlarmDatabase;
import com.vova9110.bloodbath.Database.Alarm;
import com.vova9110.bloodbath.Database.AlarmDao;
import com.vova9110.bloodbath.RecyclerView.AlarmListAdapter;

import java.util.LinkedList;
import java.util.List;

import javax.inject.Inject;

public class AlarmRepo { // Репозиторий предоставляет абстрактный доступ к базе данных, то есть представлен в роли API (так они советуют делать)
    private final String TAG = "TAG_AR";
    private AlarmDao alarmDao; // Создаём поле, которое будет представлять переменную интерфейса Дао
    private AlarmListAdapter adapter;
    private MainActivity.LDObserver observer;
    private LiveData<List<Alarm>> initialList;
    private List<Alarm> bufferList = new LinkedList<>();
    private List<Alarm> oldList;
    private final Alarm addAlarm = new Alarm(66,6);
    private Alarm prefAlarm;

    AlarmRepo(AlarmDao Dao){//Можно и здесь добавить аннотацию Inject, чтобы Даггер обращался к этому конструктору для создания и сам передавал в него Дао
        addAlarm.setAddFlag(true);
        alarmDao = Dao;
        initialList = alarmDao.getAllAlarms();//При создани репозитория мы передаём этот список в MA,
        Log.d(TAG, "Repo instance created");
    }

    public void passAdapterNObserver(AlarmListAdapter adapter, MainActivity.LDObserver observer) { this.adapter = adapter; this.observer = observer; }
    public LiveData<List<Alarm>> getInitialList() { return initialList; }
    public Alarm getPrefAlarm () {return prefAlarm;}

    private void prepare(){
        if(initialList.hasObservers()) initialList.removeObserver(observer);
        if (!bufferList.isEmpty()) bufferList.clear();
        oldList = adapter.getCurrentList();
    }

    public void passPref(int pos){
        prepare();
        bufferList.addAll(oldList);
        Alarm pref = new Alarm(bufferList.get(pos).getHour(), bufferList.get(pos).getMinute());
        pref.setPrefFlag(true); pref.setPrefItemContainer(pos); pref.setPrefVisible(true);
        prefAlarm = pref;
        bufferList.add(pref);
        submitList(oldList, bufferList);
    }

    void fill (){
        prepare();
        for (int i = 0; i<28; i++){
            Alarm alarm = new Alarm(0, i);
            bufferList.add(alarm);
            AlarmDatabase.databaseWriteExecutor.execute(() -> alarmDao.insert(alarm));
        }
        bufferList.add(addAlarm);
        AlarmDatabase.databaseWriteExecutor.execute(() -> alarmDao.insert(addAlarm));
        submitList(oldList, bufferList);
    }

    void insert (Alarm alarm){//TODO добавлять пустой будильник, в конец списка, да так, чтобы отражал только плюсик
        prepare();
        bufferList.addAll(oldList);
        bufferList.add(alarm);
        bufferList.sort((o1, o2) -> {
            int c = 1;
            if (o1.getHour() < o2.getHour()) c = -1;
            else if (o1.getHour() == o2.getHour() && o1.getMinute() < o2.getMinute()) c = -1;
            else if (o1.getHour() == o2.getHour() && o1.getMinute() == o2.getMinute()) c = 0;
            return c;
        });
        AlarmDatabase.databaseWriteExecutor.execute(() -> alarmDao.insert(alarm));
        submitList(oldList, bufferList);
    }

    public void delete(int pos) {
        prepare();
        bufferList.addAll(oldList);
        Alarm current = bufferList.get(pos);
        bufferList.remove(pos);
        AlarmDatabase.databaseWriteExecutor.execute(() -> alarmDao.deleteOne(current.getHour(), current.getMinute()));
        submitList(oldList, bufferList);
    }

    void clear () {
        prepare();
        bufferList.add(addAlarm);
        submitList(oldList, bufferList);
        AlarmDatabase.databaseWriteExecutor.execute(() -> alarmDao.deleteAll());
    }


    private void submitList(List<Alarm> oldList, List<Alarm> newList) {
        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new ListDiff(oldList, newList));
        adapter.submitList(newList);
        result.dispatchUpdatesTo(adapter);
    }
    private class ListDiff extends DiffUtil.Callback {
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
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {//Сравниваем на предмет перемещения будильников по списку
            Alarm oldAlarm = oldList.get(oldItemPosition);
            Alarm newAlarm = newList.get(newItemPosition);
            return newAlarm.getHour()==oldAlarm.getHour() && newAlarm.getMinute()==oldAlarm.getMinute();//Час и минута - это уникальный идентификатор
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {//Сравниваем на предмет изменения данных в будильнике
            Alarm oldAlarm = oldList.get(oldItemPosition);
            Alarm newAlarm = newList.get(newItemPosition);
            return newAlarm.isOnOffState()==oldAlarm.isOnOffState();
        }
    }
}

