package com.odom.privatediary.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchQueryTest {
    @Test fun trims_and_wraps() = assertEquals("%회사%", SearchQuery.toLikePattern("  회사 "))

    @Test fun escapes_like_wildcards_and_escape_char() {
        assertEquals("%100\\%%", SearchQuery.toLikePattern("100%"))
        assertEquals("%a\\_b%", SearchQuery.toLikePattern("a_b"))
        assertEquals("%\\\\%", SearchQuery.toLikePattern("\\"))
    }

    @Test fun blank_is_null() {
        assertNull(SearchQuery.toLikePattern(""))
        assertNull(SearchQuery.toLikePattern("   "))
    }
}
