package com.vova9110.bloodbath.database

import android.annotation.SuppressLint
import androidx.room.Entity
import androidx.room.Ignore
import java.util.*
import com.vova9110.bloodbath.SplitLogger
import com.vova9110.bloodbath.SplitLogger.Companion.printObject
import java.util.regex.Pattern

private typealias sl = SplitLogger

@Entity(tableName = "alarms_table", primaryKeys = ["hour", "minute"])
data class Alarm(var hour: Int, var minute: Int, var enabled: Boolean = false, var state: String = STATE_DISABLE) {
    var triggerTime: Date? = null
    var lastTriggerTime: Date? = null

    //Pref and Add views doesn't enter the the database
    @Ignore var addFlag: Boolean = false
    @Ignore var prefFlag: Boolean = false
    @Ignore var prefBelongsToAdd = false
    @Ignore var parentPos = 0

    @Ignore
    val id: String = String.format("%02d", this.hour) + String.format("%02d", this.minute)
    var vibrate = true
    var snoozed = false
    var repeatable = false
    /**
     * WARNING!
     * Before calculation, weekdays list should be normalized to the default
     * Russian week (monday first, sunday last)
     */
    var weekdays = BooleanArray(7)

    var detection = false
    var preliminary = false
    var preliminaryTime: Int = 0//in minutes before triggerTime

    @Ignore
    constructor(): this(-1,-1){ addFlag = true }

    @Ignore
    constructor(hour: Int, minute: Int, parentPos: Int, addAlarmPos: Int, enabled: Boolean): this(hour, minute){
        this.parentPos = parentPos
        this.enabled = enabled
        setPrefBelongsToAdd(parentPos, addAlarmPos)
        this.prefFlag = true
    }
    private fun setPrefBelongsToAdd(prefPos: Int, addAlarmPos: Int) {
        prefBelongsToAdd = prefPos==addAlarmPos
    }
    fun clone(newHour: Int, newMinute: Int): Alarm{
        return Alarm(newHour, newMinute, this.enabled, STATE_ALL).apply {
            this.triggerTime = this@Alarm.triggerTime
            this.repeatable = this@Alarm.repeatable
            this.weekdays = this@Alarm.weekdays
        }
    }



    //Using simple successive enumeration to find a target day to stick the time to
    //Calculation proceeds in American shifted weekdays order
    fun calculateTriggerTime(){
        val _h = Calendar.HOUR_OF_DAY
        val _m = Calendar.MINUTE

        assert(weekdays.size==7)

        val shifted = weekdays.toMutableList()
        Collections.rotate(shifted, 1)

        val cC = Calendar.getInstance().apply { this.firstDayOfWeek=Calendar.MONDAY } //declaring calendar instance with current time
        if ((this.hour < cC[_h]) || //we adding extra day if today's time is gone
                this.hour == cC[_h] && this.minute <= cC[_m])
            cC.add(Calendar.DATE, 1)
        var weekdayWalker = cC.get(Calendar.DAY_OF_WEEK)-1//Basically where all calculations occurred

        with(cC){
            this.set(Calendar.HOUR_OF_DAY, this@Alarm.hour)
            this.set(Calendar.MINUTE, this@Alarm.minute)
            this.set(Calendar.SECOND, 0)
            this.set(Calendar.MILLISECOND, 0)
        }
        if (weekdays.count { el-> el }==0){
            sl.frp("weekdays are empty, returning on *${cC.get(Calendar.DATE)},${cC.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT_STANDALONE, Locale.US)}* for *${id}*")
            triggerTime = cC.time
            return
        }
        while (true){
            if (shifted[weekdayWalker]){//We just picked one day out of a week
                sl.frp("returning on *${cC.get(Calendar.DATE)},${cC.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT_STANDALONE, Locale.US)}* for *${id}*,")

                //Just try to enjoy it;)
                val weekdayPointer = if (weekdayWalker == 0) 6 else if (weekdayWalker == 6) 0 else weekdayWalker - 1
                val daysNames = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                var compositeString = "               "
                for (i in 0..6){
                    val mark = if (weekdayPointer==i) "\u2193" else " "
                    compositeString = if (weekdays[i]) compositeString+daysNames[i] + "$mark " else compositeString+daysNames[i] + "$mark  "
                }
                sl.fst(compositeString)
                sl.fst("weekdays are: ${weekdays.printObject()}")

                triggerTime = cC.time
                return
            }
            cC.add(Calendar.DATE, 1)
            weekdayWalker = cC.get(Calendar.DAY_OF_WEEK)-1
        }
    }

    companion object{
        const val STATE_DISABLE = "disable"
        const val STATE_SUSPEND = "suspend"
        const val STATE_ANTICIPATE = "anticipate"
        const val STATE_PREPARE = "prepare"
        const val STATE_FIRE = "fire"
        const val STATE_PREPARE_SNOOZE = "prepare_snooze"
        const val STATE_SNOOZE = "snooze"
        const val STATE_MISS = "miss"
        const val STATE_ALL = "all"

        @JvmStatic
        fun getHM(id: String): IntArray{
            if (Pattern.compile("-").matcher(id).find()){
                sl.sp("cannot proceed with negative nour or minute")
                return IntArray(2)
            }
            val array = IntArray(2)
            array[0] = id.substring(0,2).toInt()
            array[1] = id.substring(2,4).toInt()
            return array
        }
    }
}


