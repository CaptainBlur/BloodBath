package com.foxstoncold.youralarm.alarmsUI

import android.graphics.Rect
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.foxstoncold.youralarm.M_to_FA_Callback
import com.foxstoncold.youralarm.R
import com.foxstoncold.youralarm.SplitLogger
import com.foxstoncold.youralarm.SplitLoggerUI.UILogger.s
import com.foxstoncold.youralarm.alarmScreenBackground.AlarmExecutionDispatch
import com.foxstoncold.youralarm.alarmsUI.recyclerView.AlarmListAdapter
import com.foxstoncold.youralarm.alarmsUI.recyclerView.RowLayoutManager
import com.foxstoncold.youralarm.database.Alarm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*
import kotlin.math.roundToInt

typealias sl = SplitLogger

class FreeAlarmsHandler(
    private val supervisor: UISupervisor,
    targetView: AdjustableView,
    override val errorNotifierMethod: (code: Int) -> Unit,
): FAHCallback, AideCallback, ErrorHandlerImpl, FA_to_M_Callback {

    override val errorCode: Int
        get() = ErrorHandlerImpl.RV_ERROR_CODE
    private val mtofaCallback: M_to_FA_Callback by supervisor.callbacks
    private val context = supervisor.app.applicationContext
    private val repo = supervisor.repo

    private var recycler = targetView as AdjustableRecyclerView
    private lateinit var adapter: AlarmListAdapter
    private lateinit var rlmCallback: RLMCallback
    private var measurements: MeasurementsAide? = null

    private val comp = kotlin.Comparator { a1:Alarm, a2:Alarm-> if (a1.addFlag) 1 else if (a2.addFlag) -1 else 0 }.
        then { a1:Alarm, a2:Alarm-> if (a1.prefBelongsToAdd) 1 else if (a2.prefBelongsToAdd) -1 else 0 }.
            then { (hour1, minute1): Alarm, (hour2, minute2):Alarm-> if (hour1!=hour2) hour1-hour2 else minute1-minute2 }

    private val addAlarm = Alarm()
    private var addAlarmParentVisibility = true
    private var bufferList = LinkedList<Alarm>()


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
    private fun initRecycler(afterError: Boolean) {
        adapter = pollForList()
        recycler.adapter = adapter

        val lm = RowLayoutManager(this, this)
        rlmCallback = lm

        recycler.layoutManager = lm
        recycler.itemAnimator = lm.itemAnimator

        val savedState = if (!afterError) supervisor.stateSaver.state.value.RVSet else arrayOf(-1,0,0).toIntArray()
        slU.i(savedState)
        rlmCallback.setRVState(savedState)

        if (savedState[1]==1) recycler.post { notifyBaseClick(savedState[2]) }

    }

    init {
        initRecycler(false)
        informFirstActive()
    }
    private fun informFirstActive() {
        val actives = repo.actives
        if (actives.isNotEmpty()) {
            val first = actives[0]
            var time = first.triggerTime!!.time

            if (first.preliminary && !first.preliminaryFired) {
                time += first.preliminaryTime * 60 * 1000
            }

            mtofaCallback.passNewFirstActiveDate(Date(time))
        } else mtofaCallback.passNewFirstActiveDate(null)
    }
    private fun informSubInfo(alarm: Alarm = Alarm(), prefOpen: Boolean = false, prefClose: Boolean = false){

        if (prefOpen){
            val effectivelyEnabled =
                alarm.state == Alarm.STATE_PREPARE_SNOOZE ||
                alarm.state == Alarm.STATE_SNOOZE ||
                alarm.state == Alarm.STATE_FIRE ||
                alarm.state == Alarm.STATE_PRELIMINARY ||
                alarm.state == Alarm.STATE_PREPARE

            if (effectivelyEnabled){

                var actualTime = alarm.triggerTime!!.time
                var preliminary = false

                if (alarm.preliminary && !alarm.preliminaryFired) {
                    actualTime += alarm.preliminaryTime * 60 * 1000
                    preliminary = true
                }

                mtofaCallback.showSubInfo(alarm.id, preliminary, Date(actualTime))
            }
            else mtofaCallback.hideSubInfo()
        }

        if (prefClose) mtofaCallback.hideSubInfo()
    }

    override fun createBrandNewRecycler(afterError: Boolean){
        val parentVG = mtofaCallback.getParentVG()
        parentVG.removeView(recycler)
        LayoutInflater.from(context).inflate(R.layout.recycler_view, parentVG, true)

        recycler = parentVG.findViewById<AdjustableRecyclerView?>(R.id.recyclerview).apply {
            val rect = Rect()
            parentVG.getWindowVisibleDisplayFrame(rect)
            makeAdjustments(rect)
        }

        initRecycler(afterError)
        slU.i("New RV created")
    }

    override fun saveRVState(state: IntArray) {
        CoroutineScope(Dispatchers.Default).launch {
            supervisor.stateSaver.updateRVSavedState(state)
        }
    }
    override fun externalDismiss(alarmID: String, preliminaryOnly: Boolean) {
        slU.i("external dismiss action requested. id: $alarmID; complete: ${!preliminaryOnly}")

        val actives = repo.actives
        if (actives.isEmpty()){
            transmitError(NullPointerException("external dismiss failed: no actives"))
            return
        }

        var alarm: Alarm? = null
        for (active in repo.actives) if (active.id == alarmID) alarm = active
        if (alarm == null){
            transmitError(IllegalArgumentException("external dismiss failed: ID doesn't match"))
            return
        }

        if (!preliminaryOnly) AlarmExecutionDispatch.helpExternalDismissMain(context, alarm, repo)
        else AlarmExecutionDispatch.helpExternalDismissPreliminary(context, alarm, repo)

        for (any in repo.all) if (any.id == alarmID) alarm = any

        val parentPos = rlmCallback.manipulatePrefPowerState(alarm!!.enabled)

        try {
            rlmCallback.setNotifyUpdate(RowLayoutManager.UPDATE_PARENT)

            bufferList.clear()
            bufferList.addAll(adapter.currentList)

            bufferList[parentPos] = alarm

            adapter.submitList(bufferList)

            informSubInfo(alarm, prefOpen = true)
            informFirstActive()

            adapter.notifyItemChanged(alarm.parentPos)
        } catch (e: Exception) {
            transmitError(e)
        }
    }

    override fun internalErrorHandling(ex: Exception) {
        slU.s("Resetting RV", ex)
        createBrandNewRecycler(true)
    }

    override fun notifyBaseClick(prefParentPos: Int) {
        if (rlmCallback.isBusy()){
            slU.f("false call")
            return
        }

        val returned = rlmCallback.defineBaseAction(prefParentPos, false)
        try {
            when(returned.flag){
                RowLayoutManager.LAYOUT_PREF -> passPrefToAdapter(returned.parentPos, returned.prefPos)

                RowLayoutManager.HIDE_N_LAYOUT_PREF -> removeNPassPrefToAdapter(returned.parentPos, returned.prefPos)
            }
        } catch (e: Exception) {
            transmitError(e)
        }
    }

    override fun launchHidePref() = try {
        removePref()
    } catch (e: Exception) {
        transmitError(e)
    }


    /*
    In all consequent code we notify adapter in such an odd way because otherwise
    it will mess up items in the list. Paying close attention to all the ^binding^ events
    is the only way to implement new ways to mutate the list.
    Not even talking about DiffUtil
     */
    private fun passPrefToAdapter(parentPos: Int, prefPos: Int, notifyAdapter: Boolean = true) {

        bufferList.clear()
        bufferList.addAll(adapter.currentList)
        val parent = bufferList[parentPos]
        val addAlarmPos = bufferList.indexOfFirst { it.addFlag }
        /*
        Here we are taking info from the parent element.
        Keep in mind, we are not taking the "pref" to the database,
        because we don't store them in there
        */
        val pref = parent.createPref(parentPos, addAlarmPos)
        bufferList.add(prefPos, pref)

        informSubInfo(parent, prefOpen = true)

        adapter.submitList(bufferList)
        if (notifyAdapter) adapter.notifyItemInserted(prefPos)
    }

    private fun removePref() {

        bufferList.clear()
        bufferList.addAll(adapter.currentList)

        var prefIndex = -1
        for (i in bufferList.indices) if (bufferList[i].prefFlag) prefIndex=i
        bufferList.removeAt(prefIndex)
//        bufferList.forEach { if (it.prefFlag) prefIndex=bufferList.indexOf(it) }.also { bufferList.removeAt(prefIndex) }

        informSubInfo(prefClose = true)

        adapter.submitList(bufferList)
        adapter.notifyItemRemoved(prefIndex)
    }

    private fun removeNPassPrefToAdapter(parentPos: Int, prefPos: Int) {

        bufferList.clear()
        bufferList.addAll(adapter.currentList)

        var exPrefIndex = -1
        for (i in bufferList.indices) if (bufferList[i].prefFlag) exPrefIndex = i
        bufferList.removeAt(exPrefIndex)

        var addAlarmPos: Int = -1
        bufferList.forEach { addAlarmPos = if (it.addFlag) bufferList.indexOf(it) else -1 }

        val parent = bufferList[parentPos]
        val pref = parent.createPref(parentPos, addAlarmPos)
        bufferList.add(prefPos, pref)

        informSubInfo(parent, prefOpen = true)

        adapter.submitList(bufferList)
        adapter.notifyItemRemoved(exPrefIndex)
        adapter.notifyItemInserted(prefPos)
    }

    /*
    This case is a little bit more complicated than remove_and_pass.
    Layout itself is the same, but animations are different
    (at least now I think so, but it's not crucial)
    and we have to do some additional calculations before
    */
    override fun changeItemTime(newHour: Int, newMinute: Int) {
        if (rlmCallback.isBusy()) return

        try {
            if (bufferList.find { it.hour== newHour && it.minute== newMinute }!=null){
                Toast.makeText(context, "Alarm already exists", Toast.LENGTH_SHORT).show()
                sl.ip("alarm already exists, exiting")
                return
            }

            bufferList.clear()
            bufferList.addAll(adapter.currentList)

            //seeking for old pref and old parent pos
            lateinit var oldPref: Alarm
            for (i in 0 until bufferList.size) if (bufferList[i].prefFlag) oldPref = bufferList[i]
            val oldParentPos: Int = oldPref.parentPos

            //finding and removing old pref
            var oldPrefPos = -1
            for (i in 0 until bufferList.size) if (bufferList[i].prefFlag) oldPrefPos = i
            bufferList.removeAt(oldPrefPos)

            //removing old parent from everywhere
            val parent = bufferList[oldParentPos]
            repo.deleteOne(parent, context)

            //creating and passing new parent
            with(parent){
                hour = newHour
                minute = newMinute
            }
            repo.insert(parent, context)
            bufferList.sortWith(comp)
            val newParentPos = bufferList.indexOf(parent)

            //defining new prefPos and giving RLM an opportunity to prepare
            val newPrefPos = rlmCallback.defineBaseAction(newParentPos, true).prefPos

            //adding new pref
            val addAlarmPos = bufferList.indexOfFirst { it.addFlag }
            val newPref = parent.createPref(newParentPos, addAlarmPos)
            bufferList.add(newPrefPos, newPref)

            informSubInfo(newPref, prefOpen = true)
            informFirstActive()

            adapter.submitList(bufferList)
            adapter.notifyItemRemoved(oldPrefPos)
            adapter.notifyItemInserted(newPrefPos)
            adapter.notifyItemMoved(oldParentPos, newParentPos)
        } catch (e: Exception) {
            transmitError(e)
        }
    }

    override fun deleteItem(adapterPos: Int) {
        if (rlmCallback.isBusy()) return

        try {
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
            repo.deleteOne(current, context)

            informFirstActive()

            adapter.submitList(bufferList)
            if (prefRemoved) recycler.post { adapter.notifyItemRemoved(prefPos) }
            recycler.post { adapter.notifyItemRemoved(currentPos) }
        } catch (e: Exception) {
            transmitError(e)
        }
    }

    override fun addItem(alarm: Alarm) {
        if (rlmCallback.isBusy()) return

        try {
            rlmCallback.setNotifyUpdate(RowLayoutManager.UPDATE_DATASET)

            bufferList.clear()
            bufferList.addAll(adapter.currentList)

            if (bufferList.find { it.hour==alarm.hour && it.minute==alarm.minute }!=null){
                Toast.makeText(context, "Alarm already exists", Toast.LENGTH_SHORT).show()
                sl.ip("alarm already exists, exiting")
                return
            }

            bufferList.add(alarm)
            repo.insert(alarm, context)
            bufferList.sortWith(comp)
            val currentPos: Int = bufferList.indexOf(alarm)

            informSubInfo(alarm, prefOpen = true)
            informFirstActive()

            var prefPos = -1
            for (i in 0 until bufferList.size) if (bufferList[i].prefFlag) prefPos = i
            bufferList.removeAt(prefPos)

            adapter.submitList(bufferList)
            recycler.post { adapter.notifyItemRemoved(prefPos) }
            recycler.post { adapter.notifyItemInserted(currentPos) }
            recycler.post{ adapter.notifyItemChanged(currentPos) }
        } catch (e: Exception) {
            transmitError(e)
        }
    }

    /*
    On any update of internal properties we have to put these changes into the DB
    and update parent View in RV, which is directly responsible for
    further display of the changes
    Also, only that mechanism implies passing new Alarm object as a container of changed properties
     */
    override fun updateInternalProperties(alarm: Alarm) {
        if (rlmCallback.isBusy()) return
        if (alarm.prefBelongsToAdd) return

//        slU.ip(alarm.parentPos)
//        slU.ip(alarm.enabled)
//        slU.ip(alarm.repeatable)
//        slU.ip(alarm.weekdays)

        try {
            rlmCallback.setNotifyUpdate(RowLayoutManager.UPDATE_PARENT)

            bufferList.clear()
            bufferList.addAll(adapter.currentList)
            bufferList[alarm.parentPos] = alarm

            repo.update(alarm, true, context)
            adapter.submitList(bufferList)

            informSubInfo(alarm, prefOpen = true)
            informFirstActive()

            adapter.notifyItemChanged(alarm.parentPos)
        } catch (e: Exception) {
            transmitError(e)
        }
    }

    //Using by CVH as a part of some animation stuff
    override fun updateParent() {
        rlmCallback.setNotifyUpdate(RowLayoutManager.UPDATE_PARENT)
        recycler.requestLayout()
    }

    private val alarm1 = Alarm(6,0, true)
    private var alarm2 = Alarm(6, 20, true)
    fun createUsual(){
        alarm1.repeatable = true
        alarm1.weekdays = BooleanArray(7) {true}
        repo.insert(alarm1, context)

        alarm2.apply {
            repeatable = true
            weekdays = BooleanArray(7) {true}
            detection = true
        }
        repo.insert(alarm2, context)
    }
    fun deleteUsual(){
        repo.deleteOne(alarm1, context)
        repo.deleteOne(alarm2, context)
    }

    override fun clearOrFill(fill: Boolean){
        repo.deleteAll(context)
        bufferList = LinkedList<Alarm>()

        if (fill){
            var r = 0
            for (i in 0..27) {
                if (i%3==0) r++
                val alarm = Alarm(r, i)
                bufferList.add(alarm)
                repo.insert(alarm, context)

            }
            bufferList.add(addAlarm)
            adapter.submitList(bufferList)
        }
        createBrandNewRecycler(false)
    }

    override fun getMeasurements(): MeasurementsAide? = this.measurements.also { it?.setNewRV(this.recycler) }


    override fun createMeasurements(
        recycler: RecyclerView.Recycler,
        master: RecyclerView.LayoutManager,
    ) {
        this.measurements = MeasurementsAide(this, recycler, this.recycler).also { this.measurements = it }
    }

    override fun getPrefAlignment(): Int? = measurements?.getPrefAlignment()

    override fun getTimeWindowState(pos: Int): Int{
        if (pos==-1){
            slU.s("Cannot get valid state for time window")
            return 0
        }
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
    override fun getDrawables(): MainDrawables = supervisor.drawables
    override fun getFragmentManager(): FragmentManager = mtofaCallback.getFM()
    override fun getItemViewHolder(adapterPos: Int): ViewHolder? = recycler.findViewHolderForAdapterPosition(adapterPos)

    override fun showAdd(){
        if (rlmCallback.isBusy()){
            slU.f("false call")
            return
        }

        bufferList.clear()
        bufferList.addAll(adapter.currentList)
        val addPos = bufferList.indexOfFirst { it.addFlag }

        val returned = rlmCallback.defineBaseAction(addPos, false)
        when(returned.flag){
            RowLayoutManager.LAYOUT_PREF -> passPrefToAdapter(returned.parentPos, returned.prefPos)

            RowLayoutManager.HIDE_N_LAYOUT_PREF -> removeNPassPrefToAdapter(returned.parentPos, returned.prefPos)
        }

        addAlarmParentVisibility = false
    }

    override fun getAddAlarmParentVisibility(): Boolean {
        return if (!addAlarmParentVisibility){
            addAlarmParentVisibility = true
            false
        } else true
    }

}

/**
 * Generally, there are two kinds of actions can be transmitted through this interface.
 * First ones belong to actions related to user's interaction with time window such as show and hide individual preferences window, and require deeper integration with [RowLayoutManager].
 * Second ones on the other hand belong directly to the inner elements of pref window
 */
sealed interface FAHCallback{
    fun notifyBaseClick(prefParentPos: Int)
    fun getAddAlarmParentVisibility(): Boolean
    fun addItem(alarm: Alarm)
    fun changeItemTime(newHour: Int, newMinute: Int)
    fun updateInternalProperties(alarm: Alarm)
    fun updateParent()
    fun deleteItem(adapterPos: Int)
    fun launchHidePref()
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
    fun getDrawables(): MainDrawables
    //Using by VH to retrieve Fragment Manager for showing Time Picker
    fun getFragmentManager(): FragmentManager

}

/**
 * [MeasurementsAide] uses this interface to communicate with [FreeAlarmsHandler] directly.
 * When it comes to creating a new instance of Aide, it's [RowLayoutManager] who call the shots
 */
sealed interface AideCallback{
    //This is how RLM takes it's aide
    fun getMeasurements(): MeasurementsAide?
    //FAH creates an aide and puts inside variable
    fun createMeasurements(recycler: RecyclerView.Recycler, master: RecyclerView.LayoutManager)
    fun getItemViewHolder(adapterPos: Int): ViewHolder?
    fun saveRVState(state: IntArray)
}

/**
 * It's not always a direct one-step process. In some case we need [RowLayoutManager] to internally define us
 * which action was taken place and prepare itself for the future layout
 */
interface RLMCallback{
    /**
     * @return flag value, must be one of
     * [RowLayoutManager.LAYOUT_PREF], [RowLayoutManager.HIDE_PREF], [RowLayoutManager.HIDE_N_LAYOUT_PREF]
     */
    fun defineBaseAction (prefParentPos: Int, changeTime: Boolean) : RLMReturnData

    /**
     * This one's using when Handler needs to define values for pref layout,
     * but in RLM we yet don't have a usual set of data to calculate it in a normal way
     */
    fun setNotifyUpdate(flag: Int)
    fun isBusy(): Boolean
    fun setRVState(state: IntArray)
    fun manipulatePrefPowerState(enabled: Boolean): Int
}

interface FA_to_M_Callback{
    fun showAdd()
    fun createBrandNewRecycler(afterError: Boolean)
    fun clearOrFill(fill: Boolean)
    fun externalDismiss(alarmID: String, preliminaryOnly: Boolean)
}

/**
 * Class in which we retrieve computed data from RLM. Using for one case only
 */
class RLMReturnData(){
    var flag: Int = -1
        private set
    var parentPos: Int = -1
        private set
    var prefPos: Int = -1
        private set
    var pullDataset: Boolean = false
        private set

    constructor(hideNLayout: Boolean, parentPos: Int, prefPos: Int): this(){
        flag = if (!hideNLayout) RowLayoutManager.LAYOUT_PREF else RowLayoutManager.HIDE_N_LAYOUT_PREF
        this.parentPos = parentPos
        this.prefPos = prefPos
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
    private var recyclerView: RecyclerView,
){

    private val master: RowLayoutManager = recyclerView.layoutManager!! as RowLayoutManager
    private val sample: View = recycler.getViewForPosition(0)

    private val mTopPadding: Int
    private val mNormalSidePadding: Int
    private val mShrankSidePadding: Int
    private val mBaseVerticalPadding: Int
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


        master.measureChild(sample,0,0)

        measuredTimeWidth = master.getDecoratedMeasuredWidth(sample)
        measuredTimeHeight = master.getDecoratedMeasuredHeight(sample)

        mNormalSidePadding = ((master.width - measuredTimeWidth*3).toFloat()/4).roundToInt()
        mShrankSidePadding = mNormalSidePadding / 2

        sample.findViewById<View>(R.id.rv_time_window).visibility = View.GONE
        sample.findViewById<View>(R.id.pref_consistent_views_layout).visibility = View.VISIBLE
        sample.findViewById<View>(R.id.rv_pref_frame).visibility = View.VISIBLE
        sample.findViewById<View>(R.id.rv_pref_weekdays_container).visibility = View.VISIBLE

        master.measureChild(sample, 0,0)

        measuredPrefWidth = master.getDecoratedMeasuredWidth(sample)
        measuredPrefHeight = master.getDecoratedMeasuredHeight(sample)

        sample.findViewById<View>(R.id.rv_time_window).visibility = View.VISIBLE
        sample.findViewById<View>(R.id.pref_consistent_views_layout).visibility = View.GONE
        sample.findViewById<View>(R.id.rv_pref_frame).visibility = View.GONE
        sample.findViewById<View>(R.id.rv_pref_weekdays_container).visibility = View.GONE

        slU.f("measurements:\n\t\tTop padding: $mTopPadding, vertical padding: $mBaseVerticalPadding \n\t\t" +
                "Normal side padding: $mNormalSidePadding, shrank side padding: $mShrankSidePadding\n\t\t" +
                "T/w width: $measuredTimeWidth, t/w height: $measuredTimeHeight\n\t\t" +
                "Pref width: $measuredPrefWidth, pref height: $measuredPrefHeight")
    }

    fun setNewRV(rv: RecyclerView) {
        recyclerView = rv
        recyclerView.setPadding(0,mTopPadding,0,(mTopPadding * (2f/3f)).toInt())
    }

    fun getHorizontalPadding(): Int = mNormalSidePadding
    //This is for edge paddings
    fun getHorizontalPadding(iterator: Int): Int {
        //first index in mother row
        val rangeStart = (master.prefRowPos-2)*3
        val motherRange = IntRange(rangeStart, rangeStart+2)

        return if (iterator in motherRange)
            if (iterator == master.prefParentPos)
                mNormalSidePadding + mShrankSidePadding
            else mShrankSidePadding
        else mNormalSidePadding
    }
    fun getVerticalPadding(): Int = mBaseVerticalPadding
//    fun getMeasuredTimeWidth(iterator: Int) = if (iterator!=master.prefParentPos) measuredTimeWidth
//        else mEnvoyWidth
//    fun getMeasuredTimeHeight(iterator: Int) = if (iterator!=master.prefParentPos) measuredTimeHeight
//        else mEnvoyHeight
    fun getDecoratedTimeWidth(): Int = measuredTimeWidth + mNormalSidePadding
    //It's made to run through all motherRow's views an get their relative positions
    fun getDecoratedTimeWidth(iterator: Int): Int {

        return if (getParentAlignment(iterator)!=null){
            measuredTimeWidth.plus(
                when (iterator - master.prefParentPos) {
                    -1 -> mNormalSidePadding + mShrankSidePadding
                    0 -> mNormalSidePadding + mShrankSidePadding
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

