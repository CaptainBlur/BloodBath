package com.vova9110.bloodbath.AlarmScreenBackground

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContract
import androidx.appcompat.app.AppCompatActivity
import com.vova9110.bloodbath.AlarmActivity
import com.vova9110.bloodbath.Database.Alarm
import com.vova9110.bloodbath.Database.AlarmRepo

/*
* ActivityResultCallback and NotificationService sends result of user's choice here
* Hence, we don't check alarm's current status when we get here - notification service checked it before
*/
class AlarmSupervisor : AppCompatActivity() {
    private val TAG = "TAG_AScreenSuper"
    companion object Constants {
        const val MAIN_CALL = 0
        const val DISMISSED = 1
        const val DELAYED_N = 2//Means, delayed in notification
        const val DELAYED = 3
    }

    private lateinit var repo: AlarmRepo
    private var current: Alarm? = null
    private var waitingForResult = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "Supervisor: onCreate ${intent.getIntExtra("action", -1)}")

        @Suppress("DEPRECATION")
        repo = if (android.os.Build.VERSION.SDK_INT>=android.os.Build.VERSION_CODES.TIRAMISU) intent.getSerializableExtra("repo", AlarmRepo::class.java)!!
        else intent.getSerializableExtra("repo") as AlarmRepo
        val actives = repo.actives
        if (actives.isNotEmpty()) current = actives[0] else Log.d(TAG, "no actives found. Test run")
        val action = intent.getIntExtra("action", -1).also { Log.d(TAG, "onCreate: $it received") }

        if (savedInstanceState?.getBoolean("waiting")!=null) waitingForResult = savedInstanceState.getBoolean("waiting")
        if (!waitingForResult){
            when (action){
                MAIN_CALL, DELAYED -> {
                    launcher.launch(action)
                    waitingForResult=true
                }
                DISMISSED -> dismiss()
                DELAYED_N -> delay()
            }
        }
    }
    
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("waiting", waitingForResult)
        super.onSaveInstanceState(outState)
    }

    private fun delay (){
        Thread {
            Log.d(TAG, "Alarm " + current?.hour + current?.minute + " delayed")
            current?.isDelayed=true
            repo.update(current)
        }.start()
        if (current!!.hour==77 && current!!.minute==77) repo.deleteOne(77,77)

        applicationContext.startService(Intent(applicationContext, NotificationService::class.java).putExtra("stop", true))
        finish()
    }
    private fun dismiss(){
        Thread {
            Log.d(TAG, "Alarm " + current?.hour + current?.minute + " dismissed")
            current?.isOnOffState = false
            repo.update(current)
        }.start()
        //todo pass current's enum here
        //applicationContext.startService(Intent(applicationContext, ActivenessDetectionService::class.java).putExtra("current", "current"))
        if (current!!.hour==77 && current!!.minute==77) repo.deleteOne(77,77)

        applicationContext.startService(Intent(applicationContext, NotificationService::class.java).putExtra("stop", true))
        finish()
    }

    private val callback = ActivityResultCallback<Int> {
        when (it){
            DISMISSED -> {
                Log.d(TAG, "callback received: DISMISSED")
                dismiss()
            }
            DELAYED -> {
                Log.d(TAG, "callback received: DELAYED")
                delay()
            }
        }
    }

    private val contract = object : ActivityResultContract<Int, Int>() {
        override fun createIntent(context: Context, input: Int): Intent {
            return Intent(context, AlarmActivity::class.java).also {
                it.putExtra("type",
                    input)
            }
        }

        override fun parseResult(resultCode: Int, intent: Intent?): Int {
            return resultCode
        }
    }
    private val launcher = registerForActivityResult(contract, callback)
}
