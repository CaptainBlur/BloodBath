package com.foxstoncold.youralarm

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import com.foxstoncold.youralarm.SplitLogger.Companion.en
import com.foxstoncold.youralarm.SplitLogger.Companion.ex
import com.foxstoncold.youralarm.alarmsUI.AdjustableImageView
import com.foxstoncold.youralarm.alarmsUI.AdjustableRecyclerView
import com.foxstoncold.youralarm.alarmsUI.ErrorHandlerImpl
import com.foxstoncold.youralarm.alarmsUI.FA_to_M_Callback
import com.foxstoncold.youralarm.alarmsUI.InterfaceUtils
import com.foxstoncold.youralarm.alarmsUI.SettingsFragment
import com.foxstoncold.youralarm.alarmsUI.UISupervisor
import com.foxstoncold.youralarm.alarmsUI.slU
import com.foxstoncold.youralarm.databinding.ActivityMainBinding
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.skydoves.balloon.ArrowPositionRules
import com.skydoves.balloon.Balloon
import com.skydoves.balloon.BalloonAnimation
import com.skydoves.balloon.BalloonSizeSpec
import com.skydoves.balloon.balloon
import com.skydoves.balloon.showAlignBottom
import io.reactivex.rxjava3.observers.DisposableObserver
import io.reactivex.rxjava3.subjects.AsyncSubject

class MainActivity : AppCompatActivity(), M_to_FA_Callback {
    private val sl = SplitLogger()
    private lateinit var binding: ActivityMainBinding
    private lateinit var _vm: MainViewModel
    private lateinit var supervisor: UISupervisor

    private lateinit var faCallback: FA_to_M_Callback


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
        slU.en()

//        Date firstDate = new Date();
//        Date secDate = firstDate;
//        secDate.setTime(secDate.getTime() + 10000);
//        firstDate = null;
//        SplitLogger.i(secDate);
//        SplitLogger.i(firstDate);

        val vm: MainViewModel by viewModels()
        _vm = vm
        supervisor = _vm.supervisor

        val rootView = this@MainActivity.window.decorView.rootView.findViewById<ConstraintLayout>(R.id.main_constraint_layout)
        LayoutInflater.from(this@MainActivity).inflate(R.layout.recycler_view, rootView, true)
        slU.f("RV inflated")

        val fragment = SettingsFragment().apply {
            onBuilt = ::onSettingsReady
        }
        fun addOrHideFragment() = kotlin.run {
            supportFragmentManager.commit {
                if (fragment.isAdded){
                    remove(fragment)
                    binding.settingsButtonBack.apply{
                        visibility = View.INVISIBLE
                    }
                    binding.settingsButtonIcon.apply{
                        visibility = View.INVISIBLE
                    }
                }
                else add(R.id.main_fragment_container, fragment)
            }
        }
        fun replaceFragment() = run {
            supportFragmentManager.commit {
                setReorderingAllowed(true)
                replace(R.id.main_fragment_container, fragment)
            }
        }

        //creating oneShot listener for MainActivity view's layout
        val targetWaiter: AsyncSubject<View> = AsyncSubject.create()
        targetWaiter.subscribe(object : DisposableObserver<View>() {
            override fun onNext(t: View) = run {
                supervisor.onMainActivityReady(this@MainActivity, t)

                val rect= Rect()
                t.getWindowVisibleDisplayFrame(rect)
                prepareRV(rect)
                replaceFragment()
            }
            override fun onError(e: Throwable) = Unit
            override fun onComplete() = this.dispose()
        })
        fun onTargetReady(target: View) = with(targetWaiter){
            onNext(target); onComplete()
        }
        val view = this.window.decorView
        view.viewTreeObserver.addOnGlobalLayoutListener { onTargetReady(view) }

        prepareReceiver()
        prepareViews()

        findViewById<FloatingActionButton>(R.id.fab).setOnClickListener {
            addOrHideFragment()
        }
    }


    private fun prepareRV(rect: Rect){
        val recyclerView = findViewById<AdjustableRecyclerView>(R.id.recyclerview)
        val topCap = findViewById<AdjustableImageView>(R.id.rv_top_cap)

        recyclerView.makeAdjustments(rect)
        topCap.makeAdjustments(rect)

        val lp = topCap.layoutParams as ConstraintLayout.LayoutParams
        lp.leftToLeft = R.id.recyclerview
        lp.topToTop = R.id.recyclerview
        topCap.layoutParams = lp

        supervisor.onRecyclerViewReady(recyclerView)

        val fatomCallback: FA_to_M_Callback by supervisor.callbacks
        faCallback = fatomCallback
    }

    private fun prepareReceiver(){
        val receiver = object: BroadcastReceiver(){
            override fun onReceive(context: Context?, intent: Intent?) {
                slU.fr("broadcast received")
                intent!!

                if(intent.action.equals(RELOAD_RV_REQUEST)){
                    _vm.dropRVReloadRequest()
                    if (this@MainActivity::faCallback.isInitialized){
                        faCallback.createBrandNewRecycler(false).also { slU.f("reloading pref") }
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(RELOAD_RV_REQUEST)
        }

        registerReceiver(receiver, filter)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun prepareViews(){
        val drawer = binding.mainDrawer.apply {
            this.addDrawerListener(object: DrawerLayout.DrawerListener{
                var oneShot = false
                var wasOpened = false

                override fun onDrawerSlide(drawerView: View, slideOffset: Float) {
                    if (slideOffset < 0.085f && !oneShot && wasOpened) {
                        drawerView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_RELEASE)
                        oneShot = true
                    }
                }
                override fun onDrawerOpened(drawerView: View){
                    wasOpened = true
                }

                override fun onDrawerClosed(drawerView: View) {
                    oneShot = false
                    wasOpened = false
                }

                override fun onDrawerStateChanged(newState: Int) = Unit
            })
        }

        binding.buttonFill.apply {
            setOnClickListener { faCallback.clearOrFill(true) }
            setOnLongClickListener { faCallback.clearOrFill(false); true }
        }
        binding.settingsButtonBack.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_PRESS)
            drawer.open()
        }

        binding.testView.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_RELEASE)
        }
        binding.testView2.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY_RELEASE)
        }
        binding.testView3.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }
        binding.testView4.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
        binding.testView5.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    private fun onSettingsReady(){
        binding.settingsButtonBack.apply{
            visibility = View.VISIBLE
            alpha = 0f
            InterfaceUtils.startAlphaAnimation(this, 1f, 2200, FastOutSlowInInterpolator())
        }
        binding.settingsButtonIcon.apply{
            visibility = View.VISIBLE
            alpha = 0f
            InterfaceUtils.startAlphaAnimation(this, 1f, 2200, FastOutSlowInInterpolator())
        }
    }

    fun transmitError(code: Int){
        when(code){
            ErrorHandlerImpl.RV_ERROR_CODE -> Toast.makeText(this, "RV ERROR!!!", Toast.LENGTH_LONG).show()
            ErrorHandlerImpl.FIRING_ERROR_CODE -> Toast.makeText(this, "FIRING ERROR!!!", Toast.LENGTH_LONG).show()
        }
        slU.i("showing error message")
    }

    fun provideStateSaver(): StateSaver = _vm

    override fun pingAddButtonVisibility() {
        TODO("Not yet implemented")
    }

    override fun getFM(): FragmentManager = supportFragmentManager

    override fun getParentVG(): ViewGroup = findViewById(R.id.main_constraint_layout)

    override fun onResume() {
        en()

        if (_vm.checkRVReloadRequest() && this::faCallback.isInitialized){
            faCallback.createBrandNewRecycler(false).also { slU.f("reloading pref") }
        }

        if (_vm.checkFiringError()) transmitError(ErrorHandlerImpl.FIRING_ERROR_CODE)

        super.onResume()
    }

    override fun onStop() {
        ex()
        if (this::faCallback.isInitialized) faCallback.onMainExiting()
        super.onStop()
    }

    companion object {
        const val RELOAD_RV_REQUEST = "rv_reload"
        const val FILL_DB = 3
        const val CLEAR_DB = 4
    }
}

interface M_to_FA_Callback{
    fun pingAddButtonVisibility()
    fun getFM(): FragmentManager
    fun getParentVG(): ViewGroup
}