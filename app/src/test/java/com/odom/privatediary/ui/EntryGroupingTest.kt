package com.odom.privatediary.ui

import com.odom.privatediary.data.EntryEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class EntryGroupingTest {
    private fun entry(id: String, date: String, createdAt: Long, emotion: String?) = EntryEntity(
        id = id, date = date, createdAt = createdAt, durationSec = 10, audioPath = null,
        transcript = "t", summary = null, emotion = emotion, intensity = null,
        causes = emptyList(), keywords = emptyList(), reply = null, crisis = false,
        analysisStatus = "done",
    )

    @Test fun groups_entries_by_date() {
        val entries = listOf(
            entry("a", "2026-10-01", 1, "평온"),
            entry("b", "2026-10-01", 2, "불안"),
            entry("c", "2026-10-02", 3, "기쁨"),
        )
        val grouped = EntryGrouping.byDate(entries)
        assertEquals(2, grouped.size)
        assertEquals(2, grouped["2026-10-01"]!!.size)
        assertEquals(1, grouped["2026-10-02"]!!.size)
    }

    @Test fun dominant_emotion_is_latest_entry() {
        val entries = listOf(
            entry("a", "2026-10-01", 1, "평온"),
            entry("b", "2026-10-01", 5, "불안"),
            entry("c", "2026-10-01", 3, "기쁨"),
        )
        assertEquals("불안", EntryGrouping.dominantEmotion(entries))
    }
}
