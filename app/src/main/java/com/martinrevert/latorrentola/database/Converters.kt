package com.martinrevert.latorrentola.database

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.martinrevert.latorrentola.model.YTS.Cast
import com.martinrevert.latorrentola.model.YTS.Torrent
import java.util.Date

class Converters {
    @TypeConverter
    fun fromTorrentString(value: String?): List<Torrent>? {
        val listType = object : TypeToken<List<Torrent>>() {}.type
        return Gson().fromJson(value, listType)
    }

    @TypeConverter
    fun fromTorrentList(list: List<Torrent>?): String? {
        return Gson().toJson(list)
    }

    @TypeConverter
    fun fromString(value: String?): List<String>? {
        val listType = object : TypeToken<List<String>>() {}.type
        return Gson().fromJson(value, listType)
    }

    @TypeConverter
    fun fromArrayList(list: List<String>?): String? {
        return Gson().toJson(list)
    }

    @TypeConverter
    fun fromCastString(value: String?): List<Cast>? {
        val listType = object : TypeToken<List<Cast>>() {}.type
        return Gson().fromJson(value, listType)
    }

    @TypeConverter
    fun fromCastList(list: List<Cast>?): String? {
        return Gson().toJson(list)
    }

    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
}
