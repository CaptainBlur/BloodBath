package com.foxstoncold.youralarm.alarmsUI

import android.Manifest
import android.animation.Animator
import android.animation.TimeInterpolator
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Resources
import android.os.Build
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.ViewPropertyAnimator
import android.view.animation.LinearInterpolator
import kotlin.math.abs
import kotlin.math.max

class InterfaceUtils {


    class SPInterlayer(context: Context, prefName: String){
        val sp = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
        private val edit = sp.edit()

        fun setElement(token: String, value: Int){
            edit.putInt(token, value)
            edit.commit()
        }
        fun setElement(token: String, value: Float){
            edit.putFloat(token, value)
            edit.commit()
        }
        fun setElement(token: String, value: Boolean){
            edit.putBoolean(token, value)
            edit.commit()
        }
        fun setElement(token: String, value: String) {
            edit.putString(token, value)
            edit.commit()
        }
    }


    class Contractor(private val lowestVal: Float, private val highestVal: Float) {
        private val divisionVal = 1f / (highestVal - lowestVal)
        init {
            assert(lowestVal >= 0f && lowestVal < highestVal && lowestVal <= 1f)
            assert(highestVal >= 0f && highestVal > lowestVal && highestVal <= 1f)
        }

        fun contract(fraction: Float): Float{
            if (fraction < lowestVal) return 0f
            else if (fraction > highestVal) return 1f

            //creating new scale by increasing division value
            val newScale = fraction * divisionVal
            //normalizing to 1 (high) and 0 (low)
            val normHigh = newScale / (divisionVal * highestVal)
            val normLow = newScale - (divisionVal * lowestVal)
            //mixing and normalizing to high
            val end = ((normHigh + normLow) / normHigh) - 1

//                    slU.i("$fraction  $divisionVal    $newScale  $normHigh  $normLow  $end")
            return end
        }

        fun contractReversed(fraction: Float): Float{
            return contract(abs(fraction - 1))
        }
    }


    class DragViewDriver(
        private val xNegativeMax: Int = 0,
        private val xPositiveMax: Int = 0,
        private val yNegativeMax: Int = 0,
        private val yPositiveMax: Int = 0,
        private val xNegativeStartThreshold: Float = 0f,
        private val xPositiveStartThreshold: Float = 0f,
        private val yNegativeStartThreshold: Float = 0f,
        private val yPositiveStartThreshold: Float = 0f,
        private val xNegativeEndThreshold: Float = 0f,
        private val xPositiveEndThreshold: Float = 0f,
        private val yNegativeEndThreshold: Float = 0f,
        private val yPositiveEndThreshold: Float = 0f,
        private val attachmentAnimationTime: Int = 0,
        private val relativelyTrimAnimationTime: Boolean = false,
        private val attachmentAnimationInterpolator: TimeInterpolator = LinearInterpolator(),
    ){
        private val xNegativeEnabled = xNegativeMax<0
        private val xPositiveEnabled = xPositiveMax>0
        private val yNegativeEnabled = yNegativeMax<0
        private val yPositiveEnabled = yPositiveMax>0

        private val xNegativeAttachment = xNegativeStartThreshold<0 || xNegativeEndThreshold<0
        private val xPositiveAttachment = xPositiveStartThreshold>0 || xPositiveEndThreshold>0
        private val yNegativeAttachment = yNegativeStartThreshold<0 || yNegativeEndThreshold<0
        private val yPositiveAttachment = yPositiveStartThreshold>0 || yPositiveEndThreshold>0

        private var layoutStartCoordinatesTaken = false

        init {
            if (xNegativeAttachment) assert(xNegativeStartThreshold > -1f) { "inappropriate value for *${this::xNegativeStartThreshold.name}*" }
            if (xPositiveAttachment) assert(xPositiveStartThreshold < 1f) { "inappropriate value for *${this::xPositiveStartThreshold.name}*" }
            if (yNegativeAttachment) assert(yNegativeStartThreshold > -1f) { "inappropriate value for *${this::yNegativeStartThreshold.name}*" }
            if (yPositiveAttachment) assert(yPositiveStartThreshold < 1f) { "inappropriate value for *${this::yPositiveStartThreshold.name}*" }
            if (xNegativeAttachment) assert(xNegativeEndThreshold > -1f) { "inappropriate value for *${this::xNegativeEndThreshold.name}*" }
            if (xPositiveAttachment) assert(xPositiveEndThreshold < 1f) { "inappropriate value for *${this::xPositiveEndThreshold.name}*" }
            if (yNegativeAttachment) assert(yNegativeEndThreshold > -1f) { "inappropriate value for *${this::yNegativeEndThreshold.name}*" }
            if (yPositiveAttachment) assert(yPositiveEndThreshold < 1f) { "inappropriate value for *${this::yPositiveEndThreshold.name}*" }

            if (attachmentAnimationTime!=0) assert(attachmentAnimationTime>0) { "attachment animation time cannot be negative" }
        }

        private val movableViews = ArrayList<View>()
        fun addMovableViews(vararg mViews: View){
            for (view in mViews) movableViews.add(view)
        }
        var onViewAttachmentStarted: (BooleanArray, BooleanArray)-> Unit = { _, _ ->}
        var onViewAttached: (BooleanArray, BooleanArray)-> Unit = { _, _ ->}

        fun recheckIncompleteAnimations(){

        }

        fun moveByNegativeX(toSidePos: Boolean){
//            if (toSidePos)
        }

        private fun animateInstantMove(view: View, startRelativeCoordinates: Pair<Float, Float>, startMoveCoordinates: Pair<Float, Float>, newTouchPosition: Pair<Float, Float>): Pair<Float, Float>{
            val preX = startMoveCoordinates.first + newTouchPosition.first
            val relX = preX - startRelativeCoordinates.first
            var moveX = startRelativeCoordinates.first

            val preY = startMoveCoordinates.second + newTouchPosition.second
            val relY = preY - startRelativeCoordinates.second
            var moveY = startRelativeCoordinates.second

            if (xPositiveEnabled && relX>=0) moveX = if (relX<xPositiveMax) preX else xPositiveMax + startRelativeCoordinates.first
            if (xNegativeEnabled && relX<=0) moveX = if (relX>xNegativeMax) preX else xNegativeMax + startRelativeCoordinates.first
            if (yPositiveEnabled && relY>=0) moveY = if (relY<yPositiveMax) preY else yPositiveMax + startRelativeCoordinates.second
            if (yNegativeEnabled && relY<=0) moveY = if (relY>yNegativeMax) preY else yNegativeMax + startRelativeCoordinates.second

//            sl.w("$preY  $relY  $moveY; ${startRelativeCoordinates.second}  ${startMoveCoordinates.second}  ${newTouchPosition.second}")

            view.animate()
                .y(moveY)
                .x(moveX)
                .setDuration(0)
                .setListener(object: Animator.AnimatorListener{
                    override fun onAnimationStart(animation: Animator) = Unit

                    override fun onAnimationEnd(animation: Animator) = Unit

                    override fun onAnimationCancel(animation: Animator) = Unit

                    override fun onAnimationRepeat(animation: Animator) = Unit

                })
                .start()

            return Pair(moveX, moveY)
        }

        private fun animateSideAttachment(view: View, startRelativeCoordinates: Pair<Float, Float>, moveCoordinates: Pair<Float, Float>, prevAlignment: Pair<Boolean, Boolean>): Pair<Boolean, Boolean>{
            val relX = moveCoordinates.first - startRelativeCoordinates.first
            var attachX = moveCoordinates.first
            var alignmentX = false

            val relY = moveCoordinates.second - startRelativeCoordinates.second
            var attachY = moveCoordinates.second
            var alignmentY = false

            val sideArray = BooleanArray(4)
            val alignmentArray = BooleanArray(4)

            if (xPositiveAttachment && relX>=0){
                val relFractionX = relX / xPositiveMax
                if (!prevAlignment.first) attachX = if (relFractionX < xPositiveStartThreshold) startRelativeCoordinates.first else xPositiveMax + startRelativeCoordinates.first.also { alignmentX = true }
                if (prevAlignment.first) attachX = if (relFractionX > xPositiveEndThreshold) xPositiveMax + startRelativeCoordinates.first.also { alignmentX = true } else startRelativeCoordinates.first
                sideArray[0] = true
                alignmentArray[0] = alignmentX
//                sl.i("$relX  $overlapX")
            }
            if (xNegativeAttachment && relX<=0){
                val relFractionX = -(relX / xNegativeMax)
                if (!prevAlignment.first) attachX = if (relFractionX > xNegativeStartThreshold) startRelativeCoordinates.first else xNegativeMax + startRelativeCoordinates.first.also { alignmentX = true }
                if (prevAlignment.first) attachX = if (relFractionX < xNegativeEndThreshold) xNegativeMax + startRelativeCoordinates.first.also { alignmentX = true } else startRelativeCoordinates.first
                sideArray[1] = true
                alignmentArray[1] = alignmentX
//                sl.i("$relX  $overlapX")
            }
            if (yPositiveAttachment && relY>=0){
                val relFractionY = relY / yPositiveMax
                if (!prevAlignment.second) attachY = if (relFractionY < yPositiveStartThreshold) startRelativeCoordinates.second else yPositiveMax + startRelativeCoordinates.second.also { alignmentY = true }
                if (prevAlignment.second) attachY = if (relFractionY > yPositiveEndThreshold) yPositiveMax + startRelativeCoordinates.second.also { alignmentY = true } else startRelativeCoordinates.second
                sideArray[2] = true
                alignmentArray[2] = alignmentY
//                sl.i("$relX  $overlapX")
            }
            if (yNegativeAttachment && relY<=0){
                val relFractionY = -(relY / yNegativeMax)
                if (!prevAlignment.second) attachY = if (relFractionY > yNegativeStartThreshold) startRelativeCoordinates.second else yNegativeMax + startRelativeCoordinates.second.also { alignmentY = true }
                if (prevAlignment.second) attachY = if (relFractionY < yNegativeEndThreshold) yNegativeMax + startRelativeCoordinates.second.also { alignmentY = true } else startRelativeCoordinates.second
                sideArray[3] = true
                alignmentArray[3] = alignmentY
//                sl.i("$relY  $relFractionY")
            }
//            if (xNegativeAttachment && relX<=0){
//                val overlapX = relX / xPositiveMax
//                attachX = if (overlapX > xNegativeThreshold) startRelativeCoordinates.first else xNegativeMax + startRelativeCoordinates.first
//            }
//            if (yPositiveAttachment && relY>=0){
//                val overlapY = relY / yPositiveMax
//                attachY = if (overlapY < yPositiveThreshold) startRelativeCoordinates.second else yPositiveMax + startRelativeCoordinates.second
//            }
//            if (yNegativeAttachment && relY<=0){
//                val overlapY = relY / yPositiveMax
//                attachY = if (overlapY > yNegativeThreshold) startRelativeCoordinates.second else yNegativeMax + startRelativeCoordinates.second
//            }

            var animationTime = if (attachmentAnimationTime > 0) attachmentAnimationTime.toLong() else 0L
            if (relativelyTrimAnimationTime){
                val completeTime = animationTime

                //todo make it through Pythagorean theorem along with X axis
                val yDistance = abs(attachY - moveCoordinates.second)
                val yTimeFraction = yDistance / max(-yNegativeMax, yPositiveMax).toFloat()
                animationTime = (completeTime * yTimeFraction).toLong()

//                sl.i("frac: $relFractionY, dist: $yDistance, timeFraction: $yTimeFraction, time: $yEffectiveTime")
//                sl.i(yDistance)
//                sl.i(relFractionY)
            }

//            sl.i("$prevAlignment; $alignmentX  $alignmentY")
            view.animate()
                .x(attachX)
                .y(attachY)
                .setDuration(animationTime)
                .setInterpolator(attachmentAnimationInterpolator)
                .setListener(object: Animator.AnimatorListener{
                    override fun onAnimationStart(animation: Animator) = Unit

                    override fun onAnimationEnd(animation: Animator) = onViewAttached(sideArray, alignmentArray)

                    override fun onAnimationCancel(animation: Animator) = Unit

                    override fun onAnimationRepeat(animation: Animator) = Unit

                })
                .start()

            onViewAttachmentStarted(sideArray, alignmentArray)
            return Pair(alignmentX, alignmentY)
        }

        //This coordinates are taken as a counterpart to the new coordinates delivered through MotionEvent
        private lateinit var startMoveCoordinatesArray: ArrayList<Pair<Float, Float>>
        //This coordinates represent positions relative to the Layout and taken for calculating distance of the current continuous drag action
        private var startRelativeCoordinatesArray = ArrayList<Pair<Float, Float>>()
        //This are intermittent coordinates representing last used 'move' values
        private val moveCoordinates = ArrayList<Pair<Float, Float>>()
        private val alignmentInfo = ArrayList<Pair<Boolean, Boolean>>()
        @SuppressLint("ClickableViewAccessibility")
        val controlTouchListener = View.OnTouchListener { v, event ->
            if (movableViews.isEmpty()) return@OnTouchListener false

            when(event.action){
                MotionEvent.ACTION_DOWN->{
                    startMoveCoordinatesArray = ArrayList(movableViews.size)
                    for (view in movableViews){
                        moveCoordinates.add(Pair(0f,0f))
                        if (!layoutStartCoordinatesTaken){
                            startRelativeCoordinatesArray.add(Pair(view.x, view.y))
                            alignmentInfo.add(Pair(false, false))
                        }
                        startMoveCoordinatesArray.add(Pair(view.x - event.rawX, view.y - event.rawY))
                    }
                    layoutStartCoordinatesTaken = true
                }
                MotionEvent.ACTION_MOVE->{
                    for (i in movableViews.indices){
                        moveCoordinates[i] = animateInstantMove(movableViews[i], startRelativeCoordinatesArray[i], startMoveCoordinatesArray[i], Pair(event.rawX, event.rawY))
                    }
                }
                MotionEvent.ACTION_UP->{
                    for (i in movableViews.indices) alignmentInfo[i] = animateSideAttachment(movableViews[i], startRelativeCoordinatesArray[i], moveCoordinates[i], alignmentInfo[i])
                }
            }

            true
        }
        companion object{
            const val EVENT_DOWN = 895
            const val EVENT_UP = 769
            const val EVENT_MOVE = 143
            const val EVENT_THRESHOLD = 429
            const val EVENT_ATTACHED_START = 849
            const val EVENT_ATTACHED_END = 244
        }
    }

    companion object{
        fun Number.toPx() = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            this.toFloat(),
            Resources.getSystem().displayMetrics)

        fun checkNoisePermission(c: Context): Boolean = c.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

        fun checkActivityPermission(c: Context): Boolean{
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                c.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED
            else true
        }

        fun checkStepSensors(c: Context): Boolean{
            val pManager = c.packageManager
            val one = pManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_STEP_DETECTOR)
            val two = pManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_STEP_COUNTER)
            if (!(one && two)) sl.wp("Step detection sensors not presented!")

            return one && two
        }

        fun startAlphaAnimation(view: View, toValue: Float, duration: Long, interpolator: TimeInterpolator = LinearInterpolator(), listener: Animator.AnimatorListener? = null){
            val animator: ViewPropertyAnimator = view.animate()

            animator
                .alpha(toValue)
                .setDuration(duration)
                .interpolator = interpolator

            listener?.let { animator.setListener(listener) }
            animator.start()
        }

    }
}