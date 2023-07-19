package com.vova9110.bloodbath

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.media.AudioManager
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.vova9110.bloodbath.SplitLogger.Companion.en
import com.vova9110.bloodbath.SplitLogger.Companion.ex
import com.vova9110.bloodbath.alarmScreenBackground.FiringControlService
import com.vova9110.bloodbath.alarmsUI.AdjustableCompoundButton
import com.vova9110.bloodbath.alarmsUI.AdjustableImageView
import com.vova9110.bloodbath.alarmsUI.AdjustableRecyclerView
import com.vova9110.bloodbath.alarmsUI.ErrorHandlerImpl
import com.vova9110.bloodbath.alarmsUI.FA_to_M_Callback
import com.vova9110.bloodbath.alarmsUI.UISupervisor
import com.vova9110.bloodbath.alarmsUI.slU
import io.reactivex.rxjava3.observers.DisposableObserver
import io.reactivex.rxjava3.subjects.AsyncSubject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : AppCompatActivity(), M_to_FA_Callback {
    private val sl = SplitLogger()
    private lateinit var _vm: MainViewModel
    private lateinit var supervisor: UISupervisor

    private lateinit var faCallback: FA_to_M_Callback


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
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

        val rootView = this@MainActivity.window.decorView.rootView.findViewById<ConstraintLayout>(R.id.constraint_layout)
        LayoutInflater.from(this@MainActivity).inflate(R.layout.recycler_view, rootView, true)
        slU.f("RV inflated")

        //creating oneShot listener for MainActivity view's layout
        val targetWaiter: AsyncSubject<View> = AsyncSubject.create()
        targetWaiter.subscribe(object : DisposableObserver<View>() {
            override fun onNext(t: View) = run {
                supervisor.onMainActivityReady(this@MainActivity, t)

                val rect= Rect()
                t.getWindowVisibleDisplayFrame(rect)
                prepareRV(rect)
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

    private fun prepareViews(){
        val utilityButton = findViewById<Button>(R.id.buttonFill)
        utilityButton.setOnClickListener { faCallback.clearOrFill(true) }
        utilityButton.setOnLongClickListener { faCallback.clearOrFill(false); true }
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

    override fun getParentVG(): ViewGroup = findViewById(R.id.constraint_layout)

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