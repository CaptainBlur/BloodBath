package com.vova9110.bloodbath.alarmsUI

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.SparseArray
import android.view.View
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentManager
import com.google.android.renderscript.Toolkit
import com.vova9110.bloodbath.MyApp
import com.vova9110.bloodbath.R
import com.vova9110.bloodbath.SplitLoggerUI
import com.vova9110.bloodbath.alarmScreenBackground.AlarmRepo
import java.lang.Exception
import kotlin.math.roundToInt

typealias slU = SplitLoggerUI.UILogger

class UISupervisor(val app: Application) {
    val repo: AlarmRepo = (app as MyApp).component.repo
    lateinit var ratios: RatiosResolver
        private set
    lateinit var drawables: DrawablesAide
        private set
    lateinit var fragmentManager: FragmentManager
        private set

    val mAHandler = MainActivityHandler(this)
    lateinit var fAHandler: FreeAlarmsHandler
        private set

    fun onMainActivityReady(view: View){
        slU.i("MA is ready. Start distributing")

        val rect = Rect()
        view.getWindowVisibleDisplayFrame(rect)

        ratios = RatiosResolver(app, rect)
        drawables = DrawablesAide(app, ratios)
        fragmentManager = mAHandler.activity.supportFragmentManager

        //Other dependencies we''l take from there manually
        fAHandler = FreeAlarmsHandler(this, view.findViewById(R.id.recyclerview), rect) { mAHandler.transmitError(1) }
        mAHandler.adjustOthers(rect)

        mAHandler.prepareActivity()
    }

    //TODO GET RID OF THIS!!!
    fun clearOrFill(fill: Boolean){
        if (fill) fAHandler.fill()
        else fAHandler.clear()
    }
}

sealed class TargetedHandler(view: AdjustableView, globalRect: Rect){
    init {
        view.makeAdjustments(globalRect)
        view.recycleAttributes()
        view as View
        view.measure(0,0)
    }
}
interface ErrorReceiver{
    val transmitterMethod: ()->Unit
    fun receiveError(ex: Exception) {
        transmitterMethod()
        handleError(ex)
    }
    fun handleError(ex: Exception)
}


