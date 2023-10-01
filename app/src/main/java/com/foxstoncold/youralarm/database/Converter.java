package com.foxstoncold.youralarm.database;

import android.net.Uri;

import androidx.room.TypeConverter;

import com.google.gson.Gson;

import java.util.Date;

public class Converter {
    @TypeConverter
    public Date timeFromLong (Long value) {
        return value == null ? null : new Date(value);
    }
    @TypeConverter
    public Long longFromTime (Date date) {
        return date == null ? null : date.getTime();
    }

    @TypeConverter
    public String stringFromUri(Uri uri) { return uri == null ? null : uri.toString(); }
    @TypeConverter
    public Uri uriFromString(String string){ return string == null ? null : Uri.parse(string); }

    @TypeConverter
    public String jsonFromArray (boolean[] array){ return new Gson().toJson(array); }
    @TypeConverter
    public boolean[] arrayFromJson(String json){ return new Gson().fromJson(json, boolean[].class); }
}
