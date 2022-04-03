 package com.vova9110.bloodbath;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.vova9110.bloodbath.Database.Tasks;

import java.util.List;

import javax.inject.Inject;

 public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    public static final int NEW_TASK_ACTIVITY_REQUEST_CODE = 1;
    public static final int FILL_DB = 3;
    public static final int CLEAR_DB = 4;
    private TaskViewModel mTaskViewModel;
    @Inject
    public TaskRepo mRepo;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Get a new or existing ViewModel from the ViewModelProvider.
        mTaskViewModel = new ViewModelProvider(this).get(TaskViewModel.class);
        RecyclerView recyclerView = findViewById(R.id.recyclerview);
        final TaskListAdapter adapter = new TaskListAdapter(new TaskListAdapter.TaskDiff(),mTaskViewModel.getComponent());
        recyclerView.setAdapter(adapter);
        //recyclerView.setLayoutManager(new GridLayoutManager(this,1, RecyclerView.VERTICAL, false));
        recyclerView.setLayoutManager(new RowLayoutManager());

        mTaskViewModel.getComponent().inject(this);

        mTaskViewModel.getAllTasks().observe(this, tasks -> {//TODO подробно расписать метод
            // Update the cached copy of the tasks in the adapter.
            adapter.submitList(tasks);
            Log.d("TAG", "Time to layout!");
        });

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
            Tasks task = new Tasks(data.getStringExtra(NewTaskActivity.EXTRA_REPLY));
            mTaskViewModel.insert(task);
        }
        else if (requestCode == NEW_TASK_ACTIVITY_REQUEST_CODE && resultCode == FILL_DB) mTaskViewModel.fill();
        else if (requestCode == NEW_TASK_ACTIVITY_REQUEST_CODE && resultCode == CLEAR_DB) mTaskViewModel.clear();
        else {
            Toast.makeText(
                    getApplicationContext(),
                    R.string.empty_not_saved,
                    Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onClick(View v) {
        return;
    }
}