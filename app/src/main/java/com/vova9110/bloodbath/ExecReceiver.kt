package com.vova9110.bloodbath

import android.app.AlarmManager
import android.app.AlarmManager.AlarmClockInfo
import android.app.Application
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.vova9110.bloodbath.AlarmScreenBackground.AlarmExec
import com.vova9110.bloodbath.AlarmScreenBackground.AlarmExec.getCalendar
import com.vova9110.bloodbath.Database.AlarmRepo
import java.util.*
import javax.inject.Inject

//Here se will be scheduling Executor's appointments and emitting notifications
//Executor have no control to this scheduling
class ExecReceiver : BroadcastReceiver() {
    var repo: AlarmRepo? = null
        @Inject set
    override fun onReceive(context: Context, intent: Intent) {
        DaggerAppComponent.builder().dBModule(DBModule(context.applicationContext as Application)).build().inject(this)
        SplitLogger.initialize(context)

        if (intent.action==Intent.ACTION_LOCKED_BOOT_COMPLETED){

            val actives = repo!!.actives
            if (actives.isNotEmpty()){
                val calendar = Calendar.getInstance()
                calendar.time = actives[0].triggerTime
                val scheduledCalendar: Calendar = getCalendar(calendar.time)

                val AManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val broadcastI = Intent(context, AlarmDeployReceiver::class.java)
                val execI = Intent(context, ExecReceiver::class.java)

                val ID = AlarmExec.getID(calendar[Calendar.HOUR_OF_DAY],
                    calendar[Calendar.MINUTE])
                val execID = AlarmExec.getID(scheduledCalendar[Calendar.HOUR_OF_DAY],
                    scheduledCalendar[Calendar.MINUTE])

                val activePI = PendingIntent.getBroadcast(context, ID, broadcastI, PendingIntent.FLAG_IMMUTABLE)
                val execPI = PendingIntent.getBroadcast(context, execID, execI, PendingIntent.FLAG_IMMUTABLE)

                if (System.currentTimeMillis() >= scheduledCalendar.timeInMillis) {
                    val info = AlarmClockInfo(calendar.timeInMillis, activePI)
                    AManager.setAlarmClock(info, activePI)
                } else {
                    AManager.setExact(AlarmManager.RTC, scheduledCalendar.timeInMillis, execPI)
                }
            }
        }

//        context.startService(Intent(context, AlarmExec::class.java))
    }
}