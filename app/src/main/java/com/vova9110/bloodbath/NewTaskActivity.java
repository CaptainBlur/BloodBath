package com.vova9110.bloodbath;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;

public class NewTaskActivity extends AppCompatActivity {

    public static final String EXTRA_REPLY = "com.example.android.Tasklistsql.REPLY";

    private EditText mEditTaskView;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_task);
        mEditTaskView = findViewById(R.id.edit_task);

        final Button button = findViewById(R.id.button_save);
        button.setOnClickListener(view -> {
            Intent replyIntent = new Intent();
            if (TextUtils.isEmpty(mEditTaskView.getText())) {
                setResult(RESULT_CANCELED, replyIntent);
            } else {
                String Task = mEditTaskView.getText().toString();
                replyIntent.putExtra(EXTRA_REPLY, Task);
                setResult(RESULT_OK, replyIntent);
            }
            finish();
        });
    }
}
