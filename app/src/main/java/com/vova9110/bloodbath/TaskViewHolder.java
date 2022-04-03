package com.vova9110.bloodbath;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckedTextView;
import android.widget.ImageSwitcher;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import javax.inject.Inject;

public class TaskViewHolder extends RecyclerView.ViewHolder{
private final TextView taskItemView;
@Inject
public TaskRepo repo;

        private TaskViewHolder(View itemView, AppComponent component) {
            super(itemView);
            component.inject(this);
            taskItemView = itemView.findViewById(R.id.textView);

            taskItemView.setClickable(true);
            taskItemView.setOnLongClickListener(view -> {
                repo.delTask(getAdapterPosition());
                return true;
            });
        }

        public void bind(String text) {
            taskItemView.setText(text);
        }

        static TaskViewHolder create(ViewGroup parent, AppComponent component) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.recyclerview_item, parent, false);
            return new TaskViewHolder(view, component);
        }
}
