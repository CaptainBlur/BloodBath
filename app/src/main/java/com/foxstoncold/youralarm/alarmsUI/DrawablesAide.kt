package com.foxstoncold.youralarm.alarmsUI

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.drawable.AnimationDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.PictureDrawable
import android.graphics.drawable.VectorDrawable
import android.os.Build
import android.util.SparseArray
import android.view.View
import android.view.animation.AlphaAnimation
import android.widget.EditText
import androidx.annotation.ColorRes
import androidx.annotation.RequiresApi
import androidx.core.content.res.ResourcesCompat
import com.caverock.androidsvg.RenderOptions
import com.caverock.androidsvg.SVG
import com.foxstoncold.youralarm.R
import com.google.android.renderscript.Toolkit
import kotlin.math.roundToInt


class RatiosResolver(private val context: Context, globalRect: Rect){
    val prefFrameRect: Rect
    val prefPowerRect: Rect
    val timeWindowRect: Rect
    val individualsTierOneRect: Rect
    val prefRepeatRect: Rect
    val prefWeekdays: Rect

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


        width = globalRect.width() *
                getFraction(R.fraction.rv_timeWindow_width)
        height = width *
                getFraction(R.fraction.rv_timeWindow_height)
        timeWindowRect = Rect(0,0, width.roundToInt(),height.roundToInt())


        width = globalRect.width() *
                getFraction(R.fraction.rv_pref_tierOne_width)
        height = width
        individualsTierOneRect = Rect(0,0, width.roundToInt(), height.roundToInt())


        height = globalRect.width() *
                getFraction(R.fraction.rv_pref_repeat_height_toGlobal_width)
        width = height *
                getFraction(R.fraction.rv_pref_repeat_width_toHeight)
        prefRepeatRect = Rect(0,0, width.roundToInt(), height.roundToInt())


        height = globalRect.width() *
                getFraction(R.fraction.rv_pref_weekday_height_toGlobal_width)
        width = height *
                getFraction(R.fraction.rv_pref_weekday_width)
        prefWeekdays = Rect(0,0, width.roundToInt(), height.roundToInt())
    }

    private fun getFraction(resourceID: Int): Float = (context.resources.getFraction(resourceID, 1,1))
}

interface DrawablesAide{
    fun get_neutral_drawable(id: Int): Drawable
    fun get_unchecked_drawable(id: Int): Drawable
    fun get_checked_drawable(id: Int): Drawable
}

class MainDrawables(private val context: Context, private val ratios: RatiosResolver): DrawablesAide{
    private val neutralDrawables = SparseArray<Drawable>()
    private val uncheckedDrawables = SparseArray<Drawable>()
    private val checkedDrawables = SparseArray<Drawable>()

    private val prefPowerAnimation_frameTime = (1f/60 * 1000).roundToInt()
    private var prefPowerAnimation_duration = 0
    val blurRad = 0.8 / 100 * ratios.prefFrameRect.width()

    companion object{
        const val rv_pref_power = 91
        const val rv_pref_frame_center = 848
        const val rv_pref_frame_right = 414
        const val rv_time_window = 674
        const val rv_pref_music = 283
        const val rv_pref_vibration = 78
        const val rv_pref_preliminary = 541
        const val rv_pref_activeness = 873
        const val rv_pref_repeat = 46
        const val rv_pref_repeat_disabled = 416
        const val rv_pref_weekday_button = 634
        const val plus = 306
    }

    private fun getRGB(@ColorRes resID: Int): String{
        val color = context.getColor(resID)
        return "#" + Integer.toHexString(color).substring(2)
    }

    init {
        val timerStart = System.currentTimeMillis()

        var drawableID: Int
        lateinit var drawableSVG: SVG
        lateinit var neutralPicture: PictureDrawable
        lateinit var uncheckedPicture: PictureDrawable
        lateinit var checkedPicture: PictureDrawable

        lateinit var bitmap: Bitmap
        lateinit var canvas: Canvas


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


        drawableID = plus
        drawableSVG = SVG.getFromAsset(context.assets, "plus.svg")
        with(drawableSVG){
            var side = ratios.timeWindowRect.height().toFloat()
            side -= (side * 0.5f)

            documentHeight = side
            documentWidth = side
        }
        neutralPicture = PictureDrawable(drawableSVG.renderToPicture())
        neutralDrawables.set(drawableID, neutralPicture)


        drawableID = rv_time_window
        drawableSVG = SVG.getFromAsset(context.assets, "rv_time_window.svg")
        with(drawableSVG){
            documentWidth = ratios.timeWindowRect.width().toFloat()
            documentHeight = ratios.timeWindowRect.height().toFloat()
        }

        //rendered PictureDrawable is not good for moving drawables
        bitmap = Bitmap.createBitmap(ratios.timeWindowRect.width(), ratios.timeWindowRect.height(), Bitmap.Config.ARGB_8888)
        canvas = Canvas(bitmap)
        drawableSVG.renderToCanvas(canvas, RenderOptions.create().css(
            "#lit_group { opacity:0 }" +
                "path#frame_unlit { fill:${getRGB(R.color.mild_greyscaleLight)} }"))
        uncheckedDrawables.set(drawableID, BitmapDrawable(context.resources, bitmap))

        bitmap = Bitmap.createBitmap(ratios.timeWindowRect.width(), ratios.timeWindowRect.height(), Bitmap.Config.ARGB_8888)
        canvas = Canvas(bitmap)
        drawableSVG.renderToCanvas(canvas, RenderOptions.create().css(
            "#unlit_group { opacity:0 }" +
                    "path#frame_lit { fill:${getRGB(R.color.mild_pitchSub)} }"))
        checkedDrawables.set(drawableID, BitmapDrawable(context.resources, bitmap))

        drawableSVG = SVG.getFromAsset(context.assets, "rv_time_window_stripped.svg")
        with(drawableSVG){
            documentWidth = ratios.timeWindowRect.width().toFloat()
            documentHeight = ratios.timeWindowRect.height().toFloat()
        }
        bitmap = Bitmap.createBitmap(ratios.timeWindowRect.width(), ratios.timeWindowRect.height(), Bitmap.Config.ARGB_8888)
        canvas = Canvas(bitmap)
        drawableSVG.renderToCanvas(canvas, RenderOptions.create().css(
            "path#frame_unlit { fill:${getRGB(R.color.mild_greyscaleLight)} }" +
                    "rect#strip_one { fill:${getRGB(R.color.mild_pitchSub)} }" +
                    "rect#strip_two { fill:${getRGB(R.color.mild_pitchSub)} }" +
                    "rect#strip_three { fill:${getRGB(R.color.mild_pitchSub)} }"))
        neutralDrawables.set(drawableID, BitmapDrawable(context.resources, bitmap))


        //All tierOne individual icons are normalized to one width

        drawableID = rv_pref_music
        drawableSVG = SVG.getFromAsset(context.assets, "rv_pref_music.svg")
        with(drawableSVG){
            documentWidth = ratios.individualsTierOneRect.width().toFloat()
            documentHeight = ratios.individualsTierOneRect.height().toFloat()
        }

        neutralPicture = PictureDrawable(drawableSVG.renderToPicture(RenderOptions.create().css(
            "path#path21778 { fill:${getRGB(R.color.mild_pitchRegular)} }"
        )))
        neutralDrawables.set(drawableID, neutralPicture)


        drawableID = rv_pref_vibration
        drawableSVG = SVG.getFromAsset(context.assets, "rv_pref_vibration.svg")
        with(drawableSVG){
            documentWidth = ratios.individualsTierOneRect.width().toFloat()
            documentHeight = ratios.individualsTierOneRect.height().toFloat()
        }

        uncheckedPicture = PictureDrawable(drawableSVG.renderToPicture(RenderOptions.create().css(
            "path#body_bold { fill-opacity:0 }" +
            "path#ears_bold { fill-opacity:0 }" +
            "path#ears_slim { fill:${getRGB(R.color.mild_greyscaleDark)} }"
        )))
        uncheckedDrawables.set(drawableID, uncheckedPicture)

        checkedPicture = PictureDrawable(drawableSVG.renderToPicture(RenderOptions.create().css(
            "path#body_slim { fill-opacity:0 }" +
                    "path#ears_slim { fill-opacity:0 }" +
                    "path#ears_bold { fill:${getRGB(R.color.mild_pitchRegular)} }"
        )))
        checkedDrawables.set(drawableID, checkedPicture)


        drawableID = rv_pref_preliminary
        drawableSVG = SVG.getFromAsset(context.assets, "rv_pref_preliminary.svg")
        with(drawableSVG){
            documentWidth = ratios.individualsTierOneRect.width().toFloat()
            documentHeight = ratios.individualsTierOneRect.height().toFloat()
        }

        uncheckedPicture = PictureDrawable(drawableSVG.renderToPicture(RenderOptions.create().css(
            "#g70239 { opacity:0 }" +
                    "#g66449 { fill:${getRGB(R.color.mild_greyscaleDark)} }" +
                    "#path2029 { fill:${getRGB(R.color.mild_greyscaleDark)} }" +
                    "#path65706 { fill:${getRGB(R.color.mild_greyscaleDark)} }"
        )))
        uncheckedDrawables.set(drawableID, uncheckedPicture)

        checkedPicture = PictureDrawable(drawableSVG.renderToPicture(RenderOptions.create().css(
            "#g66443 { opacity:0 }" +
                    "#g69498 { fill:${getRGB(R.color.mild_pitchRegular)} }" +
                    "#path51106 { fill:${getRGB(R.color.mild_pitchRegular)} }" +
                    "#path69502 { fill:${getRGB(R.color.mild_pitchRegular)} }"
        )))
        checkedDrawables.set(drawableID, checkedPicture)


        drawableID = rv_pref_activeness
        drawableSVG = SVG.getFromAsset(context.assets, "rv_pref_activeness.svg")
        with(drawableSVG){
            documentWidth = ratios.individualsTierOneRect.width().toFloat()
            documentHeight = ratios.individualsTierOneRect.height().toFloat()
        }

        uncheckedPicture = PictureDrawable(drawableSVG.renderToPicture(RenderOptions.create().css(
            "#bold { opacity:0 }" +
                    "#g20321 { fill:${getRGB(R.color.mild_greyscaleDark)} }"
        )))
        uncheckedDrawables.set(drawableID, uncheckedPicture)

        checkedPicture = PictureDrawable(drawableSVG.renderToPicture(RenderOptions.create().css(
            "#slim { opacity:0 }" +
                    "#g7937 { fill:${getRGB(R.color.mild_pitchRegular)} }"
        )))
        checkedDrawables.set(drawableID, checkedPicture)


        drawableID = rv_pref_repeat
        drawableSVG = SVG.getFromAsset(context.assets, "rv_pref_repeat.svg")
        with(drawableSVG){
            documentWidth = ratios.prefRepeatRect.width().toFloat()
            documentHeight = ratios.prefRepeatRect.height().toFloat()
        }

        uncheckedPicture = PictureDrawable(drawableSVG.renderToPicture(RenderOptions.create().css(
            "#arrows_bold, #plates_lit { opacity:0 }" +
                    "#plates_unlit { fill:${getRGB(R.color.mild_greyscaleDark)} }"
        )))
        uncheckedDrawables.set(drawableID, uncheckedPicture)

        neutralPicture = PictureDrawable(drawableSVG.renderToPicture(RenderOptions.create().css(
            "#arrows_slim { opacity:0 }" +
                    "#unlit_top, #unlit_bot, #lit_mid { opacity:0 }" +
                    "#plates_unlit { fill:${getRGB(R.color.mild_greyscaleDark)} }" +
                    "#plates_lit { fill:${getRGB(R.color.mild_pitchRegular)} }"
        )))
        neutralDrawables.set(drawableID, neutralPicture)

        checkedPicture = PictureDrawable(drawableSVG.renderToPicture(RenderOptions.create().css(
            "#arrows_slim, #plates_unlit { opacity:0 }" +
                    "#plates_lit { fill:${getRGB(R.color.mild_pitchRegular)} }"
        )))
        checkedDrawables.set(drawableID, checkedPicture)


        drawableID = rv_pref_repeat_disabled
        uncheckedPicture = PictureDrawable(drawableSVG.renderToPicture(RenderOptions.create().css(
            "#arrows_bold, #plates_lit, #plates_unlit { opacity:0 }"
        )))
        uncheckedDrawables.set(drawableID, uncheckedPicture)

        checkedPicture = PictureDrawable(drawableSVG.renderToPicture(RenderOptions.create().css(
            "#arrows_slim, #plates_lit, #plates_unlit { opacity:0 }"
        )))
        neutralDrawables.set(drawableID, checkedPicture)
        checkedDrawables.set(drawableID, checkedPicture)


        drawableID = rv_pref_weekday_button
        drawableSVG = SVG.getFromAsset(context.assets, "rv_pref_weekday_button.svg")
        with(drawableSVG){
            documentWidth = ratios.prefWeekdays.width().toFloat()
            documentHeight = ratios.prefWeekdays.height().toFloat()
        }

        uncheckedPicture = PictureDrawable(drawableSVG.renderToPicture(RenderOptions.create().css(
            "#lit { opacity:0 }" +
                    "#unlit { fill:${getRGB(R.color.mild_greyscaleDark)} }"
        )))
        uncheckedDrawables.set(drawableID, uncheckedPicture)

        checkedPicture = PictureDrawable(drawableSVG.renderToPicture(RenderOptions.create().css(
            "#unlit { opacity:0 }" +
                    "#lit { fill:${getRGB(R.color.mild_pitchRegular)} }"
        )))
        checkedDrawables.set(drawableID, checkedPicture)


        val timer = System.currentTimeMillis() - timerStart
        slU.f("Drawables preparation took ^$timer^ mills")
    }

    /*
    I decided to delegate task of calculating paths to Shapeshifter
    ValueAnimator helps to calculate colors transiti`on
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

    override fun get_neutral_drawable(id: Int): Drawable{
        val prep = neutralDrawables[id]
        prep?.let { return prep }
            ?: kotlin.run {
                slU.sp("failed to find id: ^$id^")
                return VectorDrawable() }
    }
    override fun get_unchecked_drawable(id: Int): Drawable{
        val prep = uncheckedDrawables[id]
        prep?.let { return prep }
            ?: kotlin.run {
                slU.sp("failed to find id: ^$id^")
                return VectorDrawable() }
    }
    override fun get_checked_drawable(id: Int): Drawable{
        val prep = checkedDrawables[id]
        prep?.let { return prep }
            ?: kotlin.run {
                slU.sp("failed to find id: ^$id^")
                return VectorDrawable() }
    }

    fun getResolvedRectangle(id: Int): Rect = when (id){
        rv_pref_weekday_button -> ratios.prefWeekdays
        rv_pref_repeat -> ratios.prefRepeatRect
        else -> Rect().also { slU.s("Failed to get valid ratios for ^$id^") }
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

        @RequiresApi(Build.VERSION_CODES.Q)
        @JvmStatic fun EditText.paintSelectors(color: Int){
            val array = context.theme.obtainStyledAttributes(arrayOf(
                android.R.attr.textSelectHandle,
                android.R.attr.textSelectHandleLeft,
                android.R.attr.textSelectHandleRight,
            ).toIntArray())

            val drawables = ArrayList<Drawable>(3)
            for (i in 0 .. 2){
                val resID = array.getResourceId(i,0)
                val drawable = ResourcesCompat.getDrawable(context.resources, resID, context.theme) as BitmapDrawable

                val paint = Paint()
                paint.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
                val result = Bitmap.createBitmap(drawable.bitmap.width, drawable.bitmap.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(result)
                canvas.drawBitmap(drawable.bitmap, null, Rect(0, 0, drawable.bitmap.width, drawable.bitmap.height), paint)

                drawables.add(BitmapDrawable(context.resources, result))
            }

            setTextSelectHandle(drawables[0])
            setTextSelectHandleLeft(drawables[1])
            setTextSelectHandleRight(drawables[2])
        }
    }
}