package com.vova9110.bloodbath

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.vova9110.bloodbath.AlarmScreenBackground.NotificationService
import com.vova9110.bloodbath.Database.AlarmRepo
import com.vova9110.bloodbath.SplitLogger.Companion.initialize
import com.vova9110.bloodbath.SplitLogger.SLCompanion
import javax.inject.Inject

//Using receiver only for starting NotificationService, and passing repo instance to it
class AlarmDeployReceiver : BroadcastReceiver() {
    var repo: AlarmRepo? = null
        @Inject set

    override fun onReceive(context: Context, intent: Intent) {
        initialize(context, true)
        val sl = SLCompanion(this.javaClass.name, false)

        sl.i("Alarm broadcast received!")
        DaggerAppComponent.builder().dBModule(DBModule(context.applicationContext as Application)).build().inject(this)
        context.startService(Intent(context, NotificationService::class.java)
            .putExtra("repo", repo))
    }
}