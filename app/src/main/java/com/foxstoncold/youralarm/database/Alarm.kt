package com.foxstoncold.youralarm.database

import android.content.Context
import androidx.room.Entity
import androidx.room.Ignore
import com.foxstoncold.youralarm.MainViewModel
import java.util.*
import com.foxstoncold.youralarm.SplitLogger
import com.foxstoncold.youralarm.SplitLogger.Companion.printObject
import com.foxstoncold.youralarm.alarmScreenBackground.SubInfo
import java.util.regex.Pattern

private typealias sl = SplitLogger

@Entity(tableName = "alarms_table", primaryKeys = ["hour", "minute"])
data class Alarm(var hour: Int, var minute: Int, var enabled: Boolean = false, val test: Boolean = false) {
    var triggerTime: Date? = null
    var lastTriggerTime: Date? = null
    var state: String = STATE_DISABLE

    //Pref and Add views doesn't enter the the database
    @Ignore var addFlag: Boolean = false
    @Ignore var prefFlag: Boolean = false
    @Ignore var prefBelongsToAdd = false
    @Ignore var parentPos = -1

    @get:Ignore
    val id: String
        get() = String.format("%02d", this.hour) + String.format("%02d", this.minute)
    /*
    These two flags share kinda the same strategy of defining states:
    You set it when it's gone and then you're about to find out a proper state around that fact.
    Whereas Snoozed state is processed entirely by AED, the Preliminary state directly affects
    the way we calculate new triggerTime for the instance
     */
    var snoozed = false
    var preliminaryFired = false

    /**
     * WARNING!
     * Before calculation, weekdays list should be normalized to the default
     * Standard week (monday first, sunday last)
     */
    var weekdays = BooleanArray(7)
    //Now we have this value set as one for all enabled alarms, but soon it's
    //about to get individual
    var preliminaryTime = -1
    fun setPreliminaryTime(context: Context){
        val prefs = context.getSharedPreferences(MainViewModel.USER_SETTINGS_SP, Context.MODE_PRIVATE)
        preliminaryTime = prefs.getInt("preliminaryTime", 30)
        sl.fst("preliminary time is set for *$preliminaryTime*")
    }

    var repeatable = false

    /**
     * This function relies not only on the *repeatable* flag, but also on the emptiness of *weekdays* array
     */
    fun smartGetRepeatable(): Boolean{
        return if (weekdays.contentEquals(BooleanArray(7){false}) && repeatable){
            sl.fstp("forced to false")
            repeatable = false
            false
        } else repeatable
    }
    var vibrate = true
    var preliminary = false
    var detection = false

    @Ignore
    constructor(): this(-1,-1){ addFlag = true }


    //This one for copying pref's internals inside CVH
    @Ignore
    private constructor(hour: Int, minute: Int, enabled: Boolean,
            parentPos: Int, weekdays: BooleanArray, repeatable: Boolean, vibrate: Boolean,
            detection: Boolean, preliminary: Boolean) : this(hour, minute, enabled){
        this.parentPos = parentPos
        this.weekdays = weekdays
        this.repeatable = repeatable
        this.vibrate = vibrate
        this.detection = detection
        this.preliminary = preliminary
            }

    /* This one for creating new pref inside Handler.
    ^addAlarmPos^ is for detecting pref's belonging to add
    by comparing it to passed ^parentPos^
    */
    @Ignore
    private constructor(hour: Int, minute: Int, enabled: Boolean,
                parentPos: Int, weekdays: BooleanArray, repeatable: Boolean, vibrate: Boolean,
                detection: Boolean, preliminary: Boolean, addAlarmPos: Int):
            this(hour, minute, enabled, parentPos, weekdays, repeatable, vibrate, detection, preliminary){

        setPrefBelongsToAdd(parentPos, addAlarmPos)
        this.prefFlag = true
            }

    private fun setPrefBelongsToAdd(prefPos: Int, addAlarmPos: Int) {
        prefBelongsToAdd = prefPos==addAlarmPos
    }

    //Not including any dynamically assignable variables
    fun clone(): Alarm = Alarm(hour, minute, enabled, parentPos, weekdays, repeatable, vibrate, detection, preliminary)
    fun createPref(parentPos: Int, addAlarmPos: Int): Alarm =
        Alarm(hour, minute, enabled, parentPos, weekdays, repeatable, vibrate, detection, preliminary, addAlarmPos)



    //Using simple successive enumeration to find a target day to stick the time to
    //remember that triggerTime will be set anyway
    fun calculateTriggerTime(skipNearest: Boolean = false){
        val _h = Calendar.HOUR_OF_DAY
        val _m = Calendar.MINUTE

        assert(weekdays.size==7)
        val setPreliminary =  if (preliminary && !preliminaryFired) true.also { assert(preliminaryTime>0) { "valid preliminary time needed" } } else false

        val shifted = weekdays.toMutableList()
        Collections.rotate(shifted, 1)

        val cC = Calendar.getInstance().apply { firstDayOfWeek=Calendar.MONDAY } //declaring calendar instance with current time
        //adding extra day if an alarm for today is gone
        //or we're handling mandatory state change through notification
        if ((this.hour < cC[_h] ||
                this.hour == cC[_h] && this.minute <= cC[_m]) ||
                skipNearest)
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
            if (setPreliminary){
                cC.add(_m, -preliminaryTime)
                if (cC.time.after(Date())) {
                    triggerTime = cC.time
                    sl.frp("preliminary set: $preliminaryTime")
                }
                else{
                    sl.frp("setting preliminary is redundant. Checking firing flag")
                    preliminaryFired = true
                }
            }
            return
        }
        while (true){
            //entering endless loop and just picking one day out of a week
            if (shifted[weekdayWalker]){
                sl.frp("returning on *${cC.get(Calendar.DATE)},${cC.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT_STANDALONE, Locale.US)}* for *${id}*,")

                //Just try to enjoy it;)
                val weekdayPointer = if (weekdayWalker == 0) 6 else if (weekdayWalker == 7) 0 else weekdayWalker - 1
                val daysNames = arrayOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
                var compositeString = "               "
                for (i in 0..6){
                    val mark = if (weekdayPointer==i) "\u2193" else " "
                    compositeString = if (weekdays[i]) compositeString+daysNames[i] + "$mark " else compositeString+daysNames[i] + "$mark  "
                }
                sl.fst(compositeString)
                sl.fst("weekdays are: ${weekdays.printObject()}")
                if (skipNearest) sl.fst("(nearest skipped)")

                triggerTime = cC.time
                if (setPreliminary){
                    cC.add(_m, -preliminaryTime)
                    if (cC.time.after(Date())) {
                        triggerTime = cC.time
                        sl.frp("preliminary set: $preliminaryTime")
                    }
                    else{
                        sl.frp("setting preliminary is redundant. Checking firing flag")
                        preliminaryFired = true
                    }
                }
                return
            }
            cC.add(Calendar.DATE, 1)
            weekdayWalker = cC.get(Calendar.DAY_OF_WEEK)-1
        }
    }
    fun getInfo(context: Context): SubInfo{
        val prefs = context.getSharedPreferences(MainViewModel.USER_SETTINGS_SP, Context.MODE_PRIVATE)

        return SubInfo(
            triggerTime,
            id,
            snoozed,
            preliminary && !preliminaryFired,
            preliminaryTime,
            null,
            vibrate,
            prefs.getBoolean("volumeLock", false),
            prefs.getInt("volume", 1),
            prefs.getBoolean("rampUpVolume", false),
            prefs.getInt("rampUpVolumeTime", 0),
            prefs.getInt("steps", 30),
            prefs.getBoolean("noiseDetection", false),
            5,
            prefs.getInt("timeOut", 90),
            prefs.getInt("globalSnoozed", 8).toLong(),
            prefs.getInt("globalLost", 10).toLong())
    }

    companion object{
        const val STATE_DISABLE = "disable"
        const val STATE_SUSPEND = "suspend"
        const val STATE_ANTICIPATE = "anticipate"
        const val STATE_PRELIMINARY = "preliminary"
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


