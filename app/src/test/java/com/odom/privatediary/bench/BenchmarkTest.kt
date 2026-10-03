package com.odom.privatediary.bench

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BenchmarkTest {
    @Test fun measure_latency_returns_value_and_nonnegative_ms() {
        val (value, ms) = Benchmark.measureLatency { 21 + 21 }
        assertEquals(42, value)
        assertTrue(ms >= 0)
    }
}
