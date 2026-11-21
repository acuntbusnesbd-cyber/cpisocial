package com.example.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromStringList(value: String?): List<String> {
        if (value.isNullOrEmpty()) return emptyList()
        return value.split(",")
    }

    @TypeConverter
    fun toStringList(list: List<String>?): String {
        if (list.isNullOrEmpty()) return ""
        return list.joinToString(",")
    }

    @TypeConverter
    fun fromIntList(value: String?): List<Int> {
        if (value.isNullOrEmpty()) return emptyList()
        return value.split(",").mapNotNull { it.toIntOrNull() }
    }

    @TypeConverter
    fun toIntList(list: List<Int>?): String {
        if (list.isNullOrEmpty()) return ""
        return list.joinToString(",")
    }
}
