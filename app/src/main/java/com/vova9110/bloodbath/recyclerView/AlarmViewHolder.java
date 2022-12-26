package com.vova9110.bloodbath.recyclerView;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.NumberPicker;
import android.widget.Switch;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.vova9110.bloodbath.SplitLogger;
import com.vova9110.bloodbath.alarmsUI.HandlerCallback;
import com.vova9110.bloodbath.alarmsUI.RLMCallback;
import com.vova9110.bloodbath.database.Alarm;
import com.vova9110.bloodbath.R;

import java.util.Calendar;

public class AlarmViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener{
    private SplitLogger sl;
    private final TextView timeView;
    private final NumberPicker hourPicker;
    private final NumberPicker minutePicker;
    private final Switch switcher;
    private final com.google.android.material.floatingactionbutton.FloatingActionButton FAB;

    private final HandlerCallback hCallback;
    private final RLMCallback rlmCallback;

    private AlarmViewHolder(View view, HandlerCallback c) {
        super(view);
        this.hCallback = c;
        rlmCallback = c.pullRLMCallback();

        timeView = view.findViewById(R.id.timeWindow);
        hourPicker = view.findViewById(R.id.picker_h);
        minutePicker = view.findViewById(R.id.picker_m);
        switcher = view.findViewById(R.id.switcher);
        FAB = view.findViewById(R.id.floatingActionButton);

        hourPicker.setMinValue(0); hourPicker.setMaxValue(24);
        minutePicker.setMinValue(0); minutePicker.setMaxValue(59);
    }

    public int getPosVerify(){
        return 0;
        //todo вставить ассерт сюда
    }

    static AlarmViewHolder create(ViewGroup parent, HandlerCallback hCallback) {
        //В данном случае, когда мы указиваем родительскую ViewGroup в качестве источника LayoutParams, эти самые LP передаются в View при наполнении
        //Конкретно - это те, которые указаны в материнской LinearLayout, ширина и высота всей разметки.
        //Если не передать эти LP, то RLM подхватит LP по умолчанию
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.recycler_view_item, parent, false);
                return new AlarmViewHolder(itemView, hCallback);
    }

    @Override
    public boolean onLongClick(View v) {
        sl.i( "notify delete click: " + getAdapterPosition());
        if (getAdapterPosition()==-1) {
            sl.sp( "onClick: ERROR");
            return false;
        }
        hCallback.deleteItem(getAdapterPosition());
        return true;
    }

    @Override
    public void onClick(View v) {
        sl.f( "notify click: " + getAdapterPosition());
        if (getAdapterPosition()==-1) {
            sl.sp( "onClick: ERROR");
            return;
        }
        rlmCallback.notifyBaseClick(getAdapterPosition());
    }


    public void bind(int hour, int minute) {
        timeView.setVisibility(View.VISIBLE); hourPicker.setVisibility(View.GONE); minutePicker.setVisibility(View.GONE); switcher.setVisibility(View.GONE); FAB.setVisibility(View.GONE);
        timeView.setOnClickListener(this);
        timeView.setOnLongClickListener(this);

        timeView.setText(String.format("%02d:%02d", hour, minute));
    }

    public void bindAddAlarm() {
        timeView.setVisibility(View.VISIBLE); hourPicker.setVisibility(View.GONE); minutePicker.setVisibility(View.GONE); switcher.setVisibility(View.GONE); FAB.setVisibility(View.GONE);
        timeView.setOnClickListener(this);

        timeView.setText("+");
    }

    public void bindPref(Alarm current){
        timeView.setVisibility(View.GONE); hourPicker.setVisibility(View.VISIBLE); minutePicker.setVisibility(View.VISIBLE); switcher.setVisibility(View.VISIBLE); FAB.setVisibility(View.VISIBLE);
        Calendar calendar = Calendar.getInstance();

        if (!current.getPrefBelongsToAdd()) {
            hourPicker.setValue(current.getHour());
            minutePicker.setValue(current.getMinute());
        }
        else{
            calendar.setTimeInMillis(System.currentTimeMillis());
            hourPicker.setValue(calendar.get(Calendar.HOUR_OF_DAY));
            minutePicker.setValue(calendar.get(Calendar.MINUTE));

            switcher.setVisibility(View.INVISIBLE);
        }

        if (current.getPrefBelongsToAdd()) FAB.setOnClickListener(view ->{
            sl.i( "notify adding click: " + getAdapterPosition());
            hCallback.addItem(hourPicker.getValue(), minutePicker.getValue());
        });
        else FAB.setOnClickListener(view ->{
            sl.i( "notify changing click: " + getAdapterPosition());
            hCallback.changeItem(getAdapterPosition(), hourPicker.getValue(), minutePicker.getValue());
        });

        switcher.setOnCheckedChangeListener(null);
        switcher.setChecked(current.getEnabled());
        switcher.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sl.i( "notify power click: " + isChecked);
            hCallback.updateOneState(current.getParentPos(), isChecked);
        });
    }
}
