package com.vova9110.bloodbath.alarmsUI

import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.view.View
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.RecyclerView
import com.vova9110.bloodbath.R
import com.vova9110.bloodbath.SplitLogger
import com.vova9110.bloodbath.alarmScreenBackground.AlarmRepo
import com.vova9110.bloodbath.alarmsUI.recyclerView.AlarmListAdapter
import com.vova9110.bloodbath.alarmsUI.recyclerView.RowLayoutManager
import com.vova9110.bloodbath.database.Alarm
import java.util.*
import kotlin.math.roundToInt

typealias sl = SplitLogger

class FreeAlarmsHandler(
    private val supervisor: UISupervisor,
    targetView: AdjustableView, globalRect: Rect,
    override val transmitterMethod: () -> Unit,
): TargetedHandler(targetView, globalRect),
                        FAHCallback, AideCallback, ErrorReceiver {

    private val context = supervisor.app.applicationContext
    private val repo = supervisor.repo
    val recycler = targetView as AdjustableRecyclerView
    private var adapter: AlarmListAdapter
    private var rlmCallback: RLMCallback
    private var measurements: MeasurementsAide? = null
    private val comp = kotlin.Comparator { a1:Alarm, a2:Alarm-> if (a1.addFlag) 1 else if (a2.addFlag) -1 else 0 }.
        then { a1:Alarm, a2:Alarm-> if (a1.prefBelongsToAdd) 1 else if (a2.prefBelongsToAdd) -1 else 0 }.
            then { (hour1, minute1): Alarm, (hour2, minute2):Alarm-> if (hour1!=hour2) hour1-hour2 else minute1-minute2 }

    private val addAlarm = Alarm()
    //variable made up only for notifying ViewHolder of pref view's alliance

    init {
        adapter = pollForList()
        recycler.adapter = adapter

        val lm = RowLayoutManager(this, this)
        recycler.layoutManager = lm
        rlmCallback = lm
    }

    private fun pollForList(): AlarmListAdapter {

        var list: MutableList<Alarm> = repo.all.toMutableList()
        if (list.isEmpty()) {
            list = MutableList(1) { addAlarm }
        }
        else list.add(addAlarm)

        list.sortWith(comp)

        return AlarmListAdapter(
                this,
                list
            )
    }

    override fun handleError(ex: Exception) {
        slU.s("Resetting RV", ex)

        adapter = pollForList()
        recycler.adapter = adapter

        val lm = RowLayoutManager(this, this)
        recycler.layoutManager = lm
        rlmCallback = lm

        recycler.requestLayout()
    }

    private var bufferList = LinkedList<Alarm>()

    @JvmField
    var repeatButton = false
    @JvmField
    var activeButton = false

    override fun notifyBaseClick(prefParentPos: Int) {
        val returned = rlmCallback.defineBaseAction(prefParentPos)

        when(returned.flag){
            RowLayoutManager.LAYOUT_PREF -> passPrefToAdapter(returned.parentPos, returned.prefPos)

            RowLayoutManager.HIDE_PREF -> removePref()

            RowLayoutManager.HIDE_N_LAYOUT_PREF -> removeNPassPrefToAdapter(returned.parentPos, returned.prefPos)
        }
    }



    private fun passPrefToAdapter(parentPos: Int, prefPos: Int) {


        bufferList.clear()
        bufferList.addAll(adapter.currentList)
        val parent = bufferList[parentPos]
        val addAlarmPos = bufferList.indexOfFirst { it.addFlag }
        /*
        Here we are taking info from the parent element.
        Keep in mind, we are not taking the "pref" to the database,
        because we don't store them in there
        */
        val pref = Alarm(parent.hour, parent.minute, parentPos, addAlarmPos, parent.enabled)
        bufferList.add(prefPos, pref)

        adapter.submitList(bufferList)
        adapter.notifyItemInserted(prefPos)
    }

    private fun removePref() {

        bufferList.clear()
        bufferList.addAll(adapter.currentList)

        var prefIndex = -1
        for (i in bufferList.indices) if (bufferList[i].prefFlag) prefIndex=i
        bufferList.removeAt(prefIndex)
//        bufferList.forEach { if (it.prefFlag) prefIndex=bufferList.indexOf(it) }.also { bufferList.removeAt(prefIndex) }

        adapter.submitList(bufferList)
        adapter.notifyItemRemoved(prefIndex)
    }

    private fun removeNPassPrefToAdapter(parentPos: Int, prefPos: Int) {
        bufferList.clear()
        bufferList.addAll(adapter.currentList)

        var exPrefIndex = -1
        for (i in bufferList.indices) if (bufferList[i].prefFlag) exPrefIndex=i
        bufferList.removeAt(exPrefIndex)

        val parent = bufferList[parentPos]
        var addAlarmPos: Int = -1
        bufferList.forEach { addAlarmPos = if (it.addFlag) bufferList.indexOf(it) else -1 }

        val pref = Alarm(parent.hour, parent.minute, parentPos, addAlarmPos, parent.enabled)
        bufferList.add(prefPos, pref)

        adapter.submitList(bufferList)
        adapter.notifyItemRemoved(exPrefIndex)
        adapter.notifyItemInserted(prefPos)
    }

    /*

     */

    override fun deleteItem(adapterPos: Int) {
        rlmCallback.setNotifyUpdate(RowLayoutManager.UPDATE_DATASET)

        val currentPos: Int
        var prefRemoved = false
        bufferList.clear()
        bufferList.addAll(adapter.currentList)
        val current = bufferList[adapterPos]

        //We implement this shitshow because it won't work in the Kotlin way, idk why
        var prefPos = -1
        for (i in 0 until bufferList.size) if (bufferList[i].prefFlag) prefPos = i
        if (prefPos!=-1){
            bufferList.removeAt(prefPos)
            prefRemoved = true
        }

        currentPos = bufferList.indexOf(current)
        bufferList.remove(current)
        repo.deleteOne(current)

        adapter.submitList(bufferList)
        if (prefRemoved) recycler.post { adapter.notifyItemRemoved(prefPos) }
        recycler.post { adapter.notifyItemRemoved(currentPos) }
    }

    override fun addItem(pickerNour: Int, pickerMinute: Int) {
        rlmCallback.setNotifyUpdate(RowLayoutManager.UPDATE_DATASET)

        val currentPos: Int
        bufferList.clear()
        bufferList.addAll(adapter.currentList)

        if (bufferList.find { it.hour==pickerNour && it.minute==pickerMinute }!=null){
            Toast.makeText(context, "Alarm already exists", Toast.LENGTH_SHORT).show()
            sl.ip("alarm already exists, exiting")
            return
        }

        val current = Alarm(pickerNour, pickerMinute)
        bufferList.add(current)
        repo.insert(current)
        bufferList.sortWith(comp)
        currentPos = bufferList.indexOf(current)

        var prefPos = -1
        for (i in 0 until bufferList.size) if (bufferList[i].prefFlag) prefPos = i
        bufferList.removeAt(prefPos)

        adapter.submitList(bufferList)
        recycler.post { adapter.notifyItemRemoved(prefPos) }
        recycler.post { adapter.notifyItemInserted(currentPos) }
        recycler.post{ adapter.notifyItemChanged(currentPos) }
    }

    override fun changeItemTime(pickerHour: Int, pickerMinute: Int) {
        rlmCallback.setNotifyUpdate(RowLayoutManager.UPDATE_DATASET)

        if (bufferList.find { it.hour== pickerHour && it.minute==pickerMinute }!=null){
            Toast.makeText(context, "Alarm already exists", Toast.LENGTH_SHORT).show()
            sl.ip("alarm already exists, exiting")
            return
        }
        val oldPos: Int
        bufferList.clear()
        bufferList.addAll(adapter.currentList)

        lateinit var pref: Alarm
        for (i in 0 until bufferList.size) if (bufferList[i].prefFlag) pref = bufferList[i]
        oldPos = pref.parentPos
        val current = bufferList[oldPos]

        var prefPos = -1
        for (i in 0 until bufferList.size) if (bufferList[i].prefFlag) prefPos = i
        bufferList.removeAt(prefPos)

        repo.deleteOne(current)
        bufferList.remove(current)

        val new = current.clone(pickerHour, pickerMinute)
        repo.insert(new)
        bufferList.add(new)
        bufferList.sortWith(comp)
        val newPos = bufferList.indexOf(new)

        adapter.submitList(bufferList)
        adapter.notifyItemRemoved(prefPos)
        adapter.notifyItemRemoved(oldPos)
        adapter.notifyItemInserted(newPos)
        adapter.notifyItemChanged(newPos)
    }

    /*
    On any update of internal properties we have to put these changes into the DB
    and update parent View in RV, which is directly responsible for
    further display of the changes
     */
    override fun updateInternalProperties(parentPos: Int, isChecked: Boolean) {
        rlmCallback.setNotifyUpdate(RowLayoutManager.UPDATE_PARENT)

        bufferList.clear()
        bufferList.addAll(adapter.currentList)
        val current = bufferList[parentPos]

        current.enabled = isChecked
        current.weekdays = if (current.repeatable) BooleanArray(7) { true } else BooleanArray(7) { false }

//        repo.update(current, true)
        bufferList[parentPos] = current
        adapter.submitList(bufferList)
        adapter.notifyItemChanged(parentPos)
    }

    private val alarm1 = Alarm(6,0, true)
    private var alarm2 = Alarm(6, 20, true)
    fun createUsual(){
        alarm1.repeatable = true
        alarm1.weekdays = BooleanArray(7) {true}
        repo.insert(alarm1)

        alarm2.apply {
            repeatable = true
            weekdays = BooleanArray(7) {true}
            detection = true
        }
        repo.insert(alarm2)
    }
    fun deleteUsual(){
        repo.deleteOne(alarm1)
        repo.deleteOne(alarm2)
    }

    fun fill() {
        repo.deleteAll()
        bufferList = LinkedList<Alarm>()

        var r = 0
        for (i in 0..27) {
            if (i%3==0) r++
            val alarm = Alarm(r, i)
            bufferList.add(alarm)
            repo.insert(alarm)

        }
        bufferList.add(addAlarm)
        adapter.submitList(bufferList)
    }

    fun clear() {
        repo.deleteAll()
        bufferList = LinkedList<Alarm>()
        bufferList.add(addAlarm)
        adapter.submitList(bufferList)
    }

    override fun getMeasurements(): MeasurementsAide? = this.measurements

    override fun setMeasurements(measurements: MeasurementsAide) {
        this.measurements = measurements
    }

    override fun createMeasurements(
        recycler: RecyclerView.Recycler,
        master: RecyclerView.LayoutManager,
    ): MeasurementsAide {
        return MeasurementsAide(this, recycler, this.recycler.layoutManager!! as RowLayoutManager).also { this.measurements = it }
    }

    override fun getPrefAlignment(): Int? = measurements?.getPrefAlignment()

    override fun getTimeWindowState(pos: Int): Int{
        val alarmState = adapter.currentList.get(pos).enabled

        return if (measurements==null || pos!=measurements!!.getParentPos())
            if (alarmState) 1 else -1
        else if (pos==measurements!!.getParentPos())
            if (measurements!!.getPrefVisibility()) 0
            else if (alarmState) 1
            else -1
        else 1
    }

    override fun getRatios(): RatiosResolver = supervisor.ratios

    override fun getDrawable(resID: Int, checked: Boolean): Drawable = supervisor.drawables.getPrepDrawable(resID, checked)

    override fun getFragmentManager(): FragmentManager = supervisor.fragmentManager
}

/**
 * Generally, there are two kinds of actions can be transmitted through this interface.
 * First ones belong to actions related to user's interaction with time window such as show and hide individual preferences window, and require deeper integration with [RowLayoutManager].
 * Second ones on the other hand belong directly to the inner elements of pref window
 */
sealed interface FAHCallback{
    //Return value indicates which drawable should be put on time window after click happened
    fun notifyBaseClick(prefParentPos: Int)
    fun addItem(pickerNour: Int, pickerMinute: Int)
    fun changeItemTime(pickerHour: Int, pickerMinute: Int)
    fun updateInternalProperties(parentPos: Int, isChecked: Boolean)
    fun deleteItem(adapterPos: Int)
    /*
    These two methods should be called from VH to set a proper drawable for a
    time window or a pref frame. Calling these methods without placing
    proper pref-surrounding pointers in RLM (for the frame)
    OR putting a valid parent view inside the adapter (for the time window) will cause NPE
    */
    fun getTimeWindowState(pos: Int): Int
    fun getPrefAlignment(): Int?
    //These two communicate with Supervisor's fields and methods
    fun getRatios(): RatiosResolver
    fun getDrawable(resID: Int, checked: Boolean): Drawable
    fun getFragmentManager(): FragmentManager
}

/**
 * [MeasurementsAide] uses this interface to communicate with [FreeAlarmsHandler] directly
 */
sealed interface AideCallback{
    //This is how RLM takes it's aide
    fun getMeasurements(): MeasurementsAide?
    //This is how FAH gets newly created aide
    fun setMeasurements(measurements: MeasurementsAide)
    //This is how FAH creates an aide
    fun createMeasurements(recycler: RecyclerView.Recycler, master: RecyclerView.LayoutManager): MeasurementsAide
}

/**
 * It's not always a direct one-step process. In some case we need [RowLayoutManager] to internally define us
 * which action was taken place and prepare itself for the future layout
 */
interface RLMCallback {
    /**
     * @return flag value, must be one of
     * [RowLayoutManager.LAYOUT_PREF], [RowLayoutManager.HIDE_PREF], [RowLayoutManager.HIDE_N_LAYOUT_PREF]
     */
    fun defineBaseAction (prefParentPos: Int) : RLMReturnData
    fun setNotifyUpdate(flag: Int)
}

/**
 * Class in which we retrieve computed data from RLM. Using for one case only
 */
data class RLMReturnData(var flag: Int = -1){
    var parentPos: Int = -1
    var prefPos: Int = -1
    private var pullDataset: Boolean = false

    constructor(flag: Int, parentPos: Int, prefPos: Int): this(flag){
        this.parentPos = parentPos
        this.prefPos = prefPos
    }
    constructor(flag: Int, pullDataset: Boolean): this (flag){
        this.pullDataset = pullDataset
    }
}

/**
 * The thing is, when there's a time for initialization, we measure all margins and paddings,
 * so when LM's asking for each dimensions specifically, we decide which one is appropriate
 * for a given child view
 */
data class MeasurementsAide(
    private val handler: FreeAlarmsHandler,
    private val recycler: RecyclerView.Recycler,
    private val master: RowLayoutManager,
){

    private val sample: View = recycler.getViewForPosition(0)

    private val mTopPadding: Int
    private val mNormalSidePadding: Int
    private val mShrankSidePadding: Int
    private val mBaseVerticalPadding: Int
    private val mEnvoyWidth: Int
    private val mEnvoyHeight: Int
    private val mEnvoyHorizontalIndent: Int

    val measuredTimeWidth: Int
    val measuredTimeHeight: Int
    val measuredPrefWidth: Int
    val measuredPrefHeight: Int
    val prefTopIndent: Int
    val prefLeftIndent: Int
    val prefBottomPadding: Int

    private fun getParentAlignment(iterator: Int): Int?{
        //first view index in mother row
        val rangeStart = (master.prefRowPos-2)*3
        val motherRange = IntRange(rangeStart, rangeStart+2)

        return if (iterator in motherRange) iterator - master.prefParentPos else null
    }

    fun getPrefVisibility(): Boolean = master.prefVisibility
    fun getParentPos(): Int = master.prefParentPos

    init{
        val res = sample.resources

        fun getFloat(resourceID: Int): Float = res.getFraction(resourceID, 1,1)

        mTopPadding =
            (master.width *
                    getFloat(R.fraction.rv_top_padding)).roundToInt()
        mBaseVerticalPadding =
            (master.width *
                    getFloat(R.fraction.rv_vertical_padding)).roundToInt()
        prefTopIndent =
            (master.width *
                    getFloat(R.fraction.rv_pref_vertical_indent)).roundToInt()
        prefLeftIndent =
            (master.width *
                    getFloat(R.fraction.rv_pref_horizontal_indent)).roundToInt()
        prefBottomPadding =
            (master.width *
                    getFloat(R.fraction.rv_pref_bottom_padding)).roundToInt()


        handler.recycler.setPadding(0,mTopPadding,0,(mTopPadding * (2f/3f)).toInt())
        master.measureChild(sample,0,0)

        measuredTimeWidth = master.getDecoratedMeasuredWidth(sample)
        measuredTimeHeight = master.getDecoratedMeasuredHeight(sample)

        mNormalSidePadding = ((master.width - measuredTimeWidth*3).toFloat()/4).roundToInt()
        mShrankSidePadding = mNormalSidePadding / 2

        sample.findViewById<AdjustableCompoundButton>(R.id.rv_time_window).visibility = View.GONE
        sample.findViewById<AdjustableImageView>(R.id.rv_pref_frame).visibility = View.VISIBLE

        master.measureChild(sample, 0,0)

        measuredPrefWidth = master.getDecoratedMeasuredWidth(sample)
        measuredPrefHeight = master.getDecoratedMeasuredHeight(sample)

        mEnvoyWidth =
            (measuredPrefWidth *
                    getFloat(R.fraction.rv_pref_parent_envoy_width)).roundToInt()
        mEnvoyHeight =
            (mEnvoyWidth *
                    getFloat(R.fraction.rv_pref_parent_envoy_height)).roundToInt()
        mEnvoyHorizontalIndent = (prefTopIndent * 1.6).toInt()

        sample.findViewById<AdjustableCompoundButton>(R.id.rv_time_window).visibility = View.VISIBLE
        sample.findViewById<AdjustableImageView>(R.id.rv_pref_frame).visibility = View.GONE

        slU.f("measurements:\n\t\tTop padding: $mTopPadding, vertical padding: $mBaseVerticalPadding \n\t\t" +
                "Normal side padding: $mNormalSidePadding, shrank side padding: $mShrankSidePadding\n\t\t" +
                "T/w width: $measuredTimeWidth, t/w height: $measuredTimeHeight\n\t\t" +
                "Pref width: $measuredPrefWidth, pref height: $measuredPrefHeight")
    }

    fun getHorizontalPadding(): Int = mNormalSidePadding
    fun getHorizontalPadding(iterator: Int): Int {
        //first index in mother row
        val rangeStart = (master.prefRowPos-2)*3
        val motherRange = IntRange(rangeStart, rangeStart+2)

        return if (iterator in motherRange)
            if (iterator == master.prefParentPos)
                mNormalSidePadding + mShrankSidePadding + mEnvoyHorizontalIndent
            else mShrankSidePadding
        else mNormalSidePadding
    }
    fun getVerticalPadding(): Int = mBaseVerticalPadding
    fun getMeasuredTimeWidth(iterator: Int) = if (iterator!=master.prefParentPos) measuredTimeWidth
        else mEnvoyWidth
    fun getMeasuredTimeHeight(iterator: Int) = if (iterator!=master.prefParentPos) measuredTimeHeight
        else mEnvoyHeight
    fun getDecoratedTimeWidth(): Int = measuredTimeWidth + mNormalSidePadding
    //It's made to run through all motherRow's views an get their relative positions
    fun getDecoratedTimeWidth(iterator: Int): Int {

        return if (getParentAlignment(iterator)!=null){
            measuredTimeWidth.plus(
                when (iterator - master.prefParentPos) {
                    -1 -> mNormalSidePadding + mShrankSidePadding + mEnvoyHorizontalIndent
                    0 -> mNormalSidePadding + mShrankSidePadding - mEnvoyHorizontalIndent
                    -2, 1, 2 -> mShrankSidePadding
                    else -> 0
                }
            )
        } else measuredTimeWidth + mNormalSidePadding

    }
    /*
    This is just to determine parent's view relative position to other views in the row.
    It purely relies on LayoutManager's data so be aware
     */
    fun getPrefAlignment(): Int?{
        //Matching to the central view of the row
        val parentRef: Int = (master.prefRowPos - 2) * 3 + 1
        return getParentAlignment(parentRef)
    }
    fun getDecoratedTimeHeight(): Int =
        measuredTimeHeight +
                mBaseVerticalPadding

    fun getPrefTopOffsetShift(): Int = measuredPrefHeight - prefTopIndent + prefBottomPadding

}

