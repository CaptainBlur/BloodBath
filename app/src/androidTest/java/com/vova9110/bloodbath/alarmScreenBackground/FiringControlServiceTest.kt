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

        for (active in repo.actives) if (active.test) repo.deleteOne(active, c)
    }

    @Test
    fun passSimple(){
        val cal = Calendar.getInstance().apply { add(Calendar.MINUTE, 1) }
        val new = Alarm(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), enabled = true, test = true)

        repo.insert(new, c)
    }
    @Test
    fun passTwoSimple(){
        val cal = Calendar.getInstance().apply { add(Calendar.MINUTE, 1) }
        val new = Alarm(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), enabled = true, test = true)

        val cal_ = Calendar.getInstance().apply { add(Calendar.MINUTE, 16) }
        val new_ = Alarm(cal_.get(Calendar.HOUR_OF_DAY), cal_.get(Calendar.MINUTE), enabled = true, test = true)

        repo.insert(new, c)
        repo.insert(new_, c)
    }
    @Test
    fun passPreliminary(){
        val cal = Calendar.getInstance().apply { add(Calendar.MINUTE, 31) }
        val new = Alarm(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), enabled = true, test = true).apply { preliminary = true }

        repo.insert(new, c)
    }
    @Test
    fun passPreliminaryAndRegularForButton(){
        val cal = Calendar.getInstance().apply { add(Calendar.MINUTE, 32) }
        val new = Alarm(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), enabled = true, test = true).apply { preliminary = true }

        val cal_ = Calendar.getInstance().apply { add(Calendar.MINUTE, 16) }
        val new_ = Alarm(cal_.get(Calendar.HOUR_OF_DAY), cal_.get(Calendar.MINUTE), enabled = true, test = true)

        repo.insert(new, c)
        repo.insert(new_, c)
    }
}