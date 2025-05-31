package com.example.shortcutdemo

import android.content.Intent
import android.os.Parcel
import android.os.Parcelable

@Parcelize
data class ScreenRecordConfig(
    val resultCode: Int,
    val data: Intent?
): Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readInt(),
        parcel.readParcelable(Intent::class.java.classLoader)
    ) {
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(resultCode)
        parcel.writeParcelable(data, flags)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<ScreenRecordConfig> {
        override fun createFromParcel(parcel: Parcel): ScreenRecordConfig {
            return ScreenRecordConfig(parcel)
        }

        override fun newArray(size: Int): Array<ScreenRecordConfig?> {
            return arrayOfNulls(size)
        }
    }
}

annotation class Parcelize