package com.foxstoncold.youralarm;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;

import androidx.appcompat.app.AppCompatActivity;

import pl.droidsonroids.gif.GifImageView;

public class NewTaskActivity extends AppCompatActivity {

    public static final String EXTRA_REPLY = "com.example.android.Tasklistsql.REPLY";

    private EditText mEditTaskView;
    private Button fillButton;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_task);
        fillButton = findViewById(R.id.button);
        GifImageView gifView = findViewById(R.id.kotya_pic);
//        gifView.setImageResource(R.drawable.kotya_gif_1);


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

        final int[] i = {1};
//        gifView.setOnClickListener((v)->{
//            GifImageView view  = (GifImageView) v;
//            i[0]++;
//            switch (i[0]){
//                case(1): view.setImageResource(R.drawable.kotya_gif_1);
//                    break;
//
//                case(2): view.setImageResource(R.drawable.kotya_gif_2);
//                    break;
//
//                case(3): view.setImageResource(R.drawable.kotya_gif_3);
//                    break;
//
//                case(4): view.setImageResource(R.drawable.kotya_gif_4);
//                    break;
//
//                case(5): view.setImageResource(R.drawable.kotya_gif_5);
//                    break;
//
//                case(6): view.setImageResource(R.drawable.kotya_gif_6);
//                    break;
//            }
//            if (i[0]==6) i[0]=0;
//        });
    }
}
