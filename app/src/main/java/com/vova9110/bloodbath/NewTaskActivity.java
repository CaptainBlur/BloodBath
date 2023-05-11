package com.vova9110.bloodbath;

import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

import com.vova9110.bloodbath.alarmsUI.AdjustableImageView;

public class NewTaskActivity extends AppCompatActivity {

    public static final String EXTRA_REPLY = "com.example.android.Tasklistsql.REPLY";

    private EditText mEditTaskView;
    private Button fillButton;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_task);
        fillButton = findViewById(R.id.button);


        fillButton.setOnClickListener(view -> {
            Intent replyIntent = new Intent();
            setResult(MainActivity.FILL_DB, replyIntent);
            finish();
        });
        fillButton.setOnLongClickListener(view -> {
            Intent replyIntent = new Intent();
            setResult(MainActivity.CLEAR_DB, replyIntent);
            finish();
            return true;
        });
    }
}
