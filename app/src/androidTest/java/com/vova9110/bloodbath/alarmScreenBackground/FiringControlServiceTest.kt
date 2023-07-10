package com.vova9110.bloodbath.alarmScreenBackground

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.test.platform.app.InstrumentationRegistry
import com.vova9110.bloodbath.MyApp
import com.vova9110.bloodbath.database.Alarm
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Calendar

internal class FiringControlServiceTest{
    private lateinit var c: Context
    private lateinit var repo: AlarmRepo

    @BeforeEach
    fun getVars(){
        c = InstrumentationRegistry.getInstrumentation().targetContext
        repo = (c.applicationContext as MyApp).component.repo
    }

    @Test
    fun passSimple(){
        val cal = Calendar.getInstance().apply { add(Calendar.MINUTE, 1) }
        val new = Alarm(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true)

        repo.insert(new, c)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                repo.deleteOne(new, c)
            }
        }
        val filter = IntentFilter(FiringControlService.ACTION_KILL)
        c.registerReceiver(receiver, filter)
    }
}