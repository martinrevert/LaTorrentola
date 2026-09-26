package com.martinrevert.latorrentola.database

import androidx.room.TypeConverter
import java.util.Date

/** Converts values that Room cannot persist natively. */
class Converters {
    /** Converts Unix epoch milliseconds to a [Date], preserving `null`. */
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    /** Converts a [Date] to Unix epoch milliseconds, preserving `null`. */
    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
}
