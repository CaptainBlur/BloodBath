package com.vova9110.bloodbath.alarmsUI

import android.graphics.Rect
import android.view.View
import android.view.View.OnLongClickListener
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.vova9110.bloodbath.R
import io.reactivex.rxjava3.observers.DisposableObserver
import io.reactivex.rxjava3.subjects.AsyncSubject

class MainActivityHandler(val supervisor: UISupervisor) {
    lateinit var activity: AppCompatActivity
    lateinit var fAHandler: FreeAlarmsHandler


    fun init(activity: AppCompatActivity){
        slU.en()
        this.activity = activity

        val targetWaiter: AsyncSubject<View> = AsyncSubject.create()
        targetWaiter.subscribe(object : DisposableObserver<View>() {
            override fun onNext(t: View) = supervisor.onMainActivityReady(t)
            override fun onError(e: Throwable) = Unit
            override fun onComplete() = this.dispose()
        })
        fun onTargetReady(target: View) = with(targetWaiter){
            onNext(target); onComplete()
        }
        val view = activity.window.decorView
        view.viewTreeObserver.addOnGlobalLayoutListener { onTargetReady(view) }
    }

    fun prepareActivity(){
        fAHandler = supervisor.fAHandler

        with(activity){
            findViewById<View>(R.id.buttonPower)
                .setOnClickListener { fAHandler.createUsual() }
            findViewById<View>(R.id.buttonPower)
                .setOnLongClickListener {
                    fAHandler.deleteUsual()
                    true
                }

            findViewById<View>(R.id.buttonFill).setOnClickListener { fAHandler.fill() }
            findViewById<View>(R.id.buttonFill).setOnLongClickListener {
                fAHandler.clear()
                true
            }

        }
    }

    fun adjustOthers(globalRect: Rect){
        with(activity) {
            findViewById<AdjustableImageView>(R.id.rv_top_cap).makeAdjustments(globalRect)
            findViewById<AdjustableImageView>(R.id.rv_bottom_cap).makeAdjustments(globalRect)
        }
    }

    fun transmitError(code: Int){
        Toast.makeText(activity, "ERROR!!!", Toast.LENGTH_LONG).show()
    }
}