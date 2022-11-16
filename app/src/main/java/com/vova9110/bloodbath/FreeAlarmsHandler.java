package com.vova9110.bloodbath;

import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.Toast;

import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.vova9110.bloodbath.AlarmScreenBackground.AlarmExec;
import com.vova9110.bloodbath.Database.Alarm;
import com.vova9110.bloodbath.Database.AlarmRepo;
import com.vova9110.bloodbath.RecyclerView.AlarmListAdapter;
import com.vova9110.bloodbath.RecyclerView.HandlerCallback;
import com.vova9110.bloodbath.RecyclerView.RLMCallback;

import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

/**
 * Боже мой, как же я устал бороться со всем этим блоком свободных будильников.
 * При попытке перенести всю логику на Котлин, упростив механизмы доступа, и сократив количество кода (конкретно в этом классе раза в три),
 * ресайклер просто начинает сходить с ума, безо всякой на то причины. Я даже не могу перенести сраный Обсёрвер из Мейн Активити сюда, поскольку начинается какая-то херня:
 * в реалтайме, при удалении хотя бы одного элемента из списка, ресайклер либо заново байндит все вьюшки, либо вываливает в РЛМ лист с вьюшками без позиции.
 * Что самое интересное, механизм в данном случае практически идентичный тому, что используется при удлении префа из списка, а он работает как часы.
 * На данный момент у меня нет столько времени, чтобы переоределять методы в RecyclerView, так что этот кусок приложения останется громоздким и некрасивым.
 */
public class FreeAlarmsHandler implements HandlerCallback { // Репозиторий предоставляет абстрактный доступ к базе данных, то есть представлен в роли API (так они советуют делать)
    private final String TAG = "TAG_UIH";
    private final AlarmRepo repo;
    private final Intent srcIntent;
    private final Intent execIntent;

    private RecyclerView recycler;
    private AlarmListAdapter adapter;
    private MainActivity.LDObserver observer;
    private Context context;

    private final LiveData<List<Alarm>> roomLD;
    private List<Alarm> bufferList = new LinkedList<>();
    private List<Alarm> oldList;

    private final Alarm addAlarm = new Alarm(66,66, null);
    private Alarm prefAlarm;
    private RLMCallback rlmCallback;
    private int prefPos;

    FreeAlarmsHandler(AlarmRepo repo, Intent intent){
        this.repo = repo;
        addAlarm.setAddFlag(true);
        srcIntent = intent;
        execIntent = new Intent(srcIntent);

        roomLD = repo.getLD();//При создани репозитория мы передаём этот список в MA,
        Log.d(TAG, "Handler instance created");
    }

    public void pass(RecyclerView recyclerView, AlarmListAdapter adapter, MainActivity.LDObserver observer, Context applicationContext) {
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
    }


    public HandlerCallback pullHandlerCallback(){
        return this;
    }
    public void passRLMCallback (RLMCallback callback){
        this.rlmCallback = callback;
    }
    @Override
    public RLMCallback pullRLMCallback(){
        return rlmCallback;
    }


    void fill (){
        prepare();
        repo.deleteAll();
        for (int i = 0; i<28; i++){
            Alarm alarm = new Alarm(0, i,null);
            bufferList.add(alarm);
            repo.insert(alarm);
        }
        bufferList.add(addAlarm);
        repo.insert(addAlarm);
        submitList(oldList, bufferList);
    }

    void clear () {
        prepare();
        repo.deleteAll();
        bufferList.add(addAlarm);
        repo.insert(addAlarm);
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
        repo.deleteOne(current.getHour(), current.getMinute());

        execIntent.replaceExtras(srcIntent);
        execIntent.putExtra("prevActive", true);
        if (current.getTriggerTime()!=null) execIntent.putExtra("triggerTime", current.getTriggerTime().getTime());
        context.startService(execIntent);

        adapter.submitList(bufferList);
        if (prefRemoved) recycler.post(()-> adapter.notifyItemRemoved(prefPos));
        recycler.post(()-> adapter.notifyItemRemoved(currentPos));
    }

    public void addTest(int delay){
        Toast.makeText(context, "Alarm will be launched in " + delay + " sec", Toast.LENGTH_SHORT).show();

        Calendar currentCalendar = Calendar.getInstance();
        currentCalendar.setTimeInMillis(System.currentTimeMillis());
        currentCalendar.set(Calendar.MILLISECOND, 0);
        currentCalendar.add(Calendar.SECOND, delay);

        Alarm current = new Alarm(77,77, currentCalendar.getTime());
        current.setOnOffState(true);
        repo.insert(current);

        execIntent.replaceExtras(srcIntent);
        execIntent.putExtra("prevPassive", true);
        execIntent.putExtra("triggerTime", current.getTriggerTime().getTime());
        context.startService(execIntent);
    }

    //New items always provided with off state
    @Override
    public void addItem(int hour, int minute) {
        int currentPos;
        boolean prefRemoved = false;
        prepare();
        bufferList.addAll(oldList);

        int i = 0; Alarm req;
        while (i < bufferList.size()){
            req = bufferList.get(i);
            if (req.getHour() == hour & req.getMinute() == minute){
                throw new UnsupportedOperationException("AlarmActivity already exist, can't add");
            }
            else i++;
        }

        Alarm current = new Alarm(hour, minute, null);
        bufferList.add(current);
        repo.insert(current);
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

    //Changes main time of the alarm. ALso we need to edit Exec's recordings about both old and new ones
    //Every changed item acquires triggerTime
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
                throw new UnsupportedOperationException("AlarmActivity already exist, can't add");
            }
            else i++;
        }

        prefPos = bufferList.indexOf(prefAlarm);
        if (bufferList.remove(prefAlarm)) prefRemoved = true;

        Alarm current = bufferList.get(oldPos);
        repo.deleteOne(current.getHour(), current.getMinute());
        execIntent.replaceExtras(srcIntent);
        execIntent.putExtra("prevActive", true);
        if (current.getTriggerTime()!=null) execIntent.putExtra("triggerTime", current.getTriggerTime().getTime());
        context.startService(execIntent);

        current.setHour(hour);
        current.setMinute(minute);

        bufferList.sort((o1, o2) -> {
            if (o1.getHour() != o2.getHour()) return o1.getHour() - o2.getHour();
            else return o1.getMinute() - o2.getMinute();
        });
        currentPos = bufferList.indexOf(current);

        adapter.submitList(bufferList);
        if (prefRemoved) recycler.post(()-> adapter.notifyItemRemoved(prefPos));
        recycler.post(()-> adapter.notifyItemRemoved(oldPos));
        recycler.post(()-> adapter.notifyItemInserted(currentPos));

        execIntent.replaceExtras(srcIntent);
        if (current.isOnOffState()) execIntent.putExtra("prevPassive", true);
        else execIntent.putExtra("prevActive", true);

        Calendar currentCalendar = Calendar.getInstance();
        currentCalendar.setTimeInMillis(System.currentTimeMillis());
        currentCalendar.set(Calendar.MILLISECOND, 0);
        currentCalendar.set(Calendar.SECOND, 0);
        currentCalendar.set(Calendar.MINUTE, current.getMinute());
        currentCalendar.set(Calendar.HOUR_OF_DAY, current.getHour());
        if (currentCalendar.getTimeInMillis() <= System.currentTimeMillis()) currentCalendar.roll(Calendar.DATE, true);

        current.setTriggerTime(currentCalendar.getTime());
        repo.insert(current);
        execIntent.putExtra("triggerTime", currentCalendar.getTimeInMillis());
        context.startService(execIntent);
    }

    //Updates existing alarm's data. Can directly correspond to the Exec
    @Override
    public void updateItem(int parentPos, boolean switcherState) {//todo добавить флаги повтора в enum. Нужно записывать в БД время следующего срабатывания, и обновлять его сразу после этого срабатывания, на основании флагов
        Log.d (TAG, "Updating item " + parentPos + ", state: " + switcherState);
        prepare();
        bufferList.addAll(oldList);
        Alarm current = bufferList.get(parentPos);

        Calendar currentCalendar = Calendar.getInstance();
        currentCalendar.setTimeInMillis(System.currentTimeMillis());
        currentCalendar.set(Calendar.MILLISECOND, 0);
        currentCalendar.set(Calendar.SECOND, 0);
        currentCalendar.set(Calendar.MINUTE, current.getMinute());
        currentCalendar.set(Calendar.HOUR_OF_DAY, current.getHour());
        if (currentCalendar.getTimeInMillis() <= System.currentTimeMillis()) currentCalendar.roll(Calendar.DATE, true);
        Log.d (TAG, "sending: " + currentCalendar.get(Calendar.DATE) + currentCalendar.get(Calendar.HOUR_OF_DAY) + currentCalendar.get(Calendar.MINUTE));

        current.setOnOffState(switcherState);
        current.setTriggerTime(currentCalendar.getTime());

        bufferList.set(parentPos, current);
        repo.update(current);
        adapter.submitList(bufferList);

        /*
        We always need to clear extras
        Independently of the previous on/off state we need to inform Exec about new one
         */
        execIntent.replaceExtras(srcIntent);
        if (switcherState) execIntent.putExtra("prevPassive", true);
        else execIntent.putExtra("prevActive", true);
        execIntent.putExtra("triggerTime", currentCalendar.getTime().getTime());
        context.startService(execIntent);
    }

    public void onResumeUpdate(){
        rlmCallback.hideOnResume();
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
        bufferList = repo.getAll();
        Alarm parent = bufferList.get(parentPos);

        int addAlarmPos; int i = 0; boolean flag;
        do{
            addAlarmPos = i;
            flag = bufferList.get(i).isAddFlag();
            i++;
        }
        while (!flag);

        Alarm pref = new Alarm(parent.getHour(), parent.getMinute(), null);//Здесь мы берём информацию из материнского элемента, согласно его переданной позиции
        pref.setPrefFlag();
        pref.setParentPos(parentPos);
        pref.setOnOffState(parent.isOnOffState());
        if (parentPos == addAlarmPos) pref.setPrefBelongsToAdd();
        prefAlarm = pref;

        bufferList.add(prefPos, pref);
        submitList(oldList, bufferList);
    }

    @Override
    public void removePref(boolean pullDataset) {
        prepare();

        if (pullDataset) bufferList = repo.getAll();
        else bufferList.addAll(oldList);

        bufferList.remove(prefAlarm);
        submitList(oldList, bufferList);
    }

    @Override
    public void removeNPassPrefToAdapter(int parentPos, int prefPos) {
        this.prefPos = prefPos;
        //Log.d (TAG, "" + parentPos + prefPos);
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

        Alarm pref = new Alarm(parent.getHour(), parent.getMinute(), null);//Здесь мы берём информацию из материнского элемента, согласно его переданной позиции
        pref.setPrefFlag();
        pref.setParentPos(parentPos);
        pref.setOnOffState(parent.isOnOffState());
        if (parentPos == addAlarmPos) pref.setPrefBelongsToAdd();
        prefAlarm = pref;

        bufferList.add(prefPos, pref);
        submitList(oldList, bufferList);
        //Log.d (TAG, bufferList.get(prefPos).isPrefFlag() + "");
    }


    private static class ListDiff extends DiffUtil.Callback {//Эту лабуду оставим для оповещения о настройках
        private final List<Alarm> oldList;
        private final List<Alarm> newList;

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

