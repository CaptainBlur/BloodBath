package com.vova9110.bloodbath.alarmsUI.recyclerView;

import static com.google.android.material.timepicker.TimeFormat.CLOCK_24H;

import android.content.res.Resources;
import android.graphics.Rect;
import android.graphics.drawable.AnimationDrawable;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;

import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.timepicker.MaterialTimePicker;
import com.vova9110.bloodbath.SplitLoggerUI;
import com.vova9110.bloodbath.alarmsUI.AdjustableCompoundButton;
import com.vova9110.bloodbath.alarmsUI.AdjustableImageView;
import com.vova9110.bloodbath.alarmsUI.AdjustableTextView;
import com.vova9110.bloodbath.alarmsUI.DrawableUtils;
import com.vova9110.bloodbath.alarmsUI.DrawablesAide;
import com.vova9110.bloodbath.alarmsUI.FAHCallback;
import com.vova9110.bloodbath.database.Alarm;
import com.vova9110.bloodbath.R;

import java.util.Calendar;
import java.util.Locale;

public class ChildViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener{
    private SplitLoggerUI slU;
    private final View view;
    private final FAHCallback hCallback;
    private final Rect globalRect;
    private final Rect frameRect;
    private final Rect prefTimeRect;
    private final AdjustableCompoundButton timeWindow;
    private final AdjustableImageView prefFrame;
    private final AdjustableTextView prefTime;
    private final AdjustableCompoundButton prefPower;
    private final AdjustableCompoundButton prefMusic;


    private ChildViewHolder(View view, FAHCallback c, Rect rect) {
        super(view);
        this.view = view;
        this.hCallback = c;
        globalRect = rect;

        timeWindow = view.findViewById(R.id.rv_time_window);
        timeWindow.makeAdjustments(globalRect);
        timeWindow.recycleAttributes();


        prefFrame = view.findViewById(R.id.rv_pref_frame);
        frameRect = prefFrame.makeAdjustments(globalRect);
        prefFrame.recycleAttributes();
//        prefFrame.setAnimation();

        prefTime = view.findViewById(R.id.rv_pref_time_window);
        prefTimeRect = prefTime.makeAdjustments(frameRect);
        prefTime.recycleAttributes();

        prefPower = view.findViewById(R.id.rv_pref_power);
        prefPower.makeAdjustments(frameRect);
        prefPower.recycleAttributes();

        prefMusic = view.findViewById(R.id.rv_pref_music);
        prefMusic.setBackground(hCallback.getDrawable(R.drawable.rv_pref_music, true));

    }

    private void setUnchecked(boolean startAnimation){
        slU.fr("Pref set OFF");

        Resources res = view.getResources();
        float unlitTextM = res.getFraction(R.fraction.rv_pref_time_unlit_multiplier, 1,1);
        prefTime.setTextSize(TypedValue.COMPLEX_UNIT_PX, prefTimeRect.height()*unlitTextM);
        prefTime.setTextColor(view.getContext().getColor(R.color.mild_greyscaleLight));
        prefTime.setTypeface(res.getFont(R.font.avenir_lt_55_roman));

        prefPower.setChecked(true);

        setupAnimatedDrawables(false, startAnimation);

    }
    private void setChecked(boolean startAnimation){
        slU.fr("Pref set ON");

        Resources res = view.getResources();
        float litTextM = res.getFraction(R.fraction.rv_pref_time_lit_multiplier, 1,1);
        prefTime.setTextSize(TypedValue.COMPLEX_UNIT_PX, prefTimeRect.height()*litTextM);
        prefTime.setTextColor(view.getContext().getColor(R.color.mild_shadyDist));
        prefTime.setTypeface(res.getFont(R.font.avenir_lt_65_medium));

        prefPower.setChecked(false);

        setupAnimatedDrawables(true, startAnimation);
    }


    private void setupAnimatedDrawables(boolean checked, boolean startAnimation){

        AnimationDrawable frameDrawable = null;
        AnimationDrawable powerDrawable = (AnimationDrawable) hCallback.getDrawable(DrawablesAide.rv_pref_power, checked);
        assert hCallback.getPrefAlignment()!=null;

        switch (hCallback.getPrefAlignment()){
            case (1):
                frameDrawable = (AnimationDrawable) hCallback.getDrawable(DrawablesAide.rv_pref_frame_right, checked);
                prefFrame.setRotationY(180);
                break;

            case(0):
                frameDrawable = (AnimationDrawable) hCallback.getDrawable(DrawablesAide.rv_pref_frame_center, checked);
                break;

            case(-1):
                frameDrawable = (AnimationDrawable) hCallback.getDrawable(DrawablesAide.rv_pref_frame_right, checked);
                prefFrame.setRotationY(0);
                break;
        }
        assert frameDrawable != null;


        if (startAnimation){
            prefFrame.setImageDrawable(frameDrawable);
            frameDrawable.start();

            prefPower.setBackground(powerDrawable);
            powerDrawable.start();
        }
        else{
            prefFrame.setImageDrawable(frameDrawable.getFrame(frameDrawable.getNumberOfFrames()-1));
            prefPower.setBackground(powerDrawable.getFrame(powerDrawable.getNumberOfFrames()-1));
        }
    }

    private void setParentEnvoy(){
        ViewGroup.LayoutParams params = timeWindow.getLayoutParams();
        params.width = hCallback.getRatios().getParentEnvoyRect().width();
        params.height = hCallback.getRatios().getParentEnvoyRect().height();

        timeWindow.setLayoutParams(params);
        timeWindow.setBackgroundResource(0);
        timeWindow.setText("");
    }

    private void setBindVisibility(){
        timeWindow.setVisibility(View.VISIBLE);

        prefFrame.setVisibility(View.GONE);
        prefTime.setVisibility(View.GONE);
        prefPower.setVisibility(View.GONE);
        prefMusic.setVisibility(View.GONE);
    }
    private void setBindPrefVisibility(){
        timeWindow.setVisibility(View.GONE);

        prefFrame.setVisibility(View.VISIBLE);
        prefTime.setVisibility(View.VISIBLE);
        prefPower.setVisibility(View.VISIBLE);
        prefMusic.setVisibility(View.VISIBLE);
    }


    public void bind(Alarm current){
        switch (getItemViewType()){
            case (AlarmListAdapter.TYPE_TIME):
                bindTime(current.component1(), current.component2());
                break;
            case(AlarmListAdapter.TYPE_PREF):
                bindPref(current);
                break;
            case(AlarmListAdapter.TYPE_ADD):
                bindAddAlarm();
        }
    }
    public void bindTime(int hour, int minute) {
//        hourPicker.setVisibility(View.GONE); minutePicker.setVisibility(View.GONE); switcher.setVisibility(View.GONE); FAB.setVisibility(View.GONE);
        setBindVisibility();

        timeWindow.setText(String.format(Locale.ENGLISH,"%02d:%02d", hour, minute));
        //Receiving drawable's state from handler
        switch(hCallback.getTimeWindowState(getAdapterPosition())){
            case (0):
                setParentEnvoy();
            break;

            case (1): timeWindow.setBackground(hCallback.getDrawable(R.drawable.rv_time_window, true));
                timeWindow.setTextColor(view.getContext().getColor(R.color.mild_shadyDist));
            break;

            case (-1): timeWindow.setBackground(hCallback.getDrawable(R.drawable.rv_time_window, false));
                timeWindow.setTextColor(view.getContext().getColor(R.color.mild_shady));
                break;
        }
//        slU.fst("BIND " + minute);
        timeWindow.setOnClickListener(this);
        timeWindow.setOnLongClickListener(this);

    }

    public void bindAddAlarm() {
//        hourPicker.setVisibility(View.GONE); minutePicker.setVisibility(View.GONE); switcher.setVisibility(View.GONE); FAB.setVisibility(View.GONE);
        setBindVisibility();

        timeWindow.setOnClickListener(this);
        timeWindow.setText("+");
    }

    public void bindPref(Alarm current){
        slU.fr("BINDING PREF");
//        hourPicker.setVisibility(View.VISIBLE); minutePicker.setVisibility(View.VISIBLE); switcher.setVisibility(View.VISIBLE); FAB.setVisibility(View.VISIBLE);
        setBindPrefVisibility();

        if (!current.component3()) setUnchecked(false);
        else setChecked(false);
        prefTime.setText(String.format(Locale.ENGLISH,"%02d:%02d", current.component1(), current.component2()));
        prefTime.setOnClickListener((v0)->{
            MaterialTimePicker picker = new MaterialTimePicker.Builder()
                    .setHour(current.component1())
                    .setMinute(current.component2())
                    .setInputMode(MaterialTimePicker.INPUT_MODE_CLOCK)
                    .setTimeFormat(CLOCK_24H)
                    .build();

            picker.addOnPositiveButtonClickListener((v1)->{
                hCallback.changeItemTime(picker.getHour(), picker.getMinute());
            });

            picker.show(hCallback.getFragmentManager(), "tag");
        });

        prefPower.setOnClickListener((v)->{
            boolean checked = ((CompoundButton) v).isChecked();

            if (checked) setUnchecked(true);
            else setChecked(true);

            hCallback.updateInternalProperties(current.getParentPos(), !checked);
        });


        Calendar calendar = Calendar.getInstance();

        if (!current.getPrefBelongsToAdd()) {

        }
        else{
            slU.fr("for addAlarm");

        }

    }

    static ChildViewHolder create(ViewGroup parent, FAHCallback hCallback) {
        //В данном случае, когда мы указиваем родительскую ViewGroup в качестве источника LayoutParams, эти самые LP передаются в View при наполнении
        //Конкретно - это те, которые указаны в материнской LinearLayout, ширина и высота всей разметки.
        //Если не передать эти LP, то RLM подхватит LP по умолчанию
        Rect rect = new Rect(parent.getLeft(), parent.getTop(), parent.getRight(), parent.getBottom());
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.recycler_view_item, parent, false);
                return new ChildViewHolder(itemView, hCallback, rect);
    }

    @Override
    public boolean onLongClick(View v) {
//        sl.i("notify delete click: " + getAdapterPosition());
        if (getAdapterPosition()==-1) {
//            sl.sp( "onClick: ERROR");
            return false;
        }
        hCallback.deleteItem(getAdapterPosition());
        return true;
    }

    @Override
    public void onClick(View v) {
        slU.i( "notify click: " + getAdapterPosition());
        if (getAdapterPosition()==-1) {
            slU.sp( "onClick: ERROR");
            return;
        }

        hCallback.notifyBaseClick(getAdapterPosition());
        //Receiving drawable's state from handler
        switch(hCallback.getTimeWindowState(getAdapterPosition())){
            case (0):
                setParentEnvoy();
                break;

            case (1): timeWindow.setBackground(hCallback.getDrawable(R.drawable.rv_time_window, true)); break;

            case (-1): timeWindow.setBackground(hCallback.getDrawable(R.drawable.rv_time_window, false)); break;
        }
    }
}
