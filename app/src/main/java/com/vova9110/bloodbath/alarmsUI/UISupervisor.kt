package com.vova9110.bloodbath.alarmsUI

import android.app.Application
import android.content.Context
import android.content.res.TypedArray
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import androidx.appcompat.widget.AppCompatImageView
import androidx.recyclerview.widget.RecyclerView
import com.vova9110.bloodbath.MyApp
import com.vova9110.bloodbath.R
import com.vova9110.bloodbath.SplitLoggerUI
import java.lang.Exception

typealias slU = SplitLoggerUI.UILogger

class UISupervisor(private val app: Application) {
    private val repo = (app as MyApp).component.repo

    val mAHandler = MainActivityHandler(this)
    lateinit var fAHandler: FreeAlarmsHandler
        private set

    fun onMainActivityReady(view: View){
        slU.i("MA is ready. Start distributing")
        val rect = Rect()
        view.getWindowVisibleDisplayFrame(rect)

        fAHandler = FreeAlarmsHandler(repo, app.applicationContext, view.findViewById(R.id.recyclerview), rect) { mAHandler.transmitError(1) }
        mAHandler.adjustOthers(rect)

        mAHandler.prepareActivity()
    }
}

sealed class TargetedHandler(view: AdjustableView, globalRect: Rect){
    init {
        view.makeAdjustments(globalRect)
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

interface AdjustableView{

    /**
     * 1. First step of normalization occurs between at least one global side and a chosen side of a view (need to assign at least one multiplier).
     * Side with the untouched multiplier will remain as it is
     * 2. Second side of a view is always less than the first one, so we assume the sides are even
     */
    fun adjust(context: Context, view: View, attributes: TypedArray, globalRect: Rect){
        context.resources.getResourceEntryName(view.id)?.let { slU.fr("adjusting View with id: *$it*") }

        val mWlH = attributes.getFloat(R.styleable.AdjustableView_width_toLocal_height_multiplier, -1f)
        val mWgW = attributes.getFloat(R.styleable.AdjustableView_width_toGlobal_multiplier, -1f)
        val mHlW = attributes.getFloat(R.styleable.AdjustableView_height_toLocal_width_multiplier, -1f)
        val mHgH = attributes.getFloat(R.styleable.AdjustableView_height_toGlobal_multiplier, -1f)
        attributes.recycle()

        val params = view.layoutParams

        with(params){
            if (!mWgW.equals(-1f)) width = (((globalRect.right - globalRect.left)).toFloat() * mWgW).toInt().also { slU.fst("width: $it") }
            if (!mWlH.equals(-1f)) width = (height.toFloat() * mWlH).toInt().also { slU.fst("width: $it") }

            if (!mHgH.equals(-1f)) height = (((globalRect.bottom - globalRect.top)).toFloat() * mHgH).toInt().also { slU.fst("height: $it") }
            if (!mHlW.equals(-1f)) height = (width.toFloat() * mHlW).toInt().also { slU.fst("height: $it") }
        }

        view.layoutParams = params
    }
    fun makeAdjustments(globalRect: Rect)
}

class AdjustableImageView(context: Context, attrs: AttributeSet): AppCompatImageView(context, attrs), AdjustableView{
    init {
        slU.en()
    }
    private val obtained = context.obtainStyledAttributes(attrs, R.styleable.AdjustableView)
    override fun makeAdjustments(globalRect: Rect) = super.adjust(context, this, obtained, globalRect)
}
class AdjustableRecyclerView(context: Context, attrs: AttributeSet): RecyclerView(context, attrs), AdjustableView{
    private val obtained = context.obtainStyledAttributes(attrs, R.styleable.AdjustableView)
    override fun makeAdjustments(globalRect: Rect) = super.adjust(context, this, obtained, globalRect)
}
