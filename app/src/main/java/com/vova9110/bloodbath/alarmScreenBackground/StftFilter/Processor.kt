package com.vova9110.bloodbath.alarmScreenBackground.StftFilter

import android.util.SparseIntArray
import org.jtransforms.fft.DoubleFFT_1D
import org.jtransforms.fft.FloatFFT_1D
import java.text.DecimalFormat
import java.util.*
import kotlin.math.ceil
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt

class Processor private constructor(){
    private var sampleRate = 0
    private var winSize = 20
    private var fftSize = 256
    private lateinit var func: String
    private var winSizeInSamples = 0f
    private lateinit var processingFun: (DoubleArray) -> Unit

    //I'm introducing this block of code, because we cannot just pass fft result of entire audio file, to Output
    //It will be too much for an array, especially when audio file is relatively long
    //So, we need to split the entire fft result into several groups of windows
    private lateinit var completeListener: CompleteDataListener
    private lateinit var functionListener: FunctionTestListener
    private lateinit var windowListener: NewWindowListener
    private var dbOutput = false
    private var windowSize = 0


    constructor(sampleRate: Int, fftSize: Int, winSize: Int, func: String, listener: CompleteDataListener, dbOutput: Boolean) : this(){
        this.sampleRate = sampleRate
        this.func = func
        this.fftSize = 2.0.pow(fftSize.toDouble()).toInt()
        this.winSize = winSize.toShort().toInt()
        this.winSizeInSamples = winSize.toFloat() / 1000 / (1 / sampleRate.toFloat())
        this.dbOutput = dbOutput
        completeListener = listener
        processingFun = ::processComplete
    }
    constructor(sampleRate: Int, fftSize: Int, func: String, listener: NewWindowListener, dbOutput: Boolean) : this(){
        this.sampleRate = sampleRate
        this.func = func
        this.fftSize = 2.0.pow(fftSize.toDouble()).toInt()
        this.dbOutput = dbOutput
        windowListener = listener
        processingFun = ::processComplete
    }
    constructor(func: String, listener: FunctionTestListener, windowSize: Int, dbOutput: Boolean) : this(){
        this.func = func
        this.windowSize = windowSize
        this.dbOutput = dbOutput
        functionListener = listener
        processingFun = ::processFunction
    }

    fun process(buffer: DoubleArray) = processingFun(buffer)

    private fun processComplete(buffer: DoubleArray){
        val winTotal = buffer.size / winSizeInSamples.toInt()
        var samplesCount = 0 //total samples counter through all windows

        var timeOfInput: Int = 0 //elapsed time in input signal

        val fftStep = (sampleRate / 2).toFloat() / fftSize

        //Keys starts from "00000000", including values at zero time and frequency
        val fftDataset = SparseIntArray()
        //Time indexes starts from "0", from zero time value
        val arrayTimeValues = IntArray(winTotal)
        //Freq indexes starts from "1", excluding zero freq value, because it's not valid
        val arrayFreqValues = IntArray(fftSize)
        var magMax = -170.0F
        var magMin = 0.0F

        for (i in skipIdle(buffer) until winTotal) { //cycle for each window in the whole output sequence
            val transformer = FloatFFT_1D((fftSize * 2).toLong())
            val window = DoubleArray(winSizeInSamples.toInt())
            val windowNResult = FloatArray(Integer.max(fftSize * 2, winSizeInSamples.toInt()))
            System.arraycopy(buffer, samplesCount, window, 0, winSizeInSamples.toInt())
            System.arraycopy(Windows.applyWindow(window, func), 0, windowNResult, 0, window.size)
            transformer.realForward(windowNResult)
            //            transformer.realForward(windowNResult);
            var j = fftStep //iterator for frequency val in output array (key)

            //cycle for each bin in every single window transform.
            var replaceBroken = false
            for (k in 1 until fftSize) {
                val re = windowNResult[2 * k]
                val im = windowNResult[2 * k + 1]
                var mag = sqrt(re * re + im * im)
                if (dbOutput) { //writing array in absolute values
                    val ref = 470.0 //dBFS reference value measured on pure sine wave with no window function applied
                    mag = (20 * log10(mag / ref)).toFloat()
                }
                //These two values coded into one key
                //Number of bin consists of 4 digits, and number of windows is 4 digits either (let's assume it's our input file limitation)
                val df = DecimalFormat("0000")
                val timeIndex: String = df.format(i.toLong())
                val freqIndex = df.format(k.toLong())
                val key = timeIndex + freqIndex
                arrayFreqValues[k] = (j * 100).toInt()
                if ((re.compareTo(0.0)==0) and (im.compareTo(0.0)==0)) mag = magMin
                if ((magMax < mag) and (mag.compareTo(0.0)!=0)) magMax = mag
                if (magMin > mag) magMin = mag
                if (replaceBroken and (k > 1)) {
                    val memKey: String = df.format(i.toLong()) + df.format((k - 2).toLong())
                    val prevKey: String = df.format(i.toLong()) + df.format((k - 1).toLong())
                    val prevMag = ((fftDataset[memKey.toInt()] + mag) / 2).toInt()
                    fftDataset.put(prevKey.toInt(), prevMag)
                    if (i == 0) println("\u001B[34mFreaking hell. Value approximated again. Key: " + df.format((k - 1).toLong()) + ". Something wrong with ur input" + "\u001B[0m")
                }
                replaceBroken = (re.compareTo(0.0)==0) and (im.compareTo(0.0)==0)
                fftDataset.append(key.toInt(), mag.toInt())
                j += fftStep
            }
            arrayTimeValues[i] = timeOfInput
            timeOfInput += winSize
            samplesCount += winSizeInSamples.toInt()
        }
        completeListener.onDataComputed(arrayTimeValues, arrayFreqValues, fftDataset, magMin.toInt(), magMax.toInt())

    }
    private fun skipIdle(buffer: DoubleArray): Int {
        var start = 0
        for (i in buffer.indices) {
            if (buffer[i] != 0.0) {
                //??Have questions about robustness??
                start = ceil((i.toDouble()/winSizeInSamples.toDouble())).toInt()
                break
            }
        }
        if (start == 0) throw NullPointerException("Input buffer is null")
        return start - 1
    }

    fun processSingle(buffer: DoubleArray) {
        if (buffer[0] == 0.0) {
            windowListener.onIdlePassed()
            return
        }
        val winSizeInSamples = buffer.size
        val fftStep = (sampleRate / 2).toFloat() / fftSize
        val fftSnapshot = SparseIntArray()
        var magMax = -170.0
        var magMin = 0.0
        val transformer = DoubleFFT_1D((fftSize * 2).toLong())
        val windowNResult = DoubleArray(Integer.max(fftSize * 2, winSizeInSamples))
        System.arraycopy(Windows.applyWindow(buffer, func), 0, windowNResult, 0, buffer.size)
        transformer.realForward(windowNResult)
        var j = fftStep //iterator for frequency val in output array (key)

        //cycle for each bin in every single window transform.
        var replaceBroken = false
        for (k in 1 until fftSize) {
            val re = windowNResult[2 * k]
            val im = windowNResult[2 * k + 1]
            var mag = sqrt(re * re + im * im)
            if (dbOutput) { //writing array in absolute values
                val ref = 470.0 //dBFS reference value measured on pure sine wave with no window function applied
                mag = 20 * log10(mag / ref)
            }
            val key = (j * 100).toInt()
            if ((re==0.0) and (im==0.0)) mag = magMin
            if ((magMax < mag) and (mag.compareTo(0.0)!=0)) magMax = mag
            if (magMin > mag) magMin = mag
            if (replaceBroken and (k > 1)) {
                val memKey = ((j - 2 * fftStep) * 100).toInt()
                val prevKey = ((j - fftStep) * 100).toInt()
                val prevMag = ((fftSnapshot[memKey] + mag) / 2).toInt()
                fftSnapshot.put(prevKey, prevMag)
                println("\u001B[34mFreaking hell. Value approximated again. Freq: $prevKey. Something wrong with ur input\u001B[0m")
            }
            replaceBroken = (re==0.0) and (im==0.0)
            fftSnapshot.append(key, mag.toInt())
            j += fftStep
        }
        fftSnapshot.put(0, 0)
        windowListener.onWindowComputed(fftSnapshot)
    }

    private fun processFunction(dummy: DoubleArray?) {
        val window = DoubleArray(windowSize)
        Arrays.fill(window, 1.0)
        functionListener.onRawFunctionComputed(Windows.applyWindow(window, func))
    }
}
