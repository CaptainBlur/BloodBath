package com.vova9110.bloodbath

import android.content.ClipData
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.vova9110.bloodbath.Database.Alarm
import javax.inject.Inject
import com.vova9110.bloodbath.Database.AlarmRepo
import android.os.Bundle
import android.content.Intent
import android.util.AttributeSet
import android.util.Log
import android.view.DragEvent
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.vova9110.bloodbath.AlarmScreen.ActivenessDetectionService
import com.vova9110.bloodbath.AlarmScreen.AlarmSupervisor

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
    private var current: Alarm? = null

    public override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "Alarm Activity started")
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alarm)
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        DaggerAppComponent.builder().dBModule(DBModule(application)).build().inject(this)
        if (repo!!.actives.isNotEmpty()) current = repo!!.actives[0] else Log.d(TAG, "onCreate: no actives found. Test run")
        listenDragNDrop(
            this.findViewById<CustomImageView>(R.id.imageView2),
            this.findViewById<ImageView>(R.id.imageView3),
            null,
            ::dismissFun,
            null)
    }

    private fun listenDragNDrop(
        startView: CustomImageView, dismissView: ImageView, delayView: TextView?,
        dismissBlock: (ImageView)->Unit, delayBlock: ((TextView)->Unit)?){

        dismissView.setOnDragListener {
                v, event ->
            if (event.action==DragEvent.ACTION_DROP){
                dismissBlock(v as ImageView)

                startView.alpha=1f
            }
            true
        }

        //We are only interested in DOWN action here
        //Also, startView handling exclusively in this (context) function,
        //Outside of two drag listeners
        startView.setOnTouchListener {
                view, motionEvent->
            if (motionEvent.action==MotionEvent.ACTION_DOWN){
                view.startDragAndDrop(ClipData.newPlainText("draggableIcon", "draggableIcon"),View.DragShadowBuilder(view), null, 0)
                view.alpha=0f
            }
            view.performClick()
            false
        }

    }

    private fun dismissFun(dismissView: ImageView){
        if (current!=null){
            Thread {
                Log.d(TAG, "Alarm " + current?.hour + current?.minute + " dismissed")
                current?.isOnOffState = false
                repo!!.update(current)
            }.start()
            //todo pass current's enum here
            applicationContext.startService(Intent(applicationContext, ActivenessDetectionService::class.java).putExtra("current", "current"))
        }
        else{
            Log.d(TAG, "dismissFun: not launching ActivenessDetector yet")
        }
    }
}

class CustomImageView: androidx.appcompat.widget.AppCompatImageView{
    constructor(context: Context): super(context)
    constructor(context: Context, attrs: AttributeSet?): super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int): super(context, attrs, defStyleAttr)

    override fun performClick(): Boolean {
        //todo add accessibility here
        return super.performClick()
    }
}
