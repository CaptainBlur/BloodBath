package com.vova9110.bloodbath.alarmScreenBackground

import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import com.vova9110.bloodbath.R
import java.util.*

data class SubInfo(
    private var firingDate: Date?,
    val id: String,
    val snoozed: Boolean,
    //indicates current neediness for preliminary
    val preliminary: Boolean,
    val preliminaryTime: Int,

    var soundPath: Uri?,
    val vibrate: Boolean = true,

    val volumeLock: Boolean = true,
    val volume: Int = 0,
    val duration: Int = 15,

    val threshold: Float = 2.2f,
    val noiseOffset: Int = 0,

    val noiseDuration: Long = 5,//in minutes
    val timeWarning: Int,
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


        if (soundPath ==null) soundPath = Uri.parse("android.resource://com.vova9110.bloodbath/${R.raw.meeting}")
    }

    private constructor(parcel: Parcel) : this(
        Date(parcel.readLong()),
        parcel.readString()!!,
        parcel.readInt()==1,
        parcel.readInt()==1,
        parcel.readInt(),
        Uri.parse(parcel.readString()),
        parcel.readInt()==1,
        parcel.readInt()==1,
        parcel.readInt(),
        parcel.readInt(),
        parcel.readFloat(),
        parcel.readInt(),
        parcel.readLong(),
        parcel.readInt(),
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
        parcel.writeString(soundPath.toString())
        parcel.writeInt(if(vibrate) 1 else 0)
        parcel.writeInt(if(volumeLock) 1 else 0)
        parcel.writeInt(volume)
        parcel.writeInt(duration)
        parcel.writeFloat(threshold)
        parcel.writeInt(noiseOffset)
        parcel.writeLong(noiseDuration)
        parcel.writeInt(timeWarning)
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
