package com.odom.privatediary.data

import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

class Converters {
    private val serializer = ListSerializer(String.serializer())

    @TypeConverter
    fun fromStringList(list: List<String>): String = Json.encodeToString(serializer, list)

    @TypeConverter
    fun toStringList(json: String): List<String> =
        if (json.isBlank()) emptyList() else Json.decodeFromString(serializer, json)
}
