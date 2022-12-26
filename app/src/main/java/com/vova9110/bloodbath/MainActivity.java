 package com.vova9110.bloodbath;

 import static com.vova9110.bloodbath.MainViewModel.PREFERENCES_NAME;

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
 import com.vova9110.bloodbath.alarmsUI.FreeAlarmsHandler;
 import com.vova9110.bloodbath.alarmScreenBackground.SubInfo;
 import com.vova9110.bloodbath.database.Alarm;
 import com.vova9110.bloodbath.recyclerView.RowLayoutManager;

 import java.util.Date;
 import java.util.concurrent.TimeUnit;

 import javax.inject.Inject;

public class MainActivity extends AppCompatActivity{
    private SplitLogger sl;

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
            Intent intent = new Intent(MainActivity.this, NewTaskActivity.class);
            startActivityForResult(intent, NEW_TASK_ACTIVITY_REQUEST_CODE);
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