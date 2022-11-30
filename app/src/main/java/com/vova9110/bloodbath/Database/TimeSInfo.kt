package com.vova9110.bloodbath.Database

import android.net.Uri
import android.os.Parcel
import android.os.Parcelable
import com.vova9110.bloodbath.R
import java.util.*

data class TimeSInfo(
    private var firingDate: Date?,
    var soundPath: Uri?,

    val duration: Int = 15,
    val threshold: Float = 2.2f,
    val noiseOffset: Int = 0,
    val noiseDuration: Long = 5,

    val timeWarning: Int,
    val timeSnoozed: Int,
    val timeBorderline: Int,
    val timeLost: Long
): Parcelable {
    private val calendar = Calendar.getInstance()
    val firingHour: Int
    val firingMinute: Int

    init {
        if (firingDate==null) firingDate = calendar.time
        else calendar.time = firingDate!!
        firingHour = calendar.get(Calendar.HOUR_OF_DAY)
        firingMinute = calendar.get(Calendar.MINUTE)

        if (soundPath==null) soundPath = Uri.parse("android.resource://com.vova9110.bloodbath/${R.raw.meeting}")
    }

    private constructor(parcel: Parcel) : this(
        Date(parcel.readLong()),
        Uri.parse(parcel.readString()),
        parcel.readInt(),
        parcel.readFloat(),
        parcel.readInt(),
        parcel.readLong(),
        parcel.readInt(),
        parcel.readInt(),
        parcel.readInt(),
        parcel.readLong()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeLong(firingDate!!.time)
        parcel.writeString(soundPath.toString())
        parcel.writeInt(duration)
        parcel.writeFloat(threshold)
        parcel.writeInt(noiseOffset)
        parcel.writeLong(noiseDuration)
        parcel.writeInt(timeWarning)
        parcel.writeInt(timeSnoozed)
        parcel.writeInt(timeBorderline)
        parcel.writeLong(timeLost)
    }

    override fun describeContents(): Int = 0
    companion object CREATOR : Parcelable.Creator<TimeSInfo> {
        override fun createFromParcel(parcel: Parcel): TimeSInfo {
            return TimeSInfo(parcel)
        }

        override fun newArray(size: Int): Array<TimeSInfo?> {
            return arrayOfNulls(size)
        }
    }

}
