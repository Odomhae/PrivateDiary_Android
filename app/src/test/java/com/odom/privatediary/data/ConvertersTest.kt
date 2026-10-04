package com.odom.privatediary.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ConvertersTest {
    private val c = Converters()

    @Test fun round_trips_list() {
        val list = listOf("일", "관계", "면접")
        assertEquals(list, c.toStringList(c.fromStringList(list)))
    }

    @Test fun empty_list_round_trips() {
        assertEquals(emptyList<String>(), c.toStringList(c.fromStringList(emptyList())))
    }

    @Test fun blank_json_is_empty_list() {
        assertEquals(emptyList<String>(), c.toStringList(""))
    }
}
