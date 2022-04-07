 package com.vova9110.bloodbath;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.vova9110.bloodbath.Database.Alarm;
import com.vova9110.bloodbath.RecyclerView.RowLayoutManager;

import java.util.List;

import javax.inject.Inject;

 public class MainActivity extends AppCompatActivity{

    public static final int NEW_TASK_ACTIVITY_REQUEST_CODE = 1;
    public static final int FILL_DB = 3;
    public static final int CLEAR_DB = 4;
    private AlarmViewModel mAlarmViewModel;
    private static TaskListAdapter adapter;
    LDObserver ldObserver;
    @Inject
    public AlarmRepo mRepo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Get a new or existing ViewModel from the ViewModelProvider.
        mAlarmViewModel = new ViewModelProvider(this).get(AlarmViewModel.class);
        RecyclerView recyclerView = findViewById(R.id.recyclerview);
        adapter = new TaskListAdapter(new TaskListAdapter.AlarmDiff(),mAlarmViewModel.getComponent());
        recyclerView.setAdapter(adapter);

        //recyclerView.setLayoutManager(new GridLayoutManager(this,1, RecyclerView.VERTICAL, false));
        recyclerView.setLayoutManager(new RowLayoutManager());
        ldObserver = new LDObserver();

        mAlarmViewModel.getComponent().inject(this);
        mRepo.getInitialList().observe(this, ldObserver);
        mRepo.passAdapterNObserver(adapter, ldObserver);

        ImageView imageView = findViewById(R.id.imageView);
        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(view -> {
            if (imageView.getVisibility() == View.INVISIBLE)
                imageView.setVisibility(View.VISIBLE);
            else imageView.setVisibility(View.INVISIBLE);
            Intent intent = new Intent(MainActivity.this, NewTaskActivity.class);
            startActivityForResult(intent, NEW_TASK_ACTIVITY_REQUEST_CODE);
        });

    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == NEW_TASK_ACTIVITY_REQUEST_CODE && resultCode == RESULT_OK) {
            //Alarm alarm = new Alarm(data.getStringExtra(NewTaskActivity.EXTRA_REPLY));
            //mRepo.insert(alarm);
        }
        else if (requestCode == NEW_TASK_ACTIVITY_REQUEST_CODE && resultCode == FILL_DB) mRepo.fill();
        else if (requestCode == NEW_TASK_ACTIVITY_REQUEST_CODE && resultCode == CLEAR_DB) mRepo.clear();
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
            adapter.submitList(alarms);
            Log.d("TAG", "Time to initial layout! List size: " + alarms.size());
            return;
        }
    }
}