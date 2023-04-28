 package com.vova9110.bloodbath;

 import static com.vova9110.bloodbath.MainViewModel.PREFERENCES_NAME;

 import android.app.AlarmManager;
 import android.app.PendingIntent;
 import android.content.Context;
 import android.content.Intent;
 import android.content.SharedPreferences;
 import android.os.Bundle;
 import android.view.View;
 import android.widget.Button;
 import android.widget.ImageView;
 import android.widget.Toast;

 import androidx.appcompat.app.AppCompatActivity;
 import androidx.appcompat.app.AppCompatDelegate;
 import androidx.lifecycle.ViewModelProvider;
 import androidx.recyclerview.widget.RecyclerView;

 import com.google.android.material.floatingactionbutton.FloatingActionButton;
 import com.vova9110.bloodbath.alarmScreenBackground.ActivenessDetectionService;
 import com.vova9110.bloodbath.alarmScreenBackground.AlarmExecutionDispatch;
 import com.vova9110.bloodbath.alarmScreenBackground.AlarmRepo;
 import com.vova9110.bloodbath.alarmScreenBackground.BackgroundUtils;
 import com.vova9110.bloodbath.alarmScreenBackground.FiringControlService;
 import com.vova9110.bloodbath.alarmsUI.FreeAlarmsHandler;
 import com.vova9110.bloodbath.alarmScreenBackground.SubInfo;
 import com.vova9110.bloodbath.database.Alarm;
 import com.vova9110.bloodbath.recyclerView.RowLayoutManager;

 import java.text.SimpleDateFormat;
 import java.util.Calendar;
 import java.util.Date;
 import java.util.Locale;
 import java.util.concurrent.TimeUnit;

 import javax.inject.Inject;

public class MainActivity extends AppCompatActivity{
    private SplitLogger sl = new SplitLogger();

    public static final int NEW_TASK_ACTIVITY_REQUEST_CODE = 1;
    public static final int FILL_DB = 3;
    public static final int CLEAR_DB = 4;
    private MainViewModel mMainViewModel;
    @Inject
    public FreeAlarmsHandler mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);

//        Date firstDate = new Date();
//        Date secDate = firstDate;
//        secDate.setTime(secDate.getTime() + 10000);
//        firstDate = null;
//        SplitLogger.i(secDate);
//        SplitLogger.i(firstDate);

        // Get a new or existing ViewModel from the ViewModelProvider.
        mMainViewModel = new ViewModelProvider(this).get(MainViewModel.class);
        ((MyApp) getApplicationContext()).component.inject(this);
        RecyclerView recyclerView = findViewById(R.id.recyclerview);
        recyclerView.setAdapter(mHandler.pollForList());
        recyclerView.setLayoutManager(new RowLayoutManager(this, mHandler));
        mHandler.setRecycler(recyclerView);

        ImageView imageView = findViewById(R.id.imageView);
        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(view -> {
            Intent oldIntent = new Intent(MainActivity.this, NewTaskActivity.class);
            startActivityForResult(oldIntent, NEW_TASK_ACTIVITY_REQUEST_CODE);



//            Calendar cal = Calendar.getInstance();
//            cal.roll(Calendar.DATE, true);
//            cal.set (Calendar.HOUR_OF_DAY, 6);
//            cal.set (Calendar.MINUTE, 0);
//            long time = cal.getTimeInMillis();
//
//            String id = "0800";
//            AlarmManager manager = (AlarmManager) this.getApplicationContext().getSystemService(Context.ALARM_SERVICE);
//            Intent intent = new Intent(this.getApplicationContext(), FiringControlService.class);
//            intent.setAction(id);
//            intent.putExtra("interlayer", true);
//            intent.setFlags(Intent.FLAG_RECEIVER_FOREGROUND);
//
//            String composedID = String.valueOf(56);
//            composedID+=id;
//
//            PendingIntent pending = PendingIntent.getForegroundService(this.getApplicationContext(), Integer.parseInt(composedID), intent, PendingIntent.FLAG_IMMUTABLE);
//            manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pending);
//
//            sl.fst("Scheduling exact with id *" + composedID + "* and state *" + Alarm.STATE_PREPARE + "* on " + new SimpleDateFormat("*d,EEE,HH:mm*", Locale.getDefault()).format(new Date(time)));
        });
        fab.setOnLongClickListener(v -> {
            if (imageView.getVisibility() == View.INVISIBLE)
                imageView.setVisibility(View.VISIBLE);
            else imageView.setVisibility(View.INVISIBLE);
            return true;
        });

        //ONLY TEMP THING!!!
        findViewById(R.id.buttonRepeat)
                .setOnClickListener(view-> {
                    mHandler.repeatButton = !mHandler.repeatButton;
                    view.setBackgroundTintList(this.getColorStateList(R.color.test_color_list));
                });
        findViewById(R.id.buttonActiv)
                .setOnClickListener(view-> {
                    mHandler.activeButton = !mHandler.activeButton;
                    view.setBackgroundTintList(this.getColorStateList(R.color.test_color_list));
                });
        findViewById(R.id.buttonPower)
                .setOnClickListener(view-> {
                    mHandler.createUsual();
                });
        findViewById(R.id.buttonPower)
                .setOnLongClickListener(view-> {
                    mHandler.deleteUsual();
                    return true;
                });

        Button startFiringButton = findViewById(R.id.button3);
        startFiringButton.setOnClickListener(view -> {
            AlarmRepo repo = ((MyApp) getApplicationContext()).component.getRepo();
            Alarm current = new Alarm(0,0, true, Alarm.STATE_FIRE);
            repo.deleteOne(current);
            current.setTriggerTime(new Date());
            repo.testInsert(current);

            Intent intent = new Intent(getApplicationContext(), AlarmExecutionDispatch.class);
            intent.setAction(current.getId());

            getApplicationContext().sendBroadcast(intent);
        });
        startFiringButton.setOnLongClickListener(view-> {
            new Thread(()->{
                try {
                    TimeUnit.SECONDS.sleep(5);
                } catch (InterruptedException ignored) {}

                AlarmRepo repo = ((MyApp) getApplicationContext()).component.getRepo();
                Alarm current = new Alarm(0,0, true, Alarm.STATE_FIRE);
                repo.deleteOne(current);
                current.setTriggerTime(new Date());
                repo.testInsert(current);

                Intent intent = new Intent(getApplicationContext(), AlarmExecutionDispatch.class);
                intent.setAction(current.getId());

                getApplicationContext().sendBroadcast(intent);
            }).start();
            return true;
        });

        Button startDetectionButton = findViewById(R.id.button2);
        startDetectionButton.setOnClickListener(view-> {
            AlarmRepo repo = ((MyApp) getApplicationContext()).component.getRepo();
            Alarm current = new Alarm(0,0, true, Alarm.STATE_FIRE);
            repo.deleteOne(current);
            current.setTriggerTime(new Date());
            repo.testInsert(current);
            Intent intent = new Intent(getApplicationContext(), ActivenessDetectionService.class);
            intent.putExtra("info", repo.getTimesInfo(current.getId()));

            getApplicationContext().startForegroundService(intent);
        });
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == NEW_TASK_ACTIVITY_REQUEST_CODE && resultCode == RESULT_OK) {
            //AlarmActivity alarm = new AlarmActivity(data.getStringExtra(NewTaskActivity.EXTRA_REPLY));
            //mHandler.insert(alarm);
        }
        else if (requestCode == NEW_TASK_ACTIVITY_REQUEST_CODE && resultCode == FILL_DB) mHandler.fill();
        else if (requestCode == NEW_TASK_ACTIVITY_REQUEST_CODE && resultCode == CLEAR_DB) mHandler.clear();
        else {
            Toast.makeText(
                    getApplicationContext(),
                    R.string.empty_not_saved,
                    Toast.LENGTH_LONG).show();
        }
    }

     @Override
    protected void onResume() {
        sl.en();

        SharedPreferences prefs = getApplicationContext().getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();

        editor.putBoolean("appExitedProperly", false);
        editor.apply();

//        mHandler.onResumeUpdate();
        super.onResume();
    }

     @Override
     protected void onStop() {
         sl.ex();
         SharedPreferences prefs = getApplicationContext().getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
         SharedPreferences.Editor editor = prefs.edit();

         editor.putBoolean("appExitedProperly", true);
         editor.apply();
         super.onStop();
     }

}