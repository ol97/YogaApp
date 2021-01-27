package com.example.yogaapp

import android.os.Parcel
import android.os.Parcelable

// helper class introduced to allow saving list of pose names and timestamps in savedInstanceState Bundle
// data type has to implement Parcelable to do that, so new class had to be defined
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