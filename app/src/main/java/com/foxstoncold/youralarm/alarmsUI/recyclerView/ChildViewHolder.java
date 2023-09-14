package com.foxstoncold.youralarm.alarmsUI.recyclerView;

import static com.google.android.material.timepicker.TimeFormat.CLOCK_24H;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.TimeInterpolator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Rect;
import android.graphics.drawable.AnimationDrawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.view.animation.DecelerateInterpolator;
import android.widget.CompoundButton;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;

import com.foxstoncold.youralarm.alarmsUI.InterfaceUtils;
import com.google.android.material.timepicker.MaterialTimePicker;
import com.foxstoncold.youralarm.MainViewModel;
import com.foxstoncold.youralarm.SplitLoggerUI;
import com.foxstoncold.youralarm.alarmsUI.AdjustableCompoundButton;
import com.foxstoncold.youralarm.alarmsUI.AdjustableImageView;
import com.foxstoncold.youralarm.alarmsUI.AdjustableTextView;
import com.foxstoncold.youralarm.alarmsUI.FAHCallback;
import com.foxstoncold.youralarm.alarmsUI.MainDrawables;
import com.foxstoncold.youralarm.database.Alarm;
import com.foxstoncold.youralarm.R;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Locale;

public class ChildViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener{
    private SplitLoggerUI slU;
    private final View view;
    private final FAHCallback hCallback;
    private final MainDrawables drawables;
    private final Rect globalRect;
    private final Rect frameRect;
    private final Rect prefTimeRect;
    private final ConstraintLayout prefConstraintLayout;
    private final AdjustableCompoundButton timeWindow;
    private final AdjustableImageView prefFrame;
    private final AdjustableTextView prefTime;
    private final AdjustableCompoundButton prefPower;
    private final AdjustableCompoundButton prefMusic;
    private final AdjustableCompoundButton prefVibration;
    private final AdjustableCompoundButton prefPreliminary;
    private final AdjustableCompoundButton prefActiveness;
    private final AdjustableCompoundButton prefRepeat;
    private final LinearLayout prefWeekdays;

    private final LinkedList<SpecialCompoundButton> weekdayButtons = new LinkedList<>();
    private boolean[] savedWeekdays;


    private ChildViewHolder(View view, FAHCallback c, Rect rect) {
        super(view);
        this.view = view;
        this.hCallback = c;
        drawables = hCallback.getDrawables();
        globalRect = rect;

        timeWindow = view.findViewById(R.id.rv_time_window);
        timeWindow.makeAdjustments(globalRect);


        prefConstraintLayout = view.findViewById(R.id.pref_consistent_views_layout);

        prefFrame = view.findViewById(R.id.rv_pref_frame);
        frameRect = prefFrame.makeAdjustments(globalRect);
//        prefFrame.setAnimation();

        prefTime = view.findViewById(R.id.rv_pref_time_window);
        prefTimeRect = prefTime.makeAdjustments(globalRect);

        prefPower = view.findViewById(R.id.rv_pref_power);
        prefPower.makeAdjustments(frameRect);

        prefMusic = view.findViewById(R.id.rv_pref_music);
        prefMusic.makeAdjustments(globalRect);
        prefMusic.setForeground(drawables.get_neutral_drawable(MainDrawables.rv_pref_music));

        prefVibration = view.findViewById(R.id.rv_pref_vibration);
        prefVibration.makeAdjustments(globalRect);
        prefVibration.setForeground(drawables.get_checked_drawable(MainDrawables.rv_pref_vibration));

        prefPreliminary = view.findViewById(R.id.rv_pref_preliminary);
        prefPreliminary.makeAdjustments(globalRect);
        prefPreliminary.setForeground(drawables.get_checked_drawable(MainDrawables.rv_pref_preliminary));

        prefActiveness = view.findViewById(R.id.rv_pref_activeness);
        prefActiveness.makeAdjustments(globalRect);
        prefActiveness.setForeground(drawables.get_checked_drawable(MainDrawables.rv_pref_activeness));

        prefRepeat = view.findViewById(R.id.rv_pref_repeat);
        prefRepeat.makeAmends(drawables.getResolvedRectangle(MainDrawables.rv_pref_repeat));
        prefRepeat.setForeground(drawables.get_checked_drawable(MainDrawables.rv_pref_repeat));

        prefWeekdays = view.findViewById(R.id.rv_pref_weekdays_container);
    }

    private void setUnchecked(boolean startAnimation){
        slU.fr("Pref set OFF");

        Resources res = view.getResources();
        float unlitTextM = res.getFraction(R.fraction.rv_pref_time_text_unlit_multiplier, 1,1);
        prefTime.setTextSize(TypedValue.COMPLEX_UNIT_PX, prefTimeRect.height()*unlitTextM);
        prefTime.setTextColor(view.getContext().getColor(R.color.mild_greyscaleLight));
        prefTime.setTypeface(res.getFont(R.font.avenir_lt_55_roman));

        prefPower.setChecked(true);

        setupAnimatedDrawables(false, startAnimation);

    }
    private void setChecked(boolean startAnimation){
        slU.fr("Pref set ON");

        Resources res = view.getResources();
        float litTextM = res.getFraction(R.fraction.rv_pref_time_text_lit_multiplier, 1,1);
        prefTime.setTextSize(TypedValue.COMPLEX_UNIT_PX, prefTimeRect.height()*litTextM);
        prefTime.setTextColor(view.getContext().getColor(R.color.mild_shadyDist));
        prefTime.setTypeface(res.getFont(R.font.avenir_lt_65_medium));

        prefPower.setChecked(false);

        setupAnimatedDrawables(true, startAnimation);
    }


    private void setupAnimatedDrawables(boolean checked, boolean startAnimation){

        AnimationDrawable frameDrawable = null;
        AnimationDrawable powerDrawable = (AnimationDrawable)
                ((checked) ? drawables.get_checked_drawable(MainDrawables.rv_pref_power) :
                        drawables.get_unchecked_drawable(MainDrawables.rv_pref_power));
        assert hCallback.getPrefAlignment()!=null;

        switch (hCallback.getPrefAlignment()) {
            case (1) -> {
                frameDrawable = (AnimationDrawable)
                ((checked) ? drawables.get_checked_drawable(MainDrawables.rv_pref_frame_right) :
                        drawables.get_unchecked_drawable(MainDrawables.rv_pref_frame_right));
                prefFrame.setRotationY(180);
            }
            case (0) ->
                    frameDrawable = (AnimationDrawable)
                ((checked) ? drawables.get_checked_drawable(MainDrawables.rv_pref_frame_center) :
                        drawables.get_unchecked_drawable(MainDrawables.rv_pref_frame_center));
            case (-1) -> {
                frameDrawable = (AnimationDrawable)
                ((checked) ? drawables.get_checked_drawable(MainDrawables.rv_pref_frame_right) :
                        drawables.get_unchecked_drawable(MainDrawables.rv_pref_frame_right));
                prefFrame.setRotationY(0);
            }
        }
//        assert frameDrawable != null;


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

    //I thought it would be great if every single button could set
    // it's own appearance based on a saved position in the array
    private class SpecialCompoundButton extends AdjustableCompoundButton {
        public SpecialCompoundButton(@NonNull Context context, @Nullable AttributeSet attrs) {
            super(context, attrs);
            Rect buttonRect = drawables.getResolvedRectangle(MainDrawables.rv_pref_weekday_button);
            loweredMargin = Math.round(context.getResources().getFraction(R.fraction.rv_pref_weekday_container_lowered_margin, buttonRect.height(),1));
        }

        private int arrayPos;
        private final int loweredMargin;
        //This is actually useful when we need to change "checked" state
        public void setState(int pos, boolean checked){
            arrayPos = pos;
            setState(checked);
            this.setChecked(checked);
        }
        public void setState(){
            setState(this.isChecked());
        }
        private void setState(boolean checked){
            Resources res = this.getContext().getResources();

            if (checked){
                this.setForeground((prefRepeat.isChecked()) ? drawables.get_unchecked_drawable(MainDrawables.rv_pref_weekday_button) : null);
                this.setTypeface(res.getFont(R.font.avenir_next_regular));
                savedWeekdays[arrayPos] = false;
                this.setEnabled(prefRepeat.isChecked());
            }
            else{
                this.setForeground((prefRepeat.isChecked()) ? drawables.get_checked_drawable(MainDrawables.rv_pref_weekday_button) : null);
                this.setTypeface(res.getFont(R.font.avenir_next_medium));
                savedWeekdays[arrayPos] = true;
                this.setEnabled(prefRepeat.isChecked());
            }

            if (prefRepeat.isChecked()) this.setPadding(0,0,0,0);
            else this.setPadding(0,loweredMargin,0,0);
        }
    }

    private void setupRepeatContainer(Alarm alarm){
        updateRepeatContainerAppearance();

        prefRepeat.setOnClickListener(v ->{
            AdjustableCompoundButton current = (AdjustableCompoundButton) v;
            current.setChecked(current.isChecked());

            for (SpecialCompoundButton button : weekdayButtons) button.setState();
            updateRepeatContainerAppearance();

            slU.i("repeat state updated: " + alarm.getParentPos());
            alarm.setRepeatable(current.isChecked());
            hCallback.updateInternalProperties(alarm);
        });

        prefRepeat.setOnLongClickListener(v ->{
            AdjustableCompoundButton current = (AdjustableCompoundButton) v;
            if (!current.isChecked()) return true;

            int count = 0;
            for (boolean item : savedWeekdays) if (item) count++;

            Arrays.fill(savedWeekdays, count == 0);

            for (SpecialCompoundButton button : weekdayButtons){
                button.setChecked(count != 0);
                button.setState();
            }
            updateRepeatContainerAppearance();

            slU.i("weekdays updated together: " + alarm.getParentPos());
            alarm.setWeekdays(savedWeekdays);
            hCallback.updateInternalProperties(alarm);
            return true;
        });

        Resources res = view.getContext().getResources();
        Rect buttonRect = drawables.getResolvedRectangle(MainDrawables.rv_pref_weekday_button);

        ViewGroup.LayoutParams lp = prefWeekdays.getLayoutParams();
        lp.height = Math.round(res.getFraction(R.fraction.rv_pref_weekday_container_height, buttonRect.height(),1));
        prefWeekdays.setLayoutParams(lp);


        final boolean repeatChecked = prefRepeat.isChecked();
        final boolean shiftedWeek = view.getContext().getSharedPreferences(MainViewModel.USER_SETTINGS_SP, Context.MODE_PRIVATE)
                .getBoolean("shiftedWeekdaysOrder", false);

        Rect commonRect = drawables.getResolvedRectangle(MainDrawables.rv_pref_weekday_button);
        ViewGroup.LayoutParams commonLP = new ViewGroup.LayoutParams(commonRect.width(), commonRect.height());

        float textSizeM = res.getFraction(R.fraction.rv_pref_weekday_text_height_toWidth, 1,1);
        float commonTextSize = commonRect.width() * textSizeM;

        //Inflating List with Views
        for (int i = 0; i < savedWeekdays.length; i++){
            SpecialCompoundButton currentButton = new SpecialCompoundButton(view.getContext(), null);

            currentButton.setForegroundGravity(Gravity.CENTER);
            currentButton.setTextSize(TypedValue.COMPLEX_UNIT_PX, commonTextSize);
            currentButton.setGravity(Gravity.CENTER_HORIZONTAL);

            switch (i){
                case 0 -> currentButton.setText("M");
                case 1 -> currentButton.setText("Tu");
                case 2 -> currentButton.setText("W");
                case 3 -> currentButton.setText("Th");
                case 4 -> currentButton.setText("F");
                case 5 -> currentButton.setText("S");
                case 6 -> currentButton.setText("Su");
            }

//            int drawableID = MainDrawables.rv_pref_weekday_button;
//            if (repeatChecked) currentButton.setForeground (
//                (savedWeekdays[i]) ? drawables.get_checked_drawable(drawableID) : drawables.get_unchecked_drawable(drawableID));
//            else currentButton.setForeground(drawables.get_neutral_drawable(drawableID));
//
//            currentButton.setTypeface(
//                (savedWeekdays[i]) ? res.getFont(R.font.avenir_next_medium) : res.getFont(R.font.avenir_next_regular));
            currentButton.setEnabled(repeatChecked);
            currentButton.setState(i, !savedWeekdays[i]);

            final int arrayPos = i;
            currentButton.setOnClickListener((v)->{
                SpecialCompoundButton current = (SpecialCompoundButton) v;

                current.setState(arrayPos, current.isChecked());
                updateRepeatContainerAppearance();

                slU.i("^" + arrayPos + "^ weekday updated: " + alarm.getParentPos());
                alarm.setWeekdays(savedWeekdays);
                hCallback.updateInternalProperties(alarm);
            });

            weekdayButtons.add(currentButton);
        }

        if (shiftedWeek) Collections.rotate(weekdayButtons, 1);
        for (SpecialCompoundButton view : weekdayButtons) prefWeekdays.addView(view, commonLP);
    }
    private void updateRepeatContainerAppearance() {
        final boolean repeatChecked = prefRepeat.isChecked();
        int count = 0;
        for (boolean item : savedWeekdays) if (item) count++;

        if (count==0){
            prefRepeat.setForeground(drawables.get_unchecked_drawable(
                    (repeatChecked) ? MainDrawables.rv_pref_repeat : MainDrawables.rv_pref_repeat_disabled));
        }
        else if (count==7){
            prefRepeat.setForeground(drawables.get_checked_drawable(
                    (repeatChecked) ? MainDrawables.rv_pref_repeat : MainDrawables.rv_pref_repeat_disabled));
        }
        else{
            prefRepeat.setForeground(drawables.get_neutral_drawable(
                    (repeatChecked) ? MainDrawables.rv_pref_repeat : MainDrawables.rv_pref_repeat_disabled));
        }
    }


    private void setBindVisibility(){
        timeWindow.setVisibility(View.VISIBLE);

        prefConstraintLayout.setVisibility(View.GONE);
        prefFrame.setVisibility(View.GONE);
        prefWeekdays.setVisibility(View.GONE);
    }
    private void setBindPrefVisibility(){
        timeWindow.setVisibility(View.GONE);

        prefConstraintLayout.setVisibility(View.VISIBLE);
        prefFrame.setVisibility(View.VISIBLE);
        prefWeekdays.setVisibility(View.VISIBLE);
    }


    public void bind(Alarm current){
        prefWeekdays.removeAllViews();
        weekdayButtons.clear();
        savedWeekdays = new boolean[7];

        switch (getItemViewType()) {
            case (AlarmListAdapter.TYPE_TIME) -> bindTime(current.component1(), current.component2());
            case (AlarmListAdapter.TYPE_PREF) -> bindPref(current);
            case (AlarmListAdapter.TYPE_ADD) -> bindAddAlarm();
        }
    }
    /*
    Binding new time window also occurs when mother view is restored
    after pref's removal, or when the internal properties are updated
     */
    public void bindTime(int hour, int minute) {
        setBindVisibility();

        timeWindow.setText(String.format(Locale.ENGLISH,"%02d:%02d", hour, minute));

        switch (hCallback.getTimeWindowState(getAdapterPosition())) {
            case (0) -> timeWindow.setAlpha(0f);
            case (1) -> {
                timeWindow.setBackground(drawables.get_checked_drawable(MainDrawables.rv_time_window));
                timeWindow.setTextColor(view.getContext().getColor(R.color.mild_shadyDist));
            }
            case (-1) -> {
                timeWindow.setBackground(drawables.get_unchecked_drawable(MainDrawables.rv_time_window));
                timeWindow.setTextColor(view.getContext().getColor(R.color.mild_shady));
            }
        }
//        slU.fst("BIND " + minute);
        timeWindow.setOnClickListener(this);
        timeWindow.setOnLongClickListener(this);

    }

    public void bindAddAlarm() {
//        hourPicker.setVisibility(View.GONE); minutePicker.setVisibility(View.GONE); switcher.setVisibility(View.GONE); FAB.setVisibility(View.GONE);
        setBindVisibility();

        timeWindow.setOnClickListener(this);
        timeWindow.setText(null);
        timeWindow.setBackground(drawables.get_neutral_drawable(MainDrawables.rv_time_window));
        timeWindow.setForegroundGravity(Gravity.CENTER);
        timeWindow.setForeground(drawables.get_neutral_drawable(MainDrawables.plus));

        if (!hCallback.getAddAlarmParentVisibility()) timeWindow.setAlpha(0f);
    }

    public void bindPref(Alarm current){
        slU.fr("BINDING PREF: " + getAdapterPosition());
//        hourPicker.setVisibility(View.VISIBLE); minutePicker.setVisibility(View.VISIBLE); switcher.setVisibility(View.VISIBLE); FAB.setVisibility(View.VISIBLE);
        Alarm alarm = current.clone();
        setBindPrefVisibility();
        if (!current.component3()) setUnchecked(false);
        else setChecked(false);
        int h;
        int m;

        if (!current.getPrefBelongsToAdd()) {
            ConstraintLayout.LayoutParams lp = (ConstraintLayout.LayoutParams)prefTime.getLayoutParams();
            lp.verticalBias = view.getResources().getFraction(R.fraction.rv_pref_time_verticalBias,1,1);

            prefTime.setLayoutParams(lp);
            h = current.component1();
            m = current.component2();

            prefPower.setVisibility(View.VISIBLE);
            prefPower.setOnClickListener((v) -> {
                boolean checked = ((CompoundButton) v).isChecked();

                if (checked) setUnchecked(true);
                else setChecked(true);

                slU.i("power state updated: " + alarm.getParentPos());
                alarm.setEnabled(!checked);
                hCallback.updateInternalProperties(alarm);
            });
        }
        else{
            slU.fr("FOR ADD");

            ConstraintLayout.LayoutParams lp = (ConstraintLayout.LayoutParams)prefTime.getLayoutParams();
            lp.verticalBias = view.getResources().getFraction(R.fraction.rv_pref_time_verticalBias_centered,1,1);
            prefTime.setLayoutParams(lp);
            alarm.setPrefBelongsToAdd(true);

            Calendar c = Calendar.getInstance();
            h = c.get(Calendar.HOUR_OF_DAY);
            m = c.get(Calendar.MINUTE);
            prefTime.setText(String.format(Locale.ENGLISH, "%02d:%02d", h, m));

            prefPower.setVisibility(View.GONE);
        }

        prefTime.setText(String.format(Locale.ENGLISH, "%02d:%02d",h, m));
        prefTime.setOnClickListener((v0) -> {
            MaterialTimePicker picker = new MaterialTimePicker.Builder()
                    .setHour(h)
                    .setMinute(m)
                    .setInputMode(MaterialTimePicker.INPUT_MODE_CLOCK)
                    .setTimeFormat(CLOCK_24H)
                    .build();

            picker.addOnPositiveButtonClickListener((v1) -> {
                if (!current.getPrefBelongsToAdd()) hCallback.changeItemTime(picker.getHour(), picker.getMinute());
                else{
                    alarm.setHour(picker.getHour());
                    alarm.setMinute(picker.getMinute());
                    hCallback.addItem(alarm);
                }
            });

            picker.show(hCallback.getFragmentManager(), "tag");
        });

        prefRepeat.setChecked(current.smartGetRepeatable());
//        savedWeekdays = new boolean[]{true, false, true, false, true, false, true};
        savedWeekdays = current.getWeekdays();
        setupRepeatContainer(alarm);

        prefVibration.setChecked(current.getVibrate());
        if (prefVibration.isChecked()) prefVibration.setForeground(drawables.get_checked_drawable(MainDrawables.rv_pref_vibration));
        else prefVibration.setForeground(drawables.get_unchecked_drawable(MainDrawables.rv_pref_vibration));
        prefVibration.setOnClickListener(v ->{
            AdjustableCompoundButton view = (AdjustableCompoundButton) v;
            view.setChecked(view.isChecked());

            if (view.isChecked()) view.setForeground(drawables.get_checked_drawable(MainDrawables.rv_pref_vibration));
            else view.setForeground(drawables.get_unchecked_drawable(MainDrawables.rv_pref_vibration));

            slU.i("vibration updated: " + alarm.getParentPos());
            alarm.setVibrate(view.isChecked());
            hCallback.updateInternalProperties(alarm);
        });

        prefPreliminary.setChecked(current.getPreliminary());
        if (prefPreliminary.isChecked()) prefPreliminary.setForeground(drawables.get_checked_drawable(MainDrawables.rv_pref_preliminary));
        else prefPreliminary.setForeground(drawables.get_unchecked_drawable(MainDrawables.rv_pref_preliminary));
        prefPreliminary.setOnClickListener(v ->{
            AdjustableCompoundButton view = (AdjustableCompoundButton) v;
            view.setChecked(view.isChecked());

            if (view.isChecked()) view.setForeground(drawables.get_checked_drawable(MainDrawables.rv_pref_preliminary));
            else view.setForeground(drawables.get_unchecked_drawable(MainDrawables.rv_pref_preliminary));

            slU.i("preliminary updated: " + alarm.getParentPos());
            alarm.setPreliminary(view.isChecked());
            hCallback.updateInternalProperties(alarm);
        });

        prefActiveness.setChecked(InterfaceUtils.Companion.checkActivityPermission(prefActiveness.getContext()) &&
                InterfaceUtils.Companion.checkStepSensors(prefActiveness.getContext()) &&
                current.getDetection());
        if (prefActiveness.isChecked()) prefActiveness.setForeground(drawables.get_checked_drawable(MainDrawables.rv_pref_activeness));
        else prefActiveness.setForeground(drawables.get_unchecked_drawable(MainDrawables.rv_pref_activeness));
        prefActiveness.setOnClickListener(v ->{
            AdjustableCompoundButton view = (AdjustableCompoundButton) v;

            if (InterfaceUtils.Companion.checkActivityPermission(v.getContext()) &&
                    InterfaceUtils.Companion.checkStepSensors(v.getContext())) {
                view.setChecked(view.isChecked());

                if (view.isChecked())
                    view.setForeground(drawables.get_checked_drawable(MainDrawables.rv_pref_activeness));
                else
                    view.setForeground(drawables.get_unchecked_drawable(MainDrawables.rv_pref_activeness));

                slU.i("detection updated: " + alarm.getParentPos());
                alarm.setDetection(view.isChecked());
                hCallback.updateInternalProperties(alarm);
            }
            else view.setChecked(false);
        });
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
        /*
        Instead of asking the handler about 'parent's duty' of a current view,
        we lay it down to RLM along with telling us all about animation stuff
         */
        switch (hCallback.getTimeWindowState(getAdapterPosition())) {
            case (1) -> timeWindow.setBackground(drawables.get_checked_drawable(MainDrawables.rv_time_window));
            case (-1) -> timeWindow.setBackground(drawables.get_unchecked_drawable(MainDrawables.rv_time_window));
        }
    }

    public void notifyBaseClick(){
        hCallback.notifyBaseClick(getAdapterPosition());
    }

    public void setTimeWindowVisibility(float alpha){
        timeWindow.setAlpha(alpha);
    }
    public void startTimeWindowAlphaAnimation(long duration, float toValue){
        TimeInterpolator interpolator = new DecelerateInterpolator();
        InterfaceUtils.Companion.startAlphaAnimation(timeWindow, toValue, duration, interpolator, null);
    }

    public void hidePrefNLaunchHandler(long duration){
        slU.fr("starting pref's animation");
        TimeInterpolator interpolator = new DecelerateInterpolator();
        AnimatorListenerAdapter listener = new AnimatorListenerAdapter(){
            @Override
            public void onAnimationEnd(Animator animation) {
                hCallback.launchHidePref();
            }
        };
        InterfaceUtils.Companion.startAlphaAnimation(view, 0f, duration, interpolator, listener);
    }

    public void requestParentUpdate(){
        hCallback.updateParent();
    }

    public void animatePowerChange(boolean enabled){
        if (enabled) setChecked(true);
        else setUnchecked(true);
    }
}
