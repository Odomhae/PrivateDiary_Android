package com.odom.privatediary.pipeline

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelGateTest {
    @Test fun jobs_do_not_overlap_and_second_waits() = runTest {
        val gate = ModelGate()
        val log = mutableListOf<String>()
        var waited = false
        launch { gate.withModel { log += "a-in"; delay(100); log += "a-out" } }
        launch { gate.withModel(onWait = { waited = true }) { log += "b-in"; log += "b-out" } }
        testScheduler.advanceUntilIdle()
        assertEquals(listOf("a-in", "a-out", "b-in", "b-out"), log)
        assertTrue(waited)
    }

    @Test fun lock_is_released_after_exception() = runTest {
        val gate = ModelGate()
        runCatching { gate.withModel { error("boom") } }
        var waited = false
        assertEquals(1, gate.withModel(onWait = { waited = true }) { 1 })
        assertTrue(!waited)
    }
}
