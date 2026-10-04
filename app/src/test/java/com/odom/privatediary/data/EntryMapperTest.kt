package com.odom.privatediary.data

import com.odom.privatediary.bench.AnalysisResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EntryMapperTest {
    private fun analysis(parseOk: Boolean) = AnalysisResult(
        summary = if (parseOk) "면접 대기로 불안" else "",
        emotion = if (parseOk) "불안" else null,
        intensity = 4,
        causes = listOf("일"),
        keywords = listOf("면접"),
        reply = if (parseOk) "마음 졸이셨겠어요" else "",
        crisis = false,
        raw = "{}",
        parseOk = parseOk,
    )

    @Test fun maps_successful_analysis_as_done() {
        val e = EntryMapper.toEntry("id1", "2026-10-04", 123L, 42, "전사문", analysis(true))
        assertEquals("id1", e.id)
        assertEquals("2026-10-04", e.date)
        assertEquals("전사문", e.transcript)
        assertEquals("불안", e.emotion)
        assertEquals(4, e.intensity)
        assertEquals(listOf("일"), e.causes)
        assertEquals(listOf("면접"), e.keywords)
        assertEquals("done", e.analysisStatus)
        assertEquals(42, e.durationSec)
    }

    @Test fun maps_failed_analysis_as_failed_with_null_intensity_and_summary() {
        val e = EntryMapper.toEntry("id2", "2026-10-04", 1L, 10, "원문만", analysis(false))
        assertEquals("failed", e.analysisStatus)
        assertNull(e.intensity)
        assertNull(e.summary)
        assertNull(e.emotion)
        assertNull(e.reply)
        assertEquals("원문만", e.transcript)
    }
}
