package com.vova9110.bloodbath;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckedTextView;
import android.widget.ImageSwitcher;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

public class TaskViewHolder extends RecyclerView.ViewHolder implements View.OnLongClickListener{
private final TextView taskItemView;
private final ImageView randomPic;
private static TaskRepo.DeleteClick clickHandler;

        private TaskViewHolder(View itemView, TaskRepo.DeleteClick deleteClick) {
            super(itemView);
            taskItemView = itemView.findViewById(R.id.textView);
            randomPic = itemView.findViewById(R.id.imageView);

            this.clickHandler = deleteClick;
            taskItemView.setClickable(true);
            taskItemView.setOnLongClickListener(this);
        }

        public void bind(String text) {
            taskItemView.setText(text);
            randomPic.setImageResource(R.drawable.photo);
        }

        static TaskViewHolder create(ViewGroup parent, TaskRepo.DeleteClick deleteClick) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.recyclerview_item, parent, false);
            return new TaskViewHolder(view, deleteClick);
        }

    @Override
    public boolean onLongClick(View v) {
        clickHandler.deleteClick(getAdapterPosition());
        return true;
    }
}
