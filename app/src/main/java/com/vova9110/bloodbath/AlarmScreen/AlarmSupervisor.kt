package com.vova9110.bloodbath.AlarmScreen

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContract
import androidx.appcompat.app.AppCompatActivity

class AlarmSupervisor : AppCompatActivity() {
    private val TAG = "TAG_AScreenSuper"
    companion object Constants {
        val MAIN_CALL = 0
        val DISMISSED = 1
        val DELAYED = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "onCreate")

//        val contract = object : ActivityResultContract<String, String>() {
//            override fun createIntent(context: Context, input: String?): Intent {
//                TODO("Not yet implemented")
//            }
//
//            override fun parseResult(resultCode: Int, intent: Intent?): String {
//                TODO("Not yet implemented")
//            }
//
//        }

        finish()
    }

}