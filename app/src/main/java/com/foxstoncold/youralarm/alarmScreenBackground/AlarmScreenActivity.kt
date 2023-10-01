package com.foxstoncold.youralarm.alarmScreenBackground

import android.animation.ValueAnimator
import android.content.*
import android.graphics.drawable.PictureDrawable
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.view.View
import android.view.WindowManager
import android.view.animation.BounceInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import com.caverock.androidsvg.RenderOptions
import com.caverock.androidsvg.SVG
import com.foxstoncold.youralarm.MyApp
import com.foxstoncold.youralarm.R
import com.foxstoncold.youralarm.alarmScreenBackground.*
import com.foxstoncold.youralarm.alarmsUI.InterfaceUtils
import com.foxstoncold.youralarm.alarmsUI.InterfaceUtils.Companion.toPx
import com.foxstoncold.youralarm.alarmsUI.MainDrawables
import com.foxstoncold.youralarm.databinding.ActivityAlarmBinding
import java.util.Calendar

typealias Contractor = InterfaceUtils.Contractor


/**
 * This activity has three strictly defined variants of appearance.
 * All of them depends on Supervisor's calling value.
 * It doesn't has any computation or database access here.
 */
class AlarmActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAlarmBinding

    private var alarmHandled = false
    private var serviceBound = false
    private var receiverRegistered = false
    //now I see there's a more efficient way to use FCS by binding, but I'm not refactoring that 'till it works
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
                FiringControlService.ACTION_SNOOZE -> snoozeFun()
                FiringControlService.ACTION_DISMISS -> dismissFun()
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

    private lateinit var draggableView: ImageView
    private lateinit var dismissView: ImageView
    private lateinit var snoozeView: TextView

    @Suppress("DEPRECATION")
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        binding = ActivityAlarmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                or WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON)

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        volumeControlStream = AudioManager.STREAM_ALARM


        this.draggableView = this.findViewById(R.id.alarm_draggable_view)
        dismissView = this.findViewById(R.id.alarm_dismiss_view)
        snoozeView = this.findViewById(R.id.alarm_snooze_view)

        repo = (applicationContext as MyApp).component.repo
        info = if (Build.VERSION.SDK_INT>= Build.VERSION_CODES.TIRAMISU) intent.getParcelableExtra(FiringUtils.infoExtra, SubInfo::class.java)!!
        else intent.getParcelableExtra(FiringUtils.infoExtra)!!

        setupDigitClock()
        setupViews()
    }

    override fun onResume() {
        super.onResume()
        sl.en()

        if (this::repo.isInitialized)
            if (!AED.reassureStillValid(info.id, repo)){
                sl.f("activity reassurance failed. Exiting")
                finish()
                return
            }

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

    private fun setupDigitClock(){
        val distance = applicationContext.resources.getDimension(R.dimen.main_digitClock_translationDistance).toInt()

        val duration = 3800L
        val sepDuration = 750L

        val startDelay = 300L
        val firstDelay = 1800L
        val secDelay = 2300L

        val digitsC = Contractor(0.28f, 0.85f)

        val hour = String.format("%02d", info.firingHour)
        val minute = String.format("%02d", info.firingMinute)

        val digitOne = binding.alarmDisplayClockDigitOne.apply { text = (hour).substring(0, 1) }
        val digitTwo = binding.alarmDisplayClockDigitTwo.apply { text = (hour).substring(1) }
        val digitSeparator = binding.alarmDisplayClockDigitSeparator
        val digitThree = binding.alarmDisplayClockDigitThree.apply { text = (minute).substring(0, 1) }
        val digitFour = binding.alarmDisplayClockDigitFour.apply { text = (minute).substring(1) }

        ValueAnimator.ofInt(0, distance).apply {
            this.startDelay = startDelay
            this.duration = duration
            interpolator = BounceInterpolator()

            addUpdateListener {
                digitOne.translationY = (it.animatedValue as Int).toFloat()
                digitTwo.translationY = (it.animatedValue as Int).toFloat()

                val alpha = digitsC.contract(it.animatedFraction)
                digitOne.alpha = alpha
                digitTwo.alpha = alpha
            }

            start()
        }

        ValueAnimator.ofInt(0, distance).apply {
            this.startDelay = startDelay + firstDelay
            this.duration = duration
            interpolator = BounceInterpolator()

            addUpdateListener {
                digitThree.translationY = (it.animatedValue as Int).toFloat()
                digitFour.translationY = (it.animatedValue as Int).toFloat()

                val alpha = digitsC.contract(it.animatedFraction)
                digitThree.alpha = alpha
                digitFour.alpha = alpha
            }

            start()
        }

        digitSeparator.apply {
            translationY = distance.toFloat()

            animate().apply {
                this.startDelay = startDelay + firstDelay + secDelay
                this.duration = sepDuration
                alpha(1f)
                start()
            }
        }
    }

    private fun setupViews(){
        if (info.preliminary)
            dismissView.setImageResource(R.drawable.ic_eye_open)
        else if (info.activeness)
            dismissView.setImageResource(R.drawable.ic_activeness_medium)
        else
            dismissView.setImageResource(R.drawable.ic_waking_up)

        val infoView = binding.alarmInfoView
        if (info.preliminaryEnabled && info.preliminary)
            infoView.text = "Preliminary"
        else if (info.preliminaryEnabled)
            infoView.text = "Main"
        else{
            infoView.measure(0,0)
            binding.alarmDigits.y += infoView.measuredHeight
            infoView.visibility = View.GONE
        }

        val draggableViewSide = applicationContext.resources.getDimension(R.dimen.alarm_draggable_view_side).toInt()
        draggableView.apply {
            val drawableSVG = SVG.getFromAsset(context.assets, "alarm_draggable_circle.svg").apply {
                documentWidth = draggableViewSide.toFloat()
                documentHeight = draggableViewSide.toFloat()
            }
            val drawable = PictureDrawable(drawableSVG.renderToPicture(RenderOptions.create().css(
                "#stop1 { stop-color:${MainDrawables.getRGB(applicationContext, R.color.mild_pitchRegular)} }"
            )))

            this.setImageDrawable(drawable)
        }

        snoozeView.measure(0,0)
        val dismissViewSide = applicationContext.resources.getDimension(R.dimen.alarm_dismiss_view_side).toInt()
        val upDistance = applicationContext.resources.getDimension(R.dimen.alarm_draggable_view_up_trailing_distance).toInt() +
                dismissViewSide + ((draggableViewSide - dismissViewSide) / 2) -
                5.toPx().toInt()
        val downDistance = applicationContext.resources.getDimension(R.dimen.alarm_draggable_view_down_trailing_distance).toInt() +
                snoozeView.measuredHeight + ((draggableViewSide - snoozeView.measuredHeight) / 2)

        var driver = InterfaceUtils.DragViewDriver(yPositiveMax = downDistance, yNegativeMax = -upDistance,
            yNegativeEndThreshold = -0.85f, yNegativeStartThreshold = -0.85f,
            yPositiveEndThreshold = 0.9f, yPositiveStartThreshold = 0.9f,
            attachmentAnimationTime = 750, relativelyTrimAnimationTime = true, attachmentAnimationInterpolator = DecelerateInterpolator()
        )

        if (info.snoozed || info.preliminary){
            snoozeView.visibility = View.GONE
            binding.alarmSnoozeBracket.visibility = View.GONE
            binding.alarmSnoozeFiller.visibility = View.GONE

            driver = InterfaceUtils.DragViewDriver( yNegativeMax = -upDistance,
                yNegativeEndThreshold = -0.85f, yNegativeStartThreshold = -0.85f,
                attachmentAnimationTime = 750, relativelyTrimAnimationTime = true, attachmentAnimationInterpolator = DecelerateInterpolator()
            )
        }

        driver.onViewAttachmentStarted = { sides, alignment->
            val dismissAttached = sides[3] && alignment[3]
            val snoozeAttached = sides[2] && alignment[2]

            if (dismissAttached) dismissFun()
            if (snoozeAttached) snoozeFun()
        }
        driver.addMovableViews(draggableView)
        draggableView.setOnTouchListener(driver.controlTouchListener)

        val viewWidth = snoozeView.measuredWidth.toFloat()
        val fillerWidth = viewWidth * 1.29
        val bracketWidth = viewWidth * 1.19

        var lp = binding.alarmSnoozeFiller.layoutParams
        lp.width = fillerWidth.toInt()
        binding.alarmSnoozeFiller.apply {
            layoutParams = lp
            setBackgroundColor(context.getColor(R.color.mild_pitchSub))
        }
        lp = binding.alarmSnoozeBracket.layoutParams
        lp.width = bracketWidth.toInt()
        binding.alarmSnoozeBracket.layoutParams = lp

        binding.alarmDismissFiller.setBackgroundColor(applicationContext.getColor(R.color.mild_pitchSub))

        binding.alarmDismissView.setOnClickListener {
            binding.alarmDisplayClockDigitOne.alpha = 0f
            binding.alarmDisplayClockDigitTwo.alpha = 0f
            setupDigitClock()
        }
    }

    private val fadingDur = 150L
    private val bracketDur = 2400L
    private val exitDelay = 4000L
    private fun dismissFun(){
        alarmHandled = true

        draggableView.animate().apply {
            duration = fadingDur
            alpha(0f)
            start()
        }
        binding.alarmDismissFiller.animate().apply {
            duration = bracketDur
            startDelay = fadingDur + 100
            alpha(1f)
            start()
        }


        AED.helpDismiss(applicationContext, info.id, repo)
        unbind()

        sl.ex()
        Handler(this.mainLooper).postDelayed({finish()}, exitDelay)
    }

    private fun snoozeFun(){
        alarmHandled = true

        draggableView.animate().apply {
            duration = fadingDur
            alpha(0f)
            start()
        }
        binding.alarmSnoozeFiller.animate().apply {
            duration = bracketDur
            startDelay = fadingDur + 100
            alpha(1f)
            start()
        }

        AED.helpSnooze(applicationContext, info.id, repo)
        unbind()

        sl.ex()
        Handler(this.mainLooper).postDelayed({finish()}, exitDelay)
    }

    @Deprecated("Deprecated in Java", ReplaceWith("Unit"))
    override fun onBackPressed() = Unit

    companion object{
        const val alarmingDelay = 3500L
    }
}
