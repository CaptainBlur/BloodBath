package com.vova9110.bloodbath.alarmsUI

import android.app.Application
import android.content.Context
import android.content.res.TypedArray
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import android.widget.Button
import androidx.appcompat.widget.AppCompatImageView
import androidx.constraintlayout.widget.ConstraintLayout
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

    //TODO GET RID OF THIS!!!
    fun clearOrFill(fill: Boolean){
        if (fill) fAHandler.fill()
        else fAHandler.clear()
    }
}

sealed class TargetedHandler(context: Context, view: AdjustableView, globalRect: Rect){
    init {
        view.makeAdjustments(globalRect)
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

interface AdjustableView{

    /**
     * 1. First step of normalization occurs between at least one global side and a chosen side of a view (need to assign at least one multiplier).
     * Side with the untouched multiplier will remain as it is
     * 2. Second side of a view is always less than the first one, so we assume the sides are even
     */
    fun adjust(context: Context, view: View, attributes: TypedArray, globalRect: Rect, logging: Boolean){
        if (logging)
            context.resources.getResourceEntryName(view.id)?.let { slU.fr("adjusting View with id: *$it*") }

        val mWlH = attributes.getFraction(R.styleable.AdjustableView_width_toLocal_height_multiplier, 1,1, -1f)
        val mWgW = attributes.getFraction(R.styleable.AdjustableView_width_toGlobal_multiplier, 1,1, -1f)
        val mHlW = attributes.getFraction(R.styleable.AdjustableView_height_toLocal_width_multiplier, 1,1, -1f)
        val mHgH = attributes.getFraction(R.styleable.AdjustableView_height_toGlobal_multiplier, 1,1, -1f)
        attributes.recycle()

        val params = view.layoutParams

        with(params){
            if (!mWgW.equals(-1f)) width = (((globalRect.right - globalRect.left)).toFloat() * mWgW).toInt().also { if (logging) slU.fst("width: $it") }
            if (!mWlH.equals(-1f)) width = (height.toFloat() * mWlH).toInt().also { if (logging) slU.fst("width: $it") }

            if (!mHgH.equals(-1f)) height = (((globalRect.bottom - globalRect.top)).toFloat() * mHgH).toInt().also { if (logging) slU.fst("height: $it") }
            if (!mHlW.equals(-1f)) height = (width.toFloat() * mHlW).toInt().also { if (logging) slU.fst("height: $it") }
        }

        view.layoutParams = params
    }
    fun makeAdjustments(globalRect: Rect)
}

class AdjustableImageView(context: Context, attrs: AttributeSet): AppCompatImageView(context, attrs), AdjustableView{
    private val obtained = context.obtainStyledAttributes(attrs, R.styleable.AdjustableView)
    override fun makeAdjustments(globalRect: Rect) = super.adjust(context, this, obtained, globalRect, false)
}
class AdjustableConstraintLayout(context: Context, attrs: AttributeSet): ConstraintLayout(context, attrs), AdjustableView{
    private val obtained = context.obtainStyledAttributes(attrs, R.styleable.AdjustableView)
    override fun makeAdjustments(globalRect: Rect) = super.adjust(context, this, obtained, globalRect, false)
}
class AdjustableRecyclerView(context: Context, attrs: AttributeSet): RecyclerView(context, attrs), AdjustableView{
    private val obtained = context.obtainStyledAttributes(attrs, R.styleable.AdjustableView)
    override fun makeAdjustments(globalRect: Rect) = super.adjust(context, this, obtained, globalRect, true)
}
class AdjustableButton(context: Context, attrs: AttributeSet): androidx.appcompat.widget.AppCompatButton(context, attrs), AdjustableView{
    private val obtained = context.obtainStyledAttributes(attrs, R.styleable.AdjustableView)
    override fun makeAdjustments(globalRect: Rect) = super.adjust(context, this, obtained, globalRect, false)
}
