package com.vova9110.bloodbath;

import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;

import com.vova9110.bloodbath.Database.Alarm;

public class TaskListAdapter extends ListAdapter<Alarm, AlarmViewHolder> {

    private AppComponent component;

    public TaskListAdapter(@NonNull DiffUtil.ItemCallback<Alarm> diffCallback, AppComponent component) {
        super(diffCallback);
        this.component = component;
    }
    @Override
    public AlarmViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return AlarmViewHolder.create(parent, component);
    }

    @Override
    public void onBindViewHolder(AlarmViewHolder holder, int position) {
        Alarm current = getItem(position);
        holder.bind(current.getHour(), current.getMinute());
    }

    static class AlarmDiff extends DiffUtil.ItemCallback<Alarm> {

        @Override
        public boolean areItemsTheSame(@NonNull Alarm oldItem, @NonNull Alarm newItem) {
            return oldItem == newItem;
        }

        @Override
        public boolean areContentsTheSame(@NonNull Alarm oldItem, @NonNull Alarm newItem) {
            return oldItem.getHour()==newItem.getHour() && oldItem.getMinute()==newItem.getMinute();
        }
    }
}

