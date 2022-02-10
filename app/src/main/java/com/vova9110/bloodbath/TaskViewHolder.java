package com.vova9110.bloodbath;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.RecyclerView;

public class TaskViewHolder extends RecyclerView.ViewHolder{
private final TextView wordItemView;
private final TaskRepo Repo = new TaskRepo();
private String text;

        private TaskViewHolder(View itemView) {
            super(itemView);
            wordItemView = itemView.findViewById(R.id.textView);
            text = wordItemView.getText().toString();
            wordItemView.setOnClickListener(view -> Toast.makeText(wordItemView.getContext(), text, Toast.LENGTH_SHORT));
        }

        public void bind(String text) {
            wordItemView.setText(text);
        }

        static TaskViewHolder create(ViewGroup parent) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.recyclerview_item, parent, false);
            return new TaskViewHolder(view);
        }
}
