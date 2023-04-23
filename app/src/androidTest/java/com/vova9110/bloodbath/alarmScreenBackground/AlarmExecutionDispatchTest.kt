package com.vova9110.bloodbath.alarmScreenBackground

import android.app.NotificationManager
import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import com.vova9110.bloodbath.database.Alarm
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import java.util.*

internal class AlarmExecutionDispatchTest {
    private lateinit var c: Context
    private lateinit var repo: AlarmRepo

    @BeforeEach
    fun getContext(){
        c = InstrumentationRegistry.getInstrumentation().targetContext
        repo = Mockito.mock(AlarmRepo::class.java)
//        Mockito.`when`(repo.getTimesInfo(Mockito.anyString())).thenReturn(
//            SubInfo(null,,
//                null,
//                volumeLock = true,
//                timeWarning = 40,
//                timeOut = 90,
//                globalSnoozed = 8,
//                globalLost = 15
//            )
//        )
    }
    @AfterEach fun cancel() = (c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancelAll()

    @Test
    fun insert_noTriggerTime(){
        val current = Alarm(0,0, true, Alarm.STATE_FIRE)

        try {
            AlarmExecutionDispatch.defineNewState(c, current, repo)
        } catch (e: IllegalStateException) {
            assert(true)
            return
        }
        assert(false)
    }
    @Test
    fun insert_notEnabled(){
        val current = Alarm(0,0, false, Alarm.STATE_FIRE)
        current.triggerTime = Date()

        try {
            AlarmExecutionDispatch.defineNewState(c, current, repo)
        } catch (e: IllegalStateException) {
            assert(true)
            return
        }
        assert(false)
    }
    @Test
    fun insert_disable(){
        val current = Alarm(0,0, false, Alarm.STATE_FIRE)

        BackgroundUtils.testListener = object: BackgroundUtils.IntentSchedulerListener {
            override fun onInfoPassed(state: String?, time: Long, targetName: String?) = assert(false)

            override fun onInfoCancelled(state: String?, targetName: String?) = assertEquals(Alarm.STATE_ALL, state)

            override fun onNotificationCreated(state: String?) = assert(false)

            override fun onNotificationCancelled(state: String?) = assertEquals(Alarm.STATE_ALL, state)
        }

        AlarmExecutionDispatch.defineNewState(c, current, repo)
        assertEquals(Alarm.STATE_DISABLE, current.state)
        Mockito.verify(repo).update(Mockito.any(), Mockito.anyBoolean())
    }
    @Test
    fun insert_suspend(){
        val current = Alarm(0,0, true)
        current.triggerTime = Date(System.currentTimeMillis() + 15 * 60 * 60 * 1000)

        BackgroundUtils.testListener = object : BackgroundUtils.IntentSchedulerListener {
            override fun onInfoPassed(state: String?, time: Long, targetName: String?) {
                assertEquals(Alarm.STATE_ANTICIPATE, state)
                assertEquals(current.triggerTime!!.time - 10 * 60 * 60 * 1000, time)
                assertEquals(AlarmExecutionDispatch::class.java.name, targetName)
            }

            override fun onInfoCancelled(state: String?, targetName: String?) = assertEquals(Alarm.STATE_ALL, state)

            override fun onNotificationCreated(state: String?) = assert(false)

            override fun onNotificationCancelled(state: String?) = assertEquals(Alarm.STATE_ALL, state)
        }

        AlarmExecutionDispatch.defineNewState(c, current, repo)
        assertEquals(Alarm.STATE_SUSPEND, current.state)
        assertNotNull(current.triggerTime)
        assertNull(current.lastTriggerTime)
        assertEquals(null, current.lastTriggerTime)
        Mockito.verify(repo).update(Mockito.any(), Mockito.anyBoolean())
    }
    @Test
    fun insert_anticipate(){
        val current = Alarm(0,0, true)
        current.triggerTime = Date(System.currentTimeMillis() + 3 * 60 * 60 * 1000)

        BackgroundUtils.testListener = object : BackgroundUtils.IntentSchedulerListener {
            override fun onInfoPassed(state: String?, time: Long, targetName: String?) {
                assert(state==Alarm.STATE_PREPARE || state==Alarm.STATE_FIRE)
                assert((current.triggerTime!!.time - 2 * 60 * 60 * 1000)==time || current.triggerTime!!.time==time)
                assert(AlarmExecutionDispatch::class.java.name==targetName || FiringControlService::class.java.name==targetName)
            }

            override fun onInfoCancelled(state: String?, targetName: String) = assert(false)

            override fun onNotificationCreated(state: String?) = assert(false)

            override fun onNotificationCancelled(state: String?) = assert(false)

        }

        AlarmExecutionDispatch.defineNewState(c, current, repo)
        assertNotNull(current.triggerTime)
        assertEquals(Alarm.STATE_ANTICIPATE, current.state)
        Mockito.verify(repo).update(Mockito.any(), Mockito.anyBoolean())
    }
    @Test
    fun insert_prepareFire(){
        val current = Alarm(0,0, true)
        current.triggerTime = Date(System.currentTimeMillis() + 60 * 60 * 1000)

        BackgroundUtils.testListener = object : BackgroundUtils.IntentSchedulerListener {
            override fun onInfoPassed(state: String?, time: Long, targetName: String?) {
                assertEquals(Alarm.STATE_FIRE, state)
                assertEquals(current.triggerTime!!.time, time)
                assertEquals(FiringControlService::class.java.name, targetName)
            }

            override fun onInfoCancelled(state: String?, targetName: String) {
                assertEquals(Alarm.STATE_FIRE, state)
                assertEquals(FiringControlService::class.java.name, targetName)
            }

            override fun onNotificationCreated(state: String?) = assertEquals(Alarm.STATE_PREPARE, state)

            override fun onNotificationCancelled(state: String?) = assert(false)

        }

        AlarmExecutionDispatch.defineNewState(c, current, repo)
        assertNotNull(current.triggerTime)
        assertEquals(Alarm.STATE_PREPARE, current.state)
        Mockito.verify(repo).update(Mockito.any(), Mockito.anyBoolean())
    }
    @Test
    fun insert_prepareSnooze(){
        val current = Alarm(0,0, true)
        current.triggerTime = Date(System.currentTimeMillis() + 5 * 60 * 1000)
        current.snoozed = true

        BackgroundUtils.testListener = object : BackgroundUtils.IntentSchedulerListener {
            override fun onInfoPassed(state: String?, time: Long, targetName: String?) {
                assertEquals(Alarm.STATE_SNOOZE, state)
                assertEquals(current.triggerTime!!.time, time)
                assertEquals(FiringControlService::class.java.name, targetName)
            }

            override fun onInfoCancelled(state: String?, targetName: String) = assert(false)

            override fun onNotificationCreated(state: String?) = assertEquals(Alarm.STATE_SNOOZE, state)

            override fun onNotificationCancelled(state: String?) = assert(false)

        }

        AlarmExecutionDispatch.defineNewState(c, current, repo)
        assertNotNull(current.triggerTime)
        assertEquals(Alarm.STATE_SNOOZE, current.state)
        Mockito.verify(repo).update(Mockito.any(), Mockito.anyBoolean())
    }
    @Test
    fun insert_miss_toDisable(){
        val current = Alarm(0,0, false)
        current.lastTriggerTime = Date(System.currentTimeMillis() - 60 * 60 * 1000)

        BackgroundUtils.testListener = object : BackgroundUtils.IntentSchedulerListener {
            override fun onInfoPassed(state: String?, time: Long, targetName: String?) {
                assertEquals(Alarm.STATE_DISABLE, state)
                assertEquals(current.lastTriggerTime!!.time + 2 * 60 * 60 * 1000, time)
                assertEquals(AlarmExecutionDispatch::class.java.name, targetName)
            }

            override fun onInfoCancelled(state: String?, targetName: String) = assert(false)

            override fun onNotificationCreated(state: String?) = assertEquals(Alarm.STATE_MISS, state)

            override fun onNotificationCancelled(state: String?) = assert(false)

        }

        AlarmExecutionDispatch.defineNewState(c, current, repo)
        assertNotNull(current.lastTriggerTime)
        assertEquals(Alarm.STATE_MISS, current.state)
        Mockito.verify(repo).update(Mockito.any(), Mockito.anyBoolean())
    }
    @Test
    fun insert_miss_toSuspend(){
        val current = Alarm(0,0, true)
        current.lastTriggerTime = Date(System.currentTimeMillis() - 60 * 60 * 1000)
        current.triggerTime = Date(System.currentTimeMillis() + 23 * 60 * 60 * 1000)

        BackgroundUtils.testListener = object : BackgroundUtils.IntentSchedulerListener {
            override fun onInfoPassed(state: String?, time: Long, targetName: String?) {
                assertEquals(Alarm.STATE_SUSPEND, state)
                assertEquals(current.lastTriggerTime!!.time + 2 * 60 * 60 * 1000, time)
                assertEquals(AlarmExecutionDispatch::class.java.name, targetName)
            }

            override fun onInfoCancelled(state: String?, targetName: String) = assert(false)

            override fun onNotificationCreated(state: String?) = assertEquals(Alarm.STATE_MISS, state)

            override fun onNotificationCancelled(state: String?) = assert(false)

        }

        AlarmExecutionDispatch.defineNewState(c, current, repo)
        assertNotNull(current.lastTriggerTime)
        assertEquals(Alarm.STATE_MISS, current.state)
        Mockito.verify(repo).update(Mockito.any(), Mockito.anyBoolean())
    }
    @Test
    fun insert_goneMiss_toDisable(){
        val current = Alarm(0,0, false)
        current.lastTriggerTime = Date(System.currentTimeMillis() - 3 * 60 * 60 * 1000)

        BackgroundUtils.testListener = object: BackgroundUtils.IntentSchedulerListener {
            override fun onInfoPassed(state: String?, time: Long, targetName: String?) = assert(false)

            override fun onInfoCancelled(state: String?, targetName: String?) = assertEquals(Alarm.STATE_ALL, state)

            override fun onNotificationCreated(state: String?) = assert(false)

            override fun onNotificationCancelled(state: String?) = assertEquals(Alarm.STATE_ALL, state)
        }

        AlarmExecutionDispatch.defineNewState(c, current, repo)
        assertEquals(Alarm.STATE_DISABLE, current.state)
        assertNull(current.lastTriggerTime)
        Mockito.verify(repo).update(Mockito.any(), Mockito.anyBoolean())
    }
    @Test
    fun insert_goneMiss_toSuspend(){
        val current = Alarm(0,0, true)
        current.lastTriggerTime = Date(System.currentTimeMillis() - 3 * 60 * 60 * 1000)
        current.triggerTime = Date(System.currentTimeMillis() + 21 * 60 * 60 * 1000)

        BackgroundUtils.testListener = object : BackgroundUtils.IntentSchedulerListener {
            override fun onInfoPassed(state: String?, time: Long, targetName: String?) {
                assertEquals(Alarm.STATE_ANTICIPATE, state)
                assertEquals(current.triggerTime!!.time - 10 * 60 * 60 * 1000, time)
                assertEquals(AlarmExecutionDispatch::class.java.name, targetName)
            }

            override fun onInfoCancelled(state: String?, targetName: String?) = assertEquals(Alarm.STATE_ALL, state)

            override fun onNotificationCreated(state: String?) = assert(false)

            override fun onNotificationCancelled(state: String?) = assertEquals(Alarm.STATE_ALL, state)

        }

        AlarmExecutionDispatch.defineNewState(c, current, repo)
        assertEquals(Alarm.STATE_SUSPEND, current.state)
        assertNull(current.lastTriggerTime)
        assertNotNull(current.triggerTime)
        Mockito.verify(repo).update(Mockito.any(), Mockito.anyBoolean())
    }

    @Test
    fun insert_didNotFire_fire(){
        val current = Alarm(0,0, true)
        current.triggerTime = Date(System.currentTimeMillis() - 5 * 60 * 1000)

        BackgroundUtils.testListener = object : BackgroundUtils.IntentSchedulerListener {
            override fun onInfoPassed(state: String?, time: Long, targetName: String?) {
                assertEquals(Alarm.STATE_FIRE, state)
                assertEquals(FiringControlService::class.java.name, targetName)
            }

            override fun onInfoCancelled(state: String?, targetName: String) = assert(false)

            override fun onNotificationCreated(state: String?) = assert(false)

            override fun onNotificationCancelled(state: String?) = assert(false)

        }

        AlarmExecutionDispatch.defineNewState(c, current, repo)
        assertEquals(Alarm.STATE_FIRE, current.state)
        assertNotNull(current.triggerTime)
        Mockito.verify(repo).update(Mockito.any(), Mockito.anyBoolean())
    }
    @Test
    fun insert_didNotFire_snooze(){
        val current = Alarm(0,0, true)
        current.triggerTime = Date(System.currentTimeMillis() - 5 * 60 * 1000)
        current.snoozed = true

        BackgroundUtils.testListener = object : BackgroundUtils.IntentSchedulerListener {
            override fun onInfoPassed(state: String?, time: Long, targetName: String?) {
                assertEquals(Alarm.STATE_SNOOZE, state)
                assertEquals(current.triggerTime!!.time, time)
                assertEquals(FiringControlService::class.java.name, targetName)
            }

            override fun onInfoCancelled(state: String?, targetName: String) = assert(false)

            override fun onNotificationCreated(state: String?) = assertEquals(Alarm.STATE_SNOOZE, state)

            override fun onNotificationCancelled(state: String?) = assert(false)

        }

        AlarmExecutionDispatch.defineNewState(c, current, repo)
        assertEquals(Alarm.STATE_SNOOZE, current.state)
        assertNotNull(current.triggerTime)
        assertNull(current.lastTriggerTime)
        Mockito.verify(repo).update(Mockito.any(), Mockito.anyBoolean())
    }
    @Test
    fun insert_didNotFire_miss_toDisable(){
        val current = Alarm(0,0, true, Alarm.STATE_FIRE)
        current.triggerTime = Date(System.currentTimeMillis() - 60 * 60 * 1000)
        val tT = current.triggerTime!!.time

        BackgroundUtils.testListener = object : BackgroundUtils.IntentSchedulerListener {
            override fun onInfoPassed(state: String?, time: Long, targetName: String?) {
                assertEquals(Alarm.STATE_DISABLE, state)
                assertEquals(tT + 2 * 60 * 60 * 1000, time)
                assertEquals(AlarmExecutionDispatch::class.java.name, targetName)
            }

            override fun onInfoCancelled(state: String?, targetName: String) = assert(false)

            override fun onNotificationCreated(state: String?) = assertEquals(Alarm.STATE_MISS, state)

            override fun onNotificationCancelled(state: String?) = assert(false)

        }

        AlarmExecutionDispatch.defineNewState(c, current, repo)
        assertEquals(Alarm.STATE_MISS, current.state)
        assertNotNull(current.lastTriggerTime)
        assertNull(current.triggerTime)
        Mockito.verify(repo).update(Mockito.any(), Mockito.anyBoolean())
    }
    @Test
    fun insert_didNotFire_miss_toSuspend(){
        val current = Alarm(0,0, true, Alarm.STATE_FIRE)
        current.triggerTime = Date(System.currentTimeMillis() - 60 * 60 * 1000)
        val tT = current.triggerTime!!.time
        current.repeatable = true
        current.weekdays = BooleanArray(7){ true }

        BackgroundUtils.testListener = object : BackgroundUtils.IntentSchedulerListener {
            override fun onInfoPassed(state: String?, time: Long, targetName: String?) {
                assertEquals(Alarm.STATE_SUSPEND, state)
                assertEquals(tT + 2 * 60 * 60 * 1000, time)
                assertEquals(AlarmExecutionDispatch::class.java.name, targetName)
            }

            override fun onInfoCancelled(state: String?, targetName: String) = assert(false)

            override fun onNotificationCreated(state: String?) = assertEquals(Alarm.STATE_MISS, state)

            override fun onNotificationCancelled(state: String?) = assert(false)

        }

        AlarmExecutionDispatch.defineNewState(c, current, repo)
        assertEquals(Alarm.STATE_MISS, current.state)
        assertNotNull(current.triggerTime)
        assertNotNull(current.lastTriggerTime)
        Mockito.verify(repo).update(Mockito.any(), Mockito.anyBoolean())
    }
}