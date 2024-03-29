package com.foxstoncold.youralarm.alarmScreenBackground

import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import com.foxstoncold.youralarm.R
import java.util.*

data class SubInfo(
    private var firingDate: Date?,
    val id: String,
    val snoozed: Boolean,
    //indicates current neediness for preliminary
    val preliminary: Boolean,
    val preliminaryTime: Int,
    //not involved in states or time calculation. As far, only for showing info in AlarmActivity
    val preliminaryEnabled: Boolean,
    val activeness: Boolean,

    var soundPath: Uri?,
    val vibrate: Boolean = true,

    val volumeLock: Boolean = true,
    val volume: Int = 0,
    val rampUpVolume: Boolean = false,
    val rampUpVolumeTime: Int = 0,//in sec

    val steps: Int = 15,
    val noiseDetection: Boolean = false,

    val noiseDuration: Long = 5,//in minutes
    val timeOut: Int,
    //Next two values in minutes
    val globalSnoozed: Long,
    val globalLost: Long

): Parcelable {
    private val calendar = Calendar.getInstance()
    val firingHour: Int
    val firingMinute: Int

    init {
        if (firingDate==null) firingDate = calendar.time
        else calendar.time = firingDate!!
        firingHour = calendar.get(Calendar.HOUR_OF_DAY)
        firingMinute = calendar.get(Calendar.MINUTE)

//        if (soundPath == null) soundPath =
    }

    private constructor(parcel: Parcel) : this(
        Date(parcel.readLong()),
        parcel.readString()!!,
        parcel.readInt()==1,
        parcel.readInt()==1,
        parcel.readInt(),
        parcel.readInt()==1,
        parcel.readInt()==1,
        Uri.parse(parcel.readString()),
        parcel.readInt()==1,
        parcel.readInt()==1,
        parcel.readInt(),
        parcel.readInt()==1,
        parcel.readInt(),
        parcel.readInt(),
        parcel.readInt()==1,
        parcel.readLong(),
        parcel.readInt(),
        parcel.readLong(),
        parcel.readLong(),
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeLong(firingDate!!.time)
        parcel.writeString(id)
        parcel.writeInt(if(snoozed) 1 else 0)
        parcel.writeInt(if(preliminary) 1 else 0)
        parcel.writeInt(preliminaryTime)
        parcel.writeBoolean(preliminaryEnabled)
        parcel.writeBoolean(activeness)
        parcel.writeString(soundPath.toString())
        parcel.writeInt(if(vibrate) 1 else 0)
        parcel.writeInt(if(volumeLock) 1 else 0)
        parcel.writeInt(volume)
        parcel.writeInt(if(rampUpVolume) 1 else 0)
        parcel.writeInt(rampUpVolumeTime)
        parcel.writeInt(steps)
        parcel.writeBoolean(noiseDetection)
        parcel.writeLong(noiseDuration)
        parcel.writeInt(timeOut)
        parcel.writeLong(globalSnoozed)
        parcel.writeLong(globalLost)
    }

    override fun describeContents(): Int = 0
    companion object CREATOR : Parcelable.Creator<SubInfo> {
        override fun createFromParcel(parcel: Parcel): SubInfo {
            return SubInfo(parcel)
        }

        override fun newArray(size: Int): Array<SubInfo?> {
            return arrayOfNulls(size)
        }
    }

}
