package com.vova9110.bloodbath.RecyclerView;

import android.util.Log;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.vova9110.bloodbath.AlarmsUI.HandlerCallback;
import com.vova9110.bloodbath.Database.Alarm;

import java.util.LinkedList;
import java.util.List;

public class AlarmListAdapter extends RecyclerView.Adapter<AlarmViewHolder>{
    private final String TAG = "TAG_ALA";
    private HandlerCallback hCallback;
    private List<Alarm> mList = new LinkedList<>();

    public AlarmListAdapter(HandlerCallback hCallback, List<Alarm> initialList) {
        this.hCallback = hCallback;
        this.mList.addAll(initialList);
    }

    @NonNull
    @Override
    public AlarmViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return AlarmViewHolder.create(parent, hCallback);
    }

    @Override
    public void onBindViewHolder(AlarmViewHolder holder, int position) {
        Alarm current = mList.get(position);
        if (current.getAddFlag()) holder.bindAddAlarm();
        else if (current.getPrefFlag()) holder.bindPref(current);
        else holder.bind(current.getHour(), current.getMinute());
    }

    @Override
    public int getItemCount() {
        return mList.size();
    }

    public void submitList(List<Alarm> list){
        mList.clear();
        mList.addAll(list);
    }
    public List<Alarm> getCurrentList () {
        return mList;
    }
}
