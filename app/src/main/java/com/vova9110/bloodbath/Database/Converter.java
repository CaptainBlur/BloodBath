package com.vova9110.bloodbath.Database;

import androidx.room.TypeConverter;

import java.util.Date;

public class Converter {
    @TypeConverter
    public Date timeFromLong (Long value) {
        return value == null ? null : new Date(value);
    }

    @TypeConverter
    public Long timeFromDate (Date date) {
        return date == null ? null : date.getTime();
    }
}
