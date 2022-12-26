package com.vova9110.bloodbath.alarmsUI

import android.content.Context
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.vova9110.bloodbath.SplitLogger
import com.vova9110.bloodbath.alarmScreenBackground.AlarmRepo
import com.vova9110.bloodbath.database.Alarm
import com.vova9110.bloodbath.recyclerView.AlarmListAdapter
import com.vova9110.bloodbath.recyclerView.RowLayoutManager
import java.util.*
import kotlin.Comparator

typealias sl = SplitLogger

class FreeAlarmsHandler(private val repo: AlarmRepo, private val context: Context): HandlerCallback {

    private lateinit var recycler: RecyclerView
    private lateinit var adapter: AlarmListAdapter
    private val comp = kotlin.Comparator { a1:Alarm, a2:Alarm-> if (a1.addFlag) 1 else if (a2.addFlag) -1 else 0 }.
        then { a1:Alarm, a2:Alarm-> if (a1.prefBelongsToAdd) 1 else if (a2.prefBelongsToAdd) -1 else 0 }.
            then { (hour1, minute1): Alarm, (hour2, minute2):Alarm-> if (hour1!=hour2) hour1-hour2 else minute1-minute2 }

    private val addAlarm = Alarm()

    fun pollForList(): AlarmListAdapter {

        var list: MutableList<Alarm> = repo.all.toMutableList()
        if (list.isEmpty()) {
            list = MutableList(1) { addAlarm }
        }
        else list.add(addAlarm)

        list.sortWith(comp)
        this.adapter = AlarmListAdapter(this, list)

        return adapter
    }
    fun setRecycler(recycler: RecyclerView){
        this.recycler = recycler
    }

    lateinit var rlmCallback: RLMCallback
    private var bufferList = LinkedList<Alarm>()

    @JvmField
    var repeatButton = false
    @JvmField
    var activeButton = false


    override fun passPrefToAdapter(parentPos: Int, prefPos: Int) {
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

    override fun removePref(pullDataset: Boolean) {
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

    override fun removeNPassPrefToAdapter(parentPos: Int, prefPos: Int) {
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

    override fun deleteItem(pos: Int) {
        rlmCallback.setNotifyFlag(RowLayoutManager.UPDATE_DATASET)

        val currentPos: Int
        var prefRemoved = false
        bufferList.clear()
        bufferList.addAll(adapter.currentList)
        val current = bufferList[pos]

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

    override fun addItem(hour: Int, minute: Int) {
        rlmCallback.setNotifyFlag(RowLayoutManager.UPDATE_DATASET)

        val currentPos: Int
        bufferList.clear()
        bufferList.addAll(adapter.currentList)

        if (bufferList.find { it.hour==hour && it.minute==minute }!=null){
            Toast.makeText(context, "Alarm already exists", Toast.LENGTH_SHORT).show()
            sl.ip("alarm already exists, exiting")
            return
        }

        val current = Alarm(hour, minute)
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

    override fun changeItem(oldPrefPos: Int, hour: Int, minute: Int) {
        rlmCallback.setNotifyFlag(RowLayoutManager.UPDATE_DATASET)

        if (bufferList.find { it.hour==hour && it.minute==minute }!=null){
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

        val new = current.clone(hour, minute)
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
        current.repeatable = repeatButton
        current.detection = activeButton
        Toast.makeText(context, "enabled: ${current.enabled}, repeatable: ${current.repeatable}, detect: ${current.detection}", Toast.LENGTH_LONG).show()
        repo.update(current, true)
//        bufferList[parentPos] = current
    }

    override fun pullRLMCallback(): RLMCallback = rlmCallback

    fun fill() {
        repo.deleteAll()
        bufferList = LinkedList<Alarm>()
        for (i in 0..27) {
            val alarm = Alarm(0, i)
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

    fun addTest(i: Int) {

    }
}

