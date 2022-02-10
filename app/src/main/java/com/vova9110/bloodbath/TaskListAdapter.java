package com.vova9110.bloodbath;

import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;

import com.vova9110.bloodbath.Database.Tasks;

public class TaskListAdapter extends ListAdapter<Tasks, TaskViewHolder> {

    public TaskListAdapter(@NonNull DiffUtil.ItemCallback<Tasks> diffCallback) {
        super(diffCallback);
    }

    @Override
    public TaskViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        return TaskViewHolder.create(parent);
    }

    @Override
    public void onBindViewHolder(TaskViewHolder holder, int position) {
        Tasks current = getItem(position);
        holder.bind(current.getTask());
    }

    static class WordDiff extends DiffUtil.ItemCallback<Tasks> {

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

