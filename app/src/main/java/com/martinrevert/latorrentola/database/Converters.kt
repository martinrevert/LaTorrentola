package com.martinrevert.latorrentola.database;

import androidx.room.TypeConverter;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import com.martinrevert.latorrentola.model.YTS.Torrent;


import java.lang.reflect.Type;
import java.util.Date;
import java.util.List;

/**
 * Created by martin on 07/12/17.
 */

public class Converters {
    @TypeConverter
    public static List<Torrent> fromTorrentString(String value) {
        Type listType = new TypeToken<List<Torrent>>() {}.getType();
        return new Gson().fromJson(value, listType);
    }

    @TypeConverter
    public static String fromTorrentList(List<Torrent> list) {
        Gson gson = new Gson();
        String json = gson.toJson(list);
        return json;
    }

    @TypeConverter
    public static List<String> fromString(String value) {
        Type listType = new TypeToken<List<String>>() {}.getType();
        return new Gson().fromJson(value, listType);
    }

    @TypeConverter
    public static String fromArrayList(List<String> list) {
        Gson gson = new Gson();
        String json = gson.toJson(list);
        return json;
    }

    @TypeConverter
    public Date fromTimestamp(Long value) {
        return value == null ? null : new Date(value);
    }

    @TypeConverter
    public Long dateToTimestamp(Date date) {
        return date == null ? null : date.getTime();
    }




}
