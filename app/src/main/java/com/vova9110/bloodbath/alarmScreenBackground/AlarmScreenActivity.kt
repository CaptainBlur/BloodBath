package com.vova9110.bloodbath.alarmScreenBackground

import android.content.*
import android.graphics.Color
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.util.AttributeSet
import android.view.DragEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.vova9110.bloodbath.MyApp
import com.vova9110.bloodbath.R
import com.vova9110.bloodbath.alarmScreenBackground.*
import com.vova9110.bloodbath.database.Alarm
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * This activity has three strictly defined variants of appearance.
 * All of them depends on Supervisor's calling value.
 * It doesn't has any computation or database access here.
 */
class AlarmActivity : AppCompatActivity() {
    private var alarmHandled = false
    private var serviceBound = false
    private var receiverRegistered = false
    private val mConn = object: ServiceConnection{
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) { serviceBound = true }
        override fun onServiceDisconnected(name: ComponentName?) { serviceBound = false }
    }
    private val receiver = object: BroadcastReceiver(){
        override fun onReceive(context: Context?, intent: Intent?) {
            intent!!
            sl.fr("broadcast received: ${intent.action}")

            if (alarmHandled) return

            sl.fp("handling alarm with id: *${info.id}*")
            when(intent.action){
                FiringControlService.ACTION_SNOOZE -> snoozeFun(startView, snoozeView)
                FiringControlService.ACTION_DISMISS -> dismissFun(startView, dismissView)
                FiringControlService.ACTION_KILL -> finish()
            }
        }
    }
    private val filter = IntentFilter().apply {
        addAction(FiringControlService.ACTION_SNOOZE)
        addAction(FiringControlService.ACTION_DISMISS)
        addAction(FiringControlService.ACTION_KILL)
    }
    private lateinit var repo: AlarmRepo
    private lateinit var info: SubInfo

    private lateinit var startView: CustomImageView
    private lateinit var dismissView: ImageView
    private lateinit var snoozeView: TextView

    @Suppress("DEPRECATION")
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                or WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON)

        setContentView(R.layout.activity_alarm)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        volumeControlStream = AudioManager.STREAM_ALARM

        startView = this.findViewById(R.id.alarm_start_view)
        dismissView = this.findViewById(R.id.alarm_dismiss_view)
        snoozeView = this.findViewById(R.id.alarm_delay_view)

        repo = (applicationContext as MyApp).component.repo
        info = if (Build.VERSION.SDK_INT>= Build.VERSION_CODES.TIRAMISU) intent.getParcelableExtra("info", SubInfo::class.java)!!
        else intent.getParcelableExtra("info")!!

        listenDragNDrop(
            startView,
            dismissView,
            snoozeView,
            ::dismissFun,
            ::snoozeFun)
    }

    override fun onResume() {
        super.onResume()
        sl.en()

        if (!receiverRegistered){
            receiverRegistered = true
            registerReceiver(receiver, filter)
        }

        bindService(Intent(this, FiringControlService::class.java), mConn, BIND_AUTO_CREATE)
        sl.fr("head service connected")
    }

    override fun onStop() {
        super.onStop()
        sl.ex()

        if (receiverRegistered){
            receiverRegistered = false
            unregisterReceiver(receiver)
        }
        unbind()
    }

    private fun unbind(){
        if (serviceBound) {
            serviceBound = false
            unbindService(mConn)
            sl.fr("head service disconnected")
        }
    }


    private fun listenDragNDrop(
        startView: CustomImageView, dismissView: ImageView, snoozeView: TextView,
        dismissBlock: (CustomImageView, ImageView) -> Unit, snoozeBlock: ((CustomImageView, TextView) -> Unit),
    ){
        if (info.snoozed) snoozeView.visibility=View.INVISIBLE

        //We are only interested in DOWN action here
        //Also, startView handling exclusively in this (context) function,
        //Outside of two drag listeners
        startView.setOnTouchListener {
                view, motionEvent->
            println(motionEvent.action)
            if (motionEvent.action==MotionEvent.ACTION_DOWN){
                view.startDragAndDrop(ClipData.newPlainText("draggableIcon", "draggableIcon"),View.DragShadowBuilder(view), null, 0)
                view.alpha=0f
            }
            view.performClick()
            false
        }
        //However, an essential logic placed in functions outside of this scope
        dismissView.setOnDragListener { v, event ->
//            println(event.action)
            if (event.action==DragEvent.ACTION_DROP) dismissBlock(startView, v as ImageView)
            else if (event.action==DragEvent.ACTION_DRAG_ENDED) startView.alpha=1f
            true
        }
        snoozeView.setOnDragListener{ v, event->
            if (event.action==DragEvent.ACTION_DROP) snoozeBlock(startView, v as TextView)
            true
        }
    }

    private fun dismissFun(startView: CustomImageView, dismissView: ImageView){
        alarmHandled = true
        startView.x = dismissView.x
        startView.y = dismissView.y
        dismissView.visibility = View.INVISIBLE
        startView.alpha=1f

        AlarmExecutionDispatch.helpDismiss(applicationContext, info.id, repo)
        unbind()

        sl.ex()
        Handler(this.mainLooper).postDelayed({finish()}, 2000)
    }

    private fun snoozeFun(startView: CustomImageView, snoozeView: TextView){
        alarmHandled = true
        snoozeView.setBackgroundColor(Color.parseColor("#FBC02D"))
        startView.visibility=View.INVISIBLE

        AlarmExecutionDispatch.helpSnooze(applicationContext, info.id, repo)
        unbind()

        sl.ex()
        Handler(this.mainLooper).postDelayed({finish()}, 2000)
    }

    @Deprecated("Deprecated in Java", ReplaceWith("Unit"))
    override fun onBackPressed() = Unit

}

class CustomImageView: androidx.appcompat.widget.AppCompatImageView{
    constructor(context: Context): super(context)
    constructor(context: Context, attrs: AttributeSet?): super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int): super(context, attrs, defStyleAttr)

    override fun performClick(): Boolean {
        //todo add some accessibility here
        return super.performClick()
    }
}
