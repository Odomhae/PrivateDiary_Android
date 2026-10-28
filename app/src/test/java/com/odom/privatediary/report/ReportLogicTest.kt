package com.odom.privatediary.report

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReportLogicTest {
    private val mon = LocalDate.of(2026, 10, 19)

    // --- Week ---
    @Test fun week_start_is_previous_or_same_monday() {
        assertEquals(mon, Week.start(mon))
        assertEquals(mon, Week.start(LocalDate.of(2026, 10, 22)))
        assertEquals(mon, Week.start(LocalDate.of(2026, 10, 25))) // 일요일
        assertEquals(LocalDate.of(2026, 10, 26), Week.start(LocalDate.of(2026, 10, 26)))
    }

    @Test fun week_days_and_end() {
        val days = Week.days(mon)
        assertEquals(7, days.size)
        assertEquals(mon, days.first())
        assertEquals(LocalDate.of(2026, 10, 25), days.last())
        assertEquals(LocalDate.of(2026, 10, 25), Week.end(mon))
    }

    // --- WeeklyTrend ---
    @Test fun trend_picks_most_intense_done_entry_per_day_latest_on_tie() {
        val t = WeeklyTrend.of(
            mon,
            listOf(
                entry("2026-10-19", createdAt = 1, emotion = "피곤", intensity = 3),
                entry("2026-10-19", createdAt = 2, emotion = "기쁨", intensity = 3),
                entry("2026-10-19", createdAt = 3, emotion = "슬픔", intensity = 1),
                entry("2026-10-21", createdAt = 4, emotion = "불안", intensity = 5, status = "failed"),
            ),
        )
        assertEquals(7, t.days.size)
        assertEquals("기쁨", t.days[0].emotion)
        assertEquals(3, t.days[0].intensity)
        assertNull(t.days[1].emotion)
        assertNull(t.days[2].emotion) // failed는 제외
    }

    @Test fun trend_counts_sorted_by_count_then_label_order() {
        val t = WeeklyTrend.of(
            mon,
            listOf(
                entry("2026-10-19", emotion = "피곤"),
                entry("2026-10-20", emotion = "피곤"),
                entry("2026-10-21", emotion = "평온"),
                entry("2026-10-22", emotion = "기쁨"),
            ),
        )
        assertEquals(listOf("피곤" to 2, "기쁨" to 1, "평온" to 1), t.counts)
    }

    // --- WeeklyReportPrompt ---
    @Test fun prompt_lists_done_entries_in_time_order_and_hides_crisis_summary() {
        val p = WeeklyReportPrompt.build(
            listOf(
                entry("2026-10-21", createdAt = 2, emotion = "피곤", intensity = 3, summary = "야근"),
                entry("2026-10-20", createdAt = 1, emotion = "평온", intensity = 2, summary = "산책"),
                entry("2026-10-22", createdAt = 3, summary = "비밀스러운 내용", crisis = true),
                entry("2026-10-23", createdAt = 4, summary = "실패", status = "failed"),
            ),
        )!!
        val lines = p.lines()
        val i1 = lines.indexOf("화 10/20 · 평온(2) · 산책")
        val i2 = lines.indexOf("수 10/21 · 피곤(3) · 야근")
        assertTrue(i1 >= 0 && i2 > i1)
        assertTrue(lines.contains("목 10/22 · 힘든 날이 있었음"))
        assertFalse(p.contains("비밀스러운 내용"))
        assertFalse(p.contains("실패"))
    }

    @Test fun prompt_is_null_without_usable_entries() {
        assertNull(WeeklyReportPrompt.build(emptyList()))
        assertNull(WeeklyReportPrompt.build(listOf(entry("2026-10-20", status = "failed"))))
    }

    // --- ReportBody ---
    @Test fun body_strips_fences_quotes_and_trims() {
        assertEquals("이번 주는 평온했어요.", ReportBody.clean("```\n\"이번 주는 평온했어요.\"\n```"))
        assertEquals("문단", ReportBody.clean("  “문단”  "))
    }

    @Test fun body_is_capped_and_blank_is_null() {
        assertEquals(700, ReportBody.clean("가".repeat(800))!!.length)
        assertNull(ReportBody.clean("  ``` ```  "))
        assertNull(ReportBody.clean(""))
    }
}
