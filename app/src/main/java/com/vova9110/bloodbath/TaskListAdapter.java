package com.vova9110.bloodbath;

import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;

import com.vova9110.bloodbath.Database.Tasks;

public class TaskListAdapter extends ListAdapter<Tasks, AlarmViewHolder> {

    private AppComponent component;

    public TaskListAdapter(@NonNull DiffUtil.ItemCallback<Tasks> diffCallback, AppComponent component) {
        super(diffCallback);
        this.component = component;
    }
    @Override
    public AlarmViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return AlarmViewHolder.create(parent, component);
    }

    @Override
    public void onBindViewHolder(AlarmViewHolder holder, int position) {
        Tasks current = getItem(position);
        holder.bind(current.getTask());
    }

    static class TaskDiff extends DiffUtil.ItemCallback<Tasks> {

        @Override
        public boolean areItemsTheSame(@NonNull Tasks oldItem, @NonNull Tasks newItem) {
            return oldItem == newItem;
        }

        @Override
        public boolean areContentsTheSame(@NonNull Tasks oldItem, @NonNull Tasks newItem) {
            return oldItem.getTask().equals(newItem.getTask());
        }
    }
}

