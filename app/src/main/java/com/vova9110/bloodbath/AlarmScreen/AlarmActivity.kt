package com.vova9110.bloodbath

import android.app.NotificationManager
import android.content.ClipData
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.AttributeSet
import android.util.Log
import android.view.DragEvent
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.getSystemService
import com.vova9110.bloodbath.Database.AlarmRepo
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * This activity has three strictly defined variants of appearance.
 * All of them depends on Supervisor's calling value.
 * It doesn't has any computation or database access here.
 */
class AlarmActivity : AppCompatActivity() {
    private val TAG = "TAG_AScreenA"
    @JvmField
    @Inject
    var repo: AlarmRepo? = null
    var extra = -1

    public override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "Alarm Activity started")
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        setContentView(R.layout.activity_alarm)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)

        extra = intent.getIntExtra("type", extra)

        listenDragNDrop(
            this.findViewById(R.id.alarm_start_view),
            this.findViewById(R.id.alarm_dismiss_view),
            this.findViewById(R.id.alarm_delay_view),
            ::dismissFun,
            ::delayFun)
    }

    private fun listenDragNDrop(
        startView: CustomImageView, dismissView: ImageView, delayView: TextView,
        dismissBlock: (CustomImageView, ImageView) -> Unit, delayBlock: ((CustomImageView, TextView) -> Unit),
    ){
        if (extra==AlarmSupervisor.DELAYED) delayView.visibility=View.INVISIBLE

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
        delayView.setOnDragListener{ v, event->
            if (event.action==DragEvent.ACTION_DROP) delayBlock(startView, v as TextView)
            true
        }
    }

    private fun dismissFun(startView: CustomImageView, dismissView: ImageView){
        startView.x = dismissView.x
        startView.y = dismissView.y
        dismissView.visibility = View.INVISIBLE
        startView.alpha=1f

        setResult(AlarmSupervisor.DISMISSED)
        Thread {
            TimeUnit.SECONDS.sleep(2 )
            finish()
        }.start()
    }

    private fun delayFun(startView: CustomImageView, delayView: TextView){
        delayView.setBackgroundColor(Color.parseColor("#FBC02D"))
        startView.visibility=View.INVISIBLE

        setResult(AlarmSupervisor.DELAYED)
        Thread {
            TimeUnit.SECONDS.sleep(2 )
            finish()
        }.start()
    }
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
