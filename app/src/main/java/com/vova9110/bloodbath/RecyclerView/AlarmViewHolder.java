package com.vova9110.bloodbath.RecyclerView;

import android.annotation.SuppressLint;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.NumberPicker;
import android.widget.Switch;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.vova9110.bloodbath.AlarmRepo;
import com.vova9110.bloodbath.RepoCallback;
import com.vova9110.bloodbath.R;
import com.vova9110.bloodbath.RLMCallback;

public class AlarmViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener{
    private final String TAG = "TAG_AVH";
    private final TextView timeView;
    private final NumberPicker hourPicker;
    private final NumberPicker minutePicker;
    private final Switch switcher;

    private final RLMCallback rlmCallback;

    private AlarmViewHolder(View view, AlarmRepo repo) {
        super(view);
        rlmCallback = repo.pullRLMCallback();

        timeView = view.findViewById(R.id.timeWindow);
        hourPicker = view.findViewById(R.id.picker_h);
        minutePicker = view.findViewById(R.id.picker_m);
        switcher = view.findViewById(R.id.switcher);

        hourPicker.setMinValue(0); hourPicker.setMaxValue(24);
        minutePicker.setMinValue(0); minutePicker.setMaxValue(60);
    }

    static AlarmViewHolder create(ViewGroup parent, AlarmRepo repo) {
        //В данном случае, когда мы указиваем родительскую ViewGroup в качестве источника LayoutParams, эти самые LP передаются в View при наполнении
        //Конкретно - это те, которые указаны в материнской LinearLayout, ширина и высота всей разметки.
        //Если не передать эти LP, то RLM подхватит LP по умолчанию
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.recycler_view_item, parent, false);
                return new AlarmViewHolder(itemView, repo);
    }
    
    @Override
    public boolean onLongClick(View v) {
        Log.d (TAG, "Delete click");
        return true;
    }

    @Override
    public void onClick(View v) {
        Log.d (TAG, "notify click" + getAdapterPosition());
        rlmCallback.notifyBaseClick(getAdapterPosition());
    }


    public void bind(int hour, int minute) {
        timeView.setOnClickListener(this);
        timeView.setOnLongClickListener(this);

        timeView.setText(String.format("%02d:%02d", hour, minute));
    }

    public void bindAddAlarm() {//Все элементы уже итак видны и пикерам не нужно устанавливать значения
        timeView.setOnClickListener(this);

        timeView.setText("+");
    }

    public void bindPref(int hour, int minute, boolean switcherState){
        timeView.setVisibility(View.GONE); hourPicker.setVisibility(View.VISIBLE); minutePicker.setVisibility(View.VISIBLE); switcher.setVisibility(View.VISIBLE);

        hourPicker.setValue(hour);
        minutePicker.setValue(minute);
        switcher.setChecked(switcherState);
    }
}
