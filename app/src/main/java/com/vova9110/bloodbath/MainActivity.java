 package com.vova9110.bloodbath;

 import android.content.Intent;
 import android.os.Bundle;
 import android.util.Log;
 import android.view.View;
 import android.widget.Button;
 import android.widget.ImageView;
 import android.widget.Toast;

 import androidx.appcompat.app.AppCompatActivity;
 import androidx.appcompat.app.AppCompatDelegate;
 import androidx.lifecycle.Observer;
 import androidx.lifecycle.ViewModelProvider;
 import androidx.recyclerview.widget.RecyclerView;

 import com.google.android.material.floatingactionbutton.FloatingActionButton;
 import com.vova9110.bloodbath.AlarmScreen.ActivenessDetectionService;
 import com.vova9110.bloodbath.Database.Alarm;
 import com.vova9110.bloodbath.RecyclerView.AlarmListAdapter;
 import com.vova9110.bloodbath.RecyclerView.RowLayoutManager;

 import java.util.List;

 import javax.inject.Inject;

 public class MainActivity extends AppCompatActivity{
     private final static String TAG = "TAG_MA";
     public final static String PREFERENCES_NAME = "prefs";

    public static final int NEW_TASK_ACTIVITY_REQUEST_CODE = 1;
    public static final int FILL_DB = 3;
    public static final int CLEAR_DB = 4;
    private AlarmViewModel mAlarmViewModel;
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
        mAlarmViewModel = new ViewModelProvider(this).get(AlarmViewModel.class);
        mAlarmViewModel.getComponent().inject(this);
        RecyclerView recyclerView = findViewById(R.id.recyclerview);
        adapter = new AlarmListAdapter(mHandler);
        recyclerView.setAdapter(adapter);

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
        detectionButton.setOnClickListener(view -> getApplicationContext().startService(new Intent(getApplicationContext(), ActivenessDetectionService.class).putExtra("detect", true)));
        detectionButton.setOnLongClickListener(view ->{
            getApplicationContext().startService(new Intent(getApplicationContext(), ActivenessDetectionService.class).putExtra("activate", true));
            return true;
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

    static class LDObserver implements Observer<List<Alarm>> {
        @Override
        public void onChanged(List<Alarm> alarms) {
            alarms.sort((o1, o2) -> {
                if (o1.getHour() != o2.getHour()) return o1.getHour() - o2.getHour();
                else return o1.getMinute() - o2.getMinute();
            });
            adapter.submitList(alarms);
            adapter.notifyDataSetChanged();
            Log.d(TAG, "Time to initial layout! List size: " + alarms.size());
            return;
        }
    }

     @Override
     protected void onResume() {
        Log.d (TAG, "Resuming");
        mHandler.onResumeUpdate();
        super.onResume();
     }
 }