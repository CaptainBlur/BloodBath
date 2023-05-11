package com.vova9110.bloodbath.alarmsUI

import android.content.Context
import android.graphics.Rect
import android.widget.Toast
import com.vova9110.bloodbath.SplitLogger
import com.vova9110.bloodbath.alarmScreenBackground.AlarmRepo
import com.vova9110.bloodbath.database.Alarm
import com.vova9110.bloodbath.alarmsUI.recyclerView.AlarmListAdapter
import com.vova9110.bloodbath.alarmsUI.recyclerView.RowLayoutManager
import java.lang.Exception
import java.util.*

typealias sl = SplitLogger

class FreeAlarmsHandler(private val repo: AlarmRepo, private val context: Context,
                        targetView: AdjustableView, globalRect: Rect,
                        override val transmitterMethod: () -> Unit
): TargetedHandler(targetView, globalRect),
                        FAHCallback, ErrorReceiver {

    private val recycler = targetView as AdjustableRecyclerView
    private var adapter: AlarmListAdapter
    private var rlmCallback: RLMCallback
    private val comp = kotlin.Comparator { a1:Alarm, a2:Alarm-> if (a1.addFlag) 1 else if (a2.addFlag) -1 else 0 }.
        then { a1:Alarm, a2:Alarm-> if (a1.prefBelongsToAdd) 1 else if (a2.prefBelongsToAdd) -1 else 0 }.
            then { (hour1, minute1): Alarm, (hour2, minute2):Alarm-> if (hour1!=hour2) hour1-hour2 else minute1-minute2 }

    private val addAlarm = Alarm()

    init {
        adapter = pollForList()
        recycler.adapter = adapter

        val lm = RowLayoutManager(this)
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

        val lm = RowLayoutManager(this)
        recycler.layoutManager = lm
        rlmCallback = lm

        recycler.requestLayout()
    }

    private var bufferList = LinkedList<Alarm>()

    @JvmField
    var repeatButton = false
    @JvmField
    var activeButton = false

    override fun notifyBaseClick(prefParentPos: Int){
        val returned = rlmCallback.defineBaseAction(prefParentPos)

        when(returned.flag){
            RowLayoutManager.LAYOUT_PREF -> passPrefToAdapter(returned.parentPos, returned.prefPos)
            RowLayoutManager.HIDE_PREF -> removePref(returned.pullDataset)
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

    private fun removePref(pullDataset: Boolean) {
        bufferList.clear()
        if (pullDataset) bufferList.addAll(adapter.currentList) else bufferList.addAll(adapter.currentList)

        var prefIndex = -1
        for (i in bufferList.indices) if (bufferList[i].prefFlag) prefIndex=i
        bufferList.removeAt(prefIndex)
//        bufferList.forEach { if (it.prefFlag) prefIndex=bufferList.indexOf(it) }.also { bufferList.removeAt(prefIndex) }

        adapter.submitList(bufferList)
        adapter.notifyItemRemoved(prefIndex)
//        submitList(oldList, bufferList)
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

        adapter.notifyItemRemoved(exPrefIndex)
        adapter.notifyItemInserted(prefPos)
        adapter.submitList(bufferList)
    }

    /*

     */

    override fun deleteItem(adapterPos: Int) {
        rlmCallback.setUpdateDataset(RowLayoutManager.UPDATE_DATASET)

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
        rlmCallback.setUpdateDataset(RowLayoutManager.UPDATE_DATASET)

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

    override fun changeItem(adapterPos: Int, pickerNour: Int, pickerMinute: Int) {
        rlmCallback.setUpdateDataset(RowLayoutManager.UPDATE_DATASET)

        if (bufferList.find { it.hour==pickerNour && it.minute==pickerMinute }!=null){
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

        val new = current.clone(pickerNour, pickerMinute)
        repo.insert(new)
        bufferList.add(new)
        bufferList.sortWith(comp)
        val newPos = bufferList.indexOf(new)

        adapter.submitList(bufferList)
        recycler.post { adapter.notifyItemRemoved(prefPos) }
        recycler.post { adapter.notifyItemRemoved(oldPos) }
        recycler.post { adapter.notifyItemInserted(newPos) }
        recycler.post{ adapter.notifyItemChanged(newPos) }
    }

    override fun updateOneState(parentPos: Int, isChecked: Boolean) {
        bufferList.clear()
        bufferList.addAll(adapter.currentList)
        val current = bufferList[parentPos]

        current.enabled = isChecked
        current.detection = activeButton
        current.repeatable = repeatButton
        current.weekdays = if (current.repeatable) BooleanArray(7) { true } else BooleanArray(7) { false }
        Toast.makeText(context, "enabled: ${current.enabled}, repeatable: ${current.repeatable}, detect: ${current.detection}", Toast.LENGTH_LONG).show()
        repo.update(current, true)
//        bufferList[parentPos] = current
    }

    val alarm1 = Alarm(9,0, true)
    var alarm2 = Alarm(9, 30, true)
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
    fun tempUpdate(alarm: Alarm){
        repo.update(alarm, true)
    }
    fun tempDelete(alarm: Alarm){
        repo.deleteOne(alarm)
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

}

/**
 * Generally, there are two kinds of actions can be transmitted through this interface.
 * First ones belong to actions related to user's interaction with time window such as show and hide individual preferences window, and require deeper integration with [RowLayoutManager].
 * Second ones on the other hand belong directly to the inner elements of pref window
 */
sealed interface FAHCallback{
    fun notifyBaseClick(prefParentPos: Int)
    fun addItem(pickerNour: Int, pickerMinute: Int)
    fun changeItem(adapterPos: Int, pickerNour: Int, pickerMinute: Int)
    fun updateOneState(parentPos: Int, isChecked: Boolean)
    fun deleteItem(adapterPos: Int)
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

    fun setUpdateDataset(flag: Int)
}

data class RLMReturnData(var flag: Int = -1){
    var parentPos: Int = -1
    var prefPos: Int = -1
    var pullDataset: Boolean = false

    constructor(flag: Int, parentPos: Int, prefPos: Int): this(flag){
        this.parentPos = parentPos
        this.prefPos = prefPos
    }
    constructor(flag: Int, pullDataset: Boolean): this (flag){
        this.pullDataset = pullDataset
    }
}

