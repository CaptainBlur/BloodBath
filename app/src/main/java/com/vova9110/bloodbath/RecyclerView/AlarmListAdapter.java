package com.vova9110.bloodbath.RecyclerView;

import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.vova9110.bloodbath.FreeAlarmsHandler;
import com.vova9110.bloodbath.Database.Alarm;

import java.util.LinkedList;
import java.util.List;

public class AlarmListAdapter extends RecyclerView.Adapter<AlarmViewHolder>{
    private final String TAG = "TAG_ALA";
    private FreeAlarmsHandler repo;
    private List<Alarm> mList = new LinkedList<>();

    public AlarmListAdapter(FreeAlarmsHandler repo) {
        this.repo = repo;
    }

    @NonNull
    @Override
    public AlarmViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return AlarmViewHolder.create(parent, repo );
    }

    @Override
    public void onBindViewHolder(AlarmViewHolder holder, int position) {
        Alarm current = mList.get(position);
        //Log.d (TAG, "" + current.isAddFlag() + current.isPrefBelongsToAdd());
        if (current.isAddFlag()) holder.bindAddAlarm();
        else if (current.isPrefFlag()) holder.bindPref(current);
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
