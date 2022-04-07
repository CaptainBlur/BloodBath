package com.vova9110.bloodbath;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckedTextView;
import android.widget.ImageSwitcher;
import android.widget.ImageView;
import android.widget.NumberPicker;
import android.widget.Switch;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import javax.inject.Inject;

public class AlarmViewHolder extends RecyclerView.ViewHolder{
private TextView mTimeView;
private NumberPicker mHourPicker;
private NumberPicker mMinutePicker;
private Switch mSwitcher;
@Inject
public AlarmRepo repo;

private AlarmViewHolder(View itemView, AppComponent component) {
    super(itemView);
    component.inject(this);

    mTimeView = itemView.findViewById(R.id.textView);
    mTimeView.setOnLongClickListener(view -> {
        //repo.delTask(getAdapterPosition());
        return true;
    });

    mHourPicker = itemView.findViewById(R.id.picker_h);
    mHourPicker.setMinValue(00); mHourPicker.setMaxValue(24);
    mMinutePicker = itemView.findViewById(R.id.picker_m);
    mMinutePicker.setMinValue(00); mMinutePicker.setMaxValue(60);
    mTimeView.setOnClickListener(view -> {
        getAdapterPosition();
        return;
    });
}

public void bind(int hour, int minute) {

}//В этом методе вьюшка только подхватывает данные для отображения

static AlarmViewHolder create(ViewGroup parent, AppComponent component) {
    View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.recyclerview_item, parent, false);
    return new AlarmViewHolder(view, component);
}
}
