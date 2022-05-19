package com.vova9110.bloodbath.RecyclerView;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.NumberPicker;
import android.widget.Switch;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import com.vova9110.bloodbath.AlarmRepo;
import com.vova9110.bloodbath.Database.Alarm;
import com.vova9110.bloodbath.R;
import com.vova9110.bloodbath.RLMCallback;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

public class AlarmViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener{
    private final String TAG = "TAG_AVH";
    private final TextView timeView;
    private final NumberPicker hourPicker;
    private final NumberPicker minutePicker;
    private final Switch switcher;
    private final com.google.android.material.floatingactionbutton.FloatingActionButton FAB;

    private final AlarmRepo repo;
    private final RLMCallback rlmCallback;

    private AlarmViewHolder(View view, AlarmRepo repo) {
        super(view);
        this.repo = repo;
        rlmCallback = repo.pullRLMCallback();

        timeView = view.findViewById(R.id.timeWindow);
        hourPicker = view.findViewById(R.id.picker_h);
        minutePicker = view.findViewById(R.id.picker_m);
        switcher = view.findViewById(R.id.switcher);
        FAB = view.findViewById(R.id.floatingActionButton);

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
        Log.d (TAG, "notify delete click");
        rlmCallback.updateDatasetEvent(getAdapterPosition(), RowLayoutManager.MODE_DELETE, -1, -1);
        return true;
    }

    @Override
    public void onClick(View v) {
        Log.d (TAG, "notify click" + getAdapterPosition());
        rlmCallback.notifyBaseClick(getAdapterPosition());
    }


    public void bind(int hour, int minute) {
        timeView.setVisibility(View.VISIBLE); hourPicker.setVisibility(View.GONE); minutePicker.setVisibility(View.GONE); switcher.setVisibility(View.GONE); FAB.setVisibility(View.GONE);
        timeView.setOnClickListener(this);
        timeView.setOnLongClickListener(this);
        //Log.d (TAG, "binding " + getAdapterPosition() + getOldPosition());

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

        if (!current.isPrefBelongsToAdd()) {
            hourPicker.setValue(current.getHour());
            minutePicker.setValue(current.getMinute());
        }
        else{
            calendar.setTimeInMillis(System.currentTimeMillis());
            hourPicker.setValue(calendar.get(Calendar.HOUR_OF_DAY));
            minutePicker.setValue(calendar.get(Calendar.MINUTE));

            switcher.setVisibility(View.INVISIBLE);
        }

        if (current.isPrefBelongsToAdd()) FAB.setOnClickListener(view ->{
            Log.d (TAG, "notify adding click");
            rlmCallback.updateDatasetEvent(getAdapterPosition(), RowLayoutManager.MODE_ADD, hourPicker.getValue(), minutePicker.getValue());
        });
        else FAB.setOnClickListener(view ->{
            Log.d (TAG, "notify changing click");
            rlmCallback.updateDatasetEvent(current.getParentPos(), RowLayoutManager.MODE_CHANGE, hourPicker.getValue(), minutePicker.getValue());
        });

        switcher.setChecked(current.isOnOffState());
        switcher.setOnCheckedChangeListener((buttonView, isChecked) -> repo.deployItem(current.getParentPos(), isChecked));
    }
}
