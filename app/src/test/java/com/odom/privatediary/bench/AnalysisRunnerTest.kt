package com.odom.privatediary.bench

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisRunnerTest {
    private val valid =
        """{"summary":"s","emotion":"평온","intensity":2,"causes":[],"keywords":[],"reply":"r","crisis":false}"""

    @Test fun returns_on_first_success_without_retry() = runTest {
        var calls = 0
        val r = AnalysisRunner.analyzeWithRetry("t") { calls++; valid }
        assertTrue(r.parseOk)
        assertEquals(1, calls)
    }

    @Test fun retries_once_then_succeeds() = runTest {
        var calls = 0
        val r = AnalysisRunner.analyzeWithRetry("t") { calls++; if (calls == 1) "garbage" else valid }
        assertTrue(r.parseOk)
        assertEquals(2, calls)
    }

    @Test fun both_fail_returns_parse_failed_after_two_calls() = runTest {
        var calls = 0
        val r = AnalysisRunner.analyzeWithRetry("t") { calls++; "nope" }
        assertFalse(r.parseOk)
        assertEquals(2, calls)
    }
}
