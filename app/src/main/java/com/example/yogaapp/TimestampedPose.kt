package com.example.yogaapp

import android.os.Parcel
import android.os.Parcelable

class TimestampedPose(var poseName: String, var timestamp: Long) : Parcelable {

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(poseName)
        parcel.writeLong(timestamp)
    }

    constructor(parcel: Parcel): this(parcel.readString().toString(), parcel.readLong())

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<TimestampedPose> {
        override fun createFromParcel(parcel: Parcel): TimestampedPose {
            return TimestampedPose(parcel)
        }

        override fun newArray(size: Int): Array<TimestampedPose?> {
            return arrayOfNulls(size)
        }
    }
}