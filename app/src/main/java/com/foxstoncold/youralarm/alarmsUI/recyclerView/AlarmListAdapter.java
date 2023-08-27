package com.foxstoncold.youralarm.alarmsUI.recyclerView;

import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.foxstoncold.youralarm.alarmsUI.FAHCallback;
import com.foxstoncold.youralarm.database.Alarm;

import java.util.LinkedList;
import java.util.List;

public class AlarmListAdapter extends RecyclerView.Adapter<ChildViewHolder>{
    private FAHCallback hCallback;
    private List<Alarm> mList = new LinkedList<>();

    public static final int TYPE_TIME = 717;
    public static final int TYPE_PREF = 60;
    public static final int TYPE_ADD = 805;

    public AlarmListAdapter(FAHCallback hCallback, List<Alarm> initialList) {
        this.hCallback = hCallback;
        this.mList.addAll(initialList);
    }

    @NonNull
    @Override
    public ChildViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return ChildViewHolder.create(parent, hCallback);
    }

    @Override
    public void onBindViewHolder(@NonNull ChildViewHolder holder, int position) {
        Alarm current = mList.get(position);

        holder.bind(current);
    }

    @Override
    public int getItemViewType(int position) {
        Alarm current = mList.get(position);

        if (current.getAddFlag()) return TYPE_ADD;
        else if (current.getPrefFlag()) return TYPE_PREF;
        else return TYPE_TIME;
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
