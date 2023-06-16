package com.vova9110.bloodbath.alarmsUI

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.drawable.AnimationDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.PictureDrawable
import android.graphics.drawable.VectorDrawable
import android.util.SparseArray
import android.view.View
import android.view.animation.AlphaAnimation
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import com.caverock.androidsvg.RenderOptions
import com.caverock.androidsvg.SVG
import com.google.android.renderscript.BlendingMode
import com.google.android.renderscript.Toolkit
import com.vova9110.bloodbath.R
import kotlin.math.roundToInt



class RatiosResolver(private val context: Context, globalRect: Rect){
    val prefFrameRect: Rect
    val prefPowerRect: Rect
    val parentEnvoyRect: Rect
    val individualsTierOneRect: Rect

    init {
        var width = globalRect.width() *
                getFraction(R.fraction.rv_pref_frame_width)
        var height = width *
                getFraction(R.fraction.rv_pref_frame_height)
        prefFrameRect = Rect(0,0, width.roundToInt(),height.roundToInt())


        width = prefFrameRect.width() *
                getFraction(R.fraction.rv_pref_power_width)
        height = width *
                getFraction(R.fraction.rv_pref_power_height)
        prefPowerRect = Rect(0,0, width.roundToInt(),height.roundToInt())


        width = prefFrameRect.width() *
                getFraction(R.fraction.rv_pref_parent_envoy_width)
        height = width *
                getFraction(R.fraction.rv_pref_parent_envoy_height)
        parentEnvoyRect = Rect(0,0,width.roundToInt(), height.roundToInt())


        width = globalRect.width() *
                getFraction(R.fraction.rv_pref_tierOne_width)
        height = width
        individualsTierOneRect = Rect(0,0, width.roundToInt(), height.roundToInt())
    }

    private fun getFraction(resourceID: Int): Float = (context.resources.getFraction(resourceID, 1,1))
}


class DrawablesAide(private val context: Context, private val ratios: RatiosResolver){
    private val uncheckedDrawables = SparseArray<Drawable>()
    private val checkedDrawables = SparseArray<Drawable>()

    private val prefPowerAnimation_frameTime = (1f/60 * 1000).roundToInt()
    private var prefPowerAnimation_duration = 0

    companion object{
        const val rv_pref_frame_center = 848
        const val rv_pref_frame_right = 414
        const val rv_pref_power = 91
    }

    private fun getRGB(@ColorRes resID: Int): String{
        val color = context.getColor(resID)
        return "#" + Integer.toHexString(color).substring(2)
    }

    init {
        val timerStart = System.currentTimeMillis()
        lateinit var svg: SVG
        var drawableID: Int
        var paintDrawableID: Int
        lateinit var initialDrawable: Drawable
        lateinit var paintDrawable: Drawable
        var width: Int
        var height: Int
        lateinit var bitmap: Bitmap
        lateinit var paintBitmap: Bitmap
        lateinit var canvas: Canvas
        lateinit var paintCanvas: Canvas


//        svg = SVG.getFromAsset(context.assets, "rv_pref_frame_center.svg")
//        width = ratios.prefFrameRect.width()
//        height = ratios.prefFrameRect.height()
//        //radius should strictly depend on bitmap's size
//        val blurRad = 0.8 / 100 * width
//
//        bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
//        canvas = Canvas(bitmap)
//        with(svg){
//            documentWidth = width.toFloat()
//            documentHeight = height.toFloat()
//            //Finding group by ID argument
//            renderToCanvas(canvas, RenderOptions.create().css("#layer1 { stroke:${getRGB(R.color.mild_greyscalePresence)} }"))
//        }
//        bitmap = Toolkit.blur(bitmap, blurRad.roundToInt())
//        uncheckedDrawables.set(rv_pref_frame_center, BitmapDrawable(context.resources, bitmap))
//
//        bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
//        canvas = Canvas(bitmap)
//        svg.renderToCanvas(canvas, RenderOptions.create().css("#layer1 { stroke:${getRGB(R.color.mild_pitchSub)} }"))
//        bitmap = Toolkit.blur(bitmap, blurRad.roundToInt())
//        checkedDrawables.set(rv_pref_frame_center, BitmapDrawable(context.resources, bitmap))
//
//
//        svg = SVG.getFromAsset(context.assets, "rv_pref_frame_right.svg")
//        bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
//        canvas = Canvas(bitmap)
//        with(svg){
//            documentWidth = width.toFloat()
//            documentHeight = height.toFloat()
//            renderToCanvas(canvas, RenderOptions.create().css("#layer1 { stroke:${getRGB(R.color.mild_greyscalePresence)} }"))
//        }
//        bitmap = Toolkit.blur(bitmap, blurRad.roundToInt())
//        uncheckedDrawables.set(rv_pref_frame_right, BitmapDrawable(context.resources, bitmap))
//
//        bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
//        canvas = Canvas(bitmap)
//        svg.renderToCanvas(canvas, RenderOptions.create().css("#layer1 { stroke:${getRGB(R.color.mild_pitchSub)} }"))
//        bitmap = Toolkit.blur(bitmap, blurRad.roundToInt())
//        checkedDrawables.set(rv_pref_frame_right, BitmapDrawable(context.resources, bitmap))


        drawableID = rv_pref_power
        val pair = comprisePrefPowerAnimation()
        uncheckedDrawables.set(drawableID, pair.first)
        checkedDrawables.set(drawableID, pair.second)


        drawableID = rv_pref_frame_center
        val squarePair = comprisePrefFrameAnimation()
        uncheckedDrawables.set(drawableID, squarePair.first.first)
        checkedDrawables.set(drawableID, squarePair.first.second)

        drawableID = rv_pref_frame_right
        uncheckedDrawables.set(drawableID, squarePair.second.first)
        checkedDrawables.set(drawableID, squarePair.second.second)


        drawableID = R.drawable.rv_time_window
        initialDrawable = ContextCompat.getDrawable(context, drawableID)!!
        initialDrawable.setTintMode(PorterDuff.Mode.SCREEN)
        initialDrawable.setTint(context.getColor(R.color.mild_greyscaleLight))
        uncheckedDrawables.set(drawableID, initialDrawable)

        drawableID = R.drawable.rv_time_window_lit
        initialDrawable = ContextCompat.getDrawable(context, drawableID)!!
        initialDrawable.setTintMode(PorterDuff.Mode.SCREEN)
        initialDrawable.setTint(context.getColor(R.color.mild_pitchSub))
        checkedDrawables.set(R.drawable.rv_time_window, initialDrawable)

        //All tier one individual icons are normalized to one width

        drawableID = R.drawable.rv_pref_music
        paintDrawableID = R.drawable.rv_pref_music_paint
        initialDrawable = ContextCompat.getDrawable(context, drawableID)!!
        paintDrawable = ContextCompat.getDrawable(context, paintDrawableID)!!

        width = ratios.individualsTierOneRect.width()
        height = ratios.individualsTierOneRect.height()

        bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        canvas = Canvas(bitmap)
        initialDrawable.setBounds(0, 0, canvas.width, canvas.height)
        initialDrawable.draw(canvas)

        paintDrawable.setTint(context.getColor(R.color.mild_pitchRegular))
        paintBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        paintCanvas = Canvas(paintBitmap)
        paintDrawable.setBounds(0,0,paintCanvas.width, paintCanvas.height)
        paintDrawable.draw(paintCanvas)

        Toolkit.blend(BlendingMode.ADD, bitmap, paintBitmap)
        checkedDrawables.set(drawableID, BitmapDrawable(context.resources, paintBitmap))


        val timer = System.currentTimeMillis() - timerStart
        slU.f("Drawables preparation took ^$timer^ mills")
    }

    /*
    I decided to delegate task of calculating paths to Shapeshifter
    ValueAnimator helps to calculate colors transition
     */
    private fun comprisePrefPowerAnimation(): Pair<AnimationDrawable, AnimationDrawable>{
        val framesPathList = context.assets.list("rv_pref_power")!!
        //through animation frames, our drawables come to their final states,
        //which is not quite that I planned in the first place
        val checkedFramesRange = 0 .. 24
        val uncheckedFramesRange = 30 .. 54
        assert(checkedFramesRange.last-checkedFramesRange.first == uncheckedFramesRange.last-uncheckedFramesRange.first)

        val uncheckedDrawable = AnimationDrawable().apply { isOneShot = true }
        val checkedDrawable = AnimationDrawable().apply { isOneShot = true }

        for (i in framesPathList.indices){
            val string = framesPathList[i]
            val split = string.split(Regex("\\d+"))
            val num = string.substringAfter(split[0]).substringBefore(split[1]).toInt()

            val templateSVG = SVG.getFromAsset(context.assets, "rv_pref_power/$string")
            with(templateSVG){
                documentWidth = ratios.prefPowerRect.width().toFloat()
                documentHeight = ratios.prefPowerRect.height().toFloat()
            }
            val picture = PictureDrawable(templateSVG.renderToPicture(RenderOptions.create().css(
                "path#top { fill:${getRGB(R.color.mild_pitchSub)} }" +
                        "path#bottom { fill:${getRGB(R.color.mild_greyscaleLight)} }" +
                        //because Shapeshifter cannot process dashed stroke
                        "path#bottom_res { stroke:${getRGB(R.color.mild_greyscaleLight)}; stroke-dasharray:24.4640007,9.17399979; stroke-dashoffset:38.531 }")))

            if (uncheckedFramesRange.contains(num)) uncheckedDrawable.addFrame(picture, prefPowerAnimation_frameTime)
            else if (checkedFramesRange.contains(num)) checkedDrawable.addFrame(picture, prefPowerAnimation_frameTime)
        }

        prefPowerAnimation_duration = prefPowerAnimation_frameTime * uncheckedDrawable.numberOfFrames
        return Pair(uncheckedDrawable, checkedDrawable)
    }

    //square pair
    private fun comprisePrefFrameAnimation(): Pair<Pair<AnimationDrawable, AnimationDrawable>, Pair<AnimationDrawable, AnimationDrawable>>{
        assert(prefPowerAnimation_duration!=0)
        val frames = prefPowerAnimation_duration/prefPowerAnimation_frameTime
        val grad = getColorGradient(R.color.mild_greyscalePresence, R.color.mild_pitchSub, frames)
        val blurRad = 0.8 / 100 * ratios.prefFrameRect.width()


        val checkedCenterDrawable = AnimationDrawable().apply { isOneShot = true }
        val uncheckedCenterDrawable = AnimationDrawable().apply { isOneShot = true }

        var templateSVG = SVG.getFromAsset(context.assets, "rv_pref_frame_center.svg")
        with(templateSVG){
            documentWidth = ratios.prefFrameRect.width().toFloat()
            documentHeight = ratios.prefFrameRect.height().toFloat()
        }

        var drawablesBuffer = Array<Drawable?>(frames) {null}
        for (i in 0 until frames) {
            var bitmap = Bitmap.createBitmap(ratios.prefFrameRect.width(), ratios.prefFrameRect.height(), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            templateSVG.renderToCanvas(canvas, RenderOptions.create().css("#layer1 { stroke:${grad[i]} }"))
            bitmap = Toolkit.blur(bitmap, blurRad.roundToInt())

            drawablesBuffer[i] = BitmapDrawable(context.resources, bitmap)
        }

        for (i in drawablesBuffer.indices) checkedCenterDrawable.addFrame(drawablesBuffer[i]!!, prefPowerAnimation_frameTime)
        for (i in drawablesBuffer.size-1 downTo 0) uncheckedCenterDrawable.addFrame(drawablesBuffer[i]!!, prefPowerAnimation_frameTime)


        val checkedRightDrawable = AnimationDrawable().apply { isOneShot = true }
        val uncheckedRightDrawable = AnimationDrawable().apply { isOneShot = true }

        templateSVG = SVG.getFromAsset(context.assets, "rv_pref_frame_right.svg")
        with(templateSVG){
            documentWidth = ratios.prefFrameRect.width().toFloat()
            documentHeight = ratios.prefFrameRect.height().toFloat()
        }

        drawablesBuffer = Array(frames) {null}
        for (i in 0 until frames) {
            var bitmap = Bitmap.createBitmap(ratios.prefFrameRect.width(), ratios.prefFrameRect.height(), Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            templateSVG.renderToCanvas(canvas, RenderOptions.create().css("#layer1 { stroke:${grad[i]} }"))
            bitmap = Toolkit.blur(bitmap, blurRad.roundToInt())

            drawablesBuffer[i] = BitmapDrawable(context.resources, bitmap)
        }

        for (i in drawablesBuffer.indices) checkedRightDrawable.addFrame(drawablesBuffer[i]!!, prefPowerAnimation_frameTime)
        for (i in drawablesBuffer.size-1 downTo 0) uncheckedRightDrawable.addFrame(drawablesBuffer[i]!!, prefPowerAnimation_frameTime)

        return Pair(Pair(uncheckedCenterDrawable, checkedCenterDrawable), Pair(uncheckedRightDrawable, checkedRightDrawable))
    }

    private fun getColorGradient(@ColorRes startColorID: Int, @ColorRes endColorID: Int, frames: Int): Array<String>{
        var color = getRGB(startColorID)
        val startColor = arrayOf(
            Integer.parseInt(color.substring(1..2), 16),
            Integer.parseInt(color.substring(3..4), 16),
            Integer.parseInt(color.substring(5..6), 16)
        )
        color = getRGB(endColorID)
        val endColor = arrayOf(
            Integer.parseInt(color.substring(1..2), 16),
            Integer.parseInt(color.substring(3..4), 16),
            Integer.parseInt(color.substring(5..6), 16)
        )

        val rStep = (endColor[0] - startColor[0]).toFloat() / (frames-1)
        val gStep = (endColor[1] - startColor[1]).toFloat() / (frames-1)
        val bStep = (endColor[2] - startColor[2]).toFloat() / (frames-1)

        val walker = arrayOf(
            startColor[0].toFloat(),
            startColor[1].toFloat(),
            startColor[2].toFloat()
        )

        val gradient = Array(frames) {""}
        for (i in 0 until frames){
            gradient[i] = "#" +
                    Integer.toHexString(walker[0].roundToInt()) +
                    Integer.toHexString(walker[1].roundToInt()) +
                    Integer.toHexString(walker[2].roundToInt())

            walker[0] += rStep
            walker[1] += gStep
            walker[2] += bStep
        }
        return gradient
    }

    fun getPrepDrawable(resID: Int, checked: Boolean): Drawable{
        val prep = if (!checked) uncheckedDrawables[resID] else checkedDrawables[resID]
        prep?.let { return prep }
            ?: kotlin.run {
                slU.s("Back off man. We don't have a painted drawable with resId: ^${context.resources.getResourceEntryName(resID)}^")
                return VectorDrawable() }
    }
}

class DrawableUtils {
    companion object{

        //
        @JvmStatic
        fun View.startOpacityAnimation(from: Float, to: Float){
            val animation = AlphaAnimation(from, to)
            animation.duration = 500
            animation.fillAfter = true

            this.startAnimation(animation)
        }
    }
}