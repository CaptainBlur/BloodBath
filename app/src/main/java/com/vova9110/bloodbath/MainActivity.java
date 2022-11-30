 package com.vova9110.bloodbath;

 import static com.vova9110.bloodbath.MainViewModel.PREFERENCES_NAME;

 import android.annotation.SuppressLint;
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
 import androidx.lifecycle.Observer;
 import androidx.lifecycle.ViewModelProvider;
 import androidx.recyclerview.widget.RecyclerView;

 import com.elvishew.xlog.LogLevel;
 import com.google.android.material.floatingactionbutton.FloatingActionButton;
 import com.vova9110.bloodbath.AlarmScreenBackground.ActivenessDetectionService;
 import com.vova9110.bloodbath.Database.TimeSInfo;
 import com.vova9110.bloodbath.Database.Alarm;
 import com.vova9110.bloodbath.RecyclerView.AlarmListAdapter;
 import com.vova9110.bloodbath.RecyclerView.RowLayoutManager;

 import java.io.Console;
 import java.util.List;
 import java.util.logging.ConsoleHandler;
 import java.util.logging.Filter;
 import java.util.logging.Formatter;
 import java.util.logging.Handler;
 import java.util.logging.Level;
 import java.util.logging.LogRecord;
 import java.util.logging.Logger;
 import java.util.logging.SimpleFormatter;

 import javax.inject.Inject;

 public class MainActivity extends AppCompatActivity{
     private final static String TAG = "TAG_MA";
     private static SplitLogger.SLCompanion sl;

    public static final int NEW_TASK_ACTIVITY_REQUEST_CODE = 1;
    public static final int FILL_DB = 3;
    public static final int CLEAR_DB = 4;
    private MainViewModel mMainViewModel;
    private static AlarmListAdapter adapter;
    LDObserver ldObserver;
    @Inject
    public FreeAlarmsHandler mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);

        // Get a new or existing ViewModel from the ViewModelProvider.
        mMainViewModel = new ViewModelProvider(this).get(MainViewModel.class);
        mMainViewModel.getComponent().inject(this);
        RecyclerView recyclerView = findViewById(R.id.recyclerview);
        adapter = new AlarmListAdapter(mHandler);
        recyclerView.setAdapter(adapter);
        sl = new SplitLogger.SLCompanion(false, this.getClass().getName(), false);

        //recyclerView.setLayoutManager(new GridLayoutManager(this,1, RecyclerView.VERTICAL, false));
        recyclerView.setLayoutManager(new RowLayoutManager(this, mHandler));
        ldObserver = new LDObserver();

        mHandler.getInitialList().observe(this, ldObserver);
        mHandler.pass(recyclerView, adapter, ldObserver, getApplicationContext());
        //mHandler.fill();

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

        Button detectionButton = findViewById(R.id.button2);
//        detectionButton.setOnClickListener(view -> getApplicationContext().startService());
        detectionButton.setOnLongClickListener(view ->{
            getApplicationContext().startService(new Intent(getApplicationContext(), ActivenessDetectionService.class).putExtra("stopCall", true));
            return true;
        });
        detectionButton.setOnClickListener(view -> {
            Intent intent = new Intent(getApplicationContext(), ActivenessDetectionService.class).putExtra("info",
                    new TimeSInfo(null,
                            null,
                            50,
                            1.5f,
                            0,
                            5,
                            40,
                            90,
                            8 * 60,
                            15 * 60
                    ));
            intent.putExtra("testMode", false);
            intent.putExtra("fileOutput", true);
            getApplicationContext().startForegroundService(intent);
        });

        Button startActivityButton = findViewById(R.id.button3);
        startActivityButton.setOnClickListener(view -> mHandler.addTest(1));
        startActivityButton.setOnLongClickListener(view -> {
            mHandler.addTest(7);
            return true;
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

    //Выглядит как говно, я знаю, чекни описание во FreeAlarmsHandler
    static class LDObserver implements Observer<List<Alarm>> {
        @SuppressLint("NotifyDataSetChanged")
        @Override
        public void onChanged(List<Alarm> alarms) {
            alarms.sort((o1, o2) -> {
                if (o1.getHour() != o2.getHour()) return o1.getHour() - o2.getHour();
                else return o1.getMinute() - o2.getMinute();
            });
            adapter.submitList(alarms);
            adapter.notifyDataSetChanged();
            sl.fr("Time to initial layout! List size: " + alarms.size());
        }
    }

     @Override
     protected void onResume() {
        sl.i("Resuming");
        mHandler.onResumeUpdate();
        super.onResume();
     }

     @Override
     protected void onStop() {
         SharedPreferences prefs = getApplicationContext().getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
         SharedPreferences.Editor editor = prefs.edit();

         editor.putBoolean("appExitedProperly", true);
         editor.apply();
         sl.i("Intentional exit detected!");
         super.onStop();
     }

 }