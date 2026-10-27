package com.odom.privatediary.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchSnippetTest {
    private val long = "가".repeat(30) + "회사" + "나".repeat(30)

    @Test fun short_text_is_returned_whole() =
        assertEquals("오늘 회사 갔어", SearchSnippet.around("오늘 회사 갔어", "회사", radius = 20))

    @Test fun middle_match_is_trimmed_on_both_sides() =
        assertEquals("…" + "가".repeat(5) + "회사" + "나".repeat(5) + "…", SearchSnippet.around(long, "회사", radius = 5))

    @Test fun leading_match_has_no_leading_ellipsis() =
        assertEquals("회사" + "나".repeat(3) + "…", SearchSnippet.around("회사" + "나".repeat(10), "회사", radius = 3))

    @Test fun match_is_case_insensitive_and_query_trimmed() =
        assertEquals("…b KPI c…", SearchSnippet.around("aaaa b KPI c dddd", " kpi ", radius = 2))

    @Test fun no_match_returns_head() =
        assertEquals("가".repeat(10) + "…", SearchSnippet.around(long, "없음", radius = 5))
}
