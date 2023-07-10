package com.vova9110.bloodbath.alarmsUI

import android.content.Context
import android.content.res.TypedArray
import android.graphics.Rect
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.vova9110.bloodbath.R
import kotlin.math.roundToInt

interface AdjustableView{
    val obtained: TypedArray

    /**
     * 1. First step of normalization occurs between at least one global side and a chosen side of a view (need to assign at least one multiplier).
     * Side with the untouched multiplier will remain as it is
     * 2. Second side of a view is always less than the first one, so we assume the sides are even
     * 3. Also, by adjusting we save drawables (be aware of NPE)
     */
    fun adjust(context: Context, view: View, globalRect: Rect, logging: Boolean): Rect{
        if (logging)
            context.resources.getResourceEntryName(view.id)?.let { slU.fr("adjusting View with id: *$it*") }

        val mWlH = obtained.getFraction(R.styleable.AdjustableView_width_toLocal_height_multiplier, 1,1, -1f)
        val mWgW = obtained.getFraction(R.styleable.AdjustableView_width_toGlobal_multiplier, 1,1, -1f)
        val mHlW = obtained.getFraction(R.styleable.AdjustableView_height_toLocal_width_multiplier, 1,1, -1f)
        val mHgH = obtained.getFraction(R.styleable.AdjustableView_height_toGlobal_multiplier, 1,1, -1f)
        val mTsH = obtained.getFraction(R.styleable.AdjustableView_textSize_toHeight, 1,1, -1f)

        val params = view.layoutParams

        with(params){
            if (!mWgW.equals(-1f)) width = (((globalRect.right - globalRect.left)).toFloat() * mWgW).roundToInt().also { if (logging) slU.fst("width: $it") }
            if (!mWlH.equals(-1f)) width = (height.toFloat() * mWlH).roundToInt().also { if (logging) slU.fst("width: $it") }

            if (!mHgH.equals(-1f)) height = (((globalRect.bottom - globalRect.top)).toFloat() * mHgH).roundToInt().also { if (logging) slU.fst("height: $it") }
            if (!mHlW.equals(-1f)) height = (width.toFloat() * mHlW).roundToInt().also { if (logging) slU.fst("height: $it") }

            if (!mTsH.equals(-1f)) (view as TextView).setTextSize(TypedValue.COMPLEX_UNIT_PX, height.times(mTsH).also { if (logging) slU.fst("text height: $it") })
        }

        view.layoutParams = params
        obtained.recycle()

        return Rect(0,0, params.width, params.height)
    }

    fun amend(view: View, lpRect: Rect){
        view.layoutParams.apply {
            width = lpRect.width()
            height = lpRect.height()
        }
    }
    fun makeAdjustments(globalRect: Rect): Rect
    fun makeAmends(lpRect: Rect)
}
const val logging = false

class AdjustableImageView(context: Context, attrs: AttributeSet): AppCompatImageView(context, attrs), AdjustableView{
    override val obtained = context.obtainStyledAttributes(attrs, R.styleable.AdjustableView)
    override fun makeAdjustments(globalRect: Rect) = super.adjust(context, this, globalRect, logging)
    override fun makeAmends(lpRect: Rect) = amend(this, lpRect).also { obtained.recycle() }
}
class AdjustableTextView(context: Context, attrs: AttributeSet): androidx.appcompat.widget.AppCompatTextView(context, attrs), AdjustableView{
    override val obtained = context.obtainStyledAttributes(attrs, R.styleable.AdjustableView)
    override fun makeAdjustments(globalRect: Rect) = super.adjust(context, this, globalRect, logging)
    override fun makeAmends(lpRect: Rect) = amend(this, lpRect).also { obtained.recycle() }
}
class AdjustableRecyclerView(context: Context, attrs: AttributeSet): RecyclerView(context, attrs), AdjustableView{
    override val obtained = context.obtainStyledAttributes(attrs, R.styleable.AdjustableView)
    override fun makeAdjustments(globalRect: Rect) = super.adjust(context, this, globalRect, true)
    override fun makeAmends(lpRect: Rect) = amend(this, lpRect).also { obtained.recycle() }

}
class AdjustableButton(context: Context, attrs: AttributeSet): androidx.appcompat.widget.AppCompatButton(context, attrs), AdjustableView{
    override val obtained = context.obtainStyledAttributes(attrs, R.styleable.AdjustableView)
    override fun makeAdjustments(globalRect: Rect) = super.adjust(context, this, globalRect, logging)
    override fun makeAmends(lpRect: Rect) = amend(this, lpRect).also { obtained.recycle() }
}
open class AdjustableCompoundButton(context: Context, attrs: AttributeSet?): CompoundButton(context, attrs), AdjustableView{
    override val obtained = context.obtainStyledAttributes(attrs, R.styleable.AdjustableView)
    init { if (attrs==null) obtained.recycle() }
    override fun makeAdjustments(globalRect: Rect) = super.adjust(context, this, globalRect, logging)
    override fun makeAmends(lpRect: Rect) = amend(this, lpRect).also { obtained.recycle() }
}

class AdjustableConstraintLayout(context: Context, attrs: AttributeSet): ConstraintLayout(context, attrs), AdjustableView{
    override val obtained = context.obtainStyledAttributes(attrs, R.styleable.AdjustableView)
    override fun makeAdjustments(globalRect: Rect) = super.adjust(context, this, globalRect, logging)
    override fun makeAmends(lpRect: Rect) = amend(this, lpRect).also { obtained.recycle() }
}
class AdjustableLinearLayout(context: Context, attrs: AttributeSet): LinearLayout(context, attrs), AdjustableView{
    override val obtained = context.obtainStyledAttributes(attrs, R.styleable.AdjustableView)
    override fun makeAdjustments(globalRect: Rect) = super.adjust(context, this, globalRect, true)
    override fun makeAmends(lpRect: Rect) = amend(this, lpRect).also { obtained.recycle() }
    fun recycleAttributes() = obtained.recycle()
}