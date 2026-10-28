package com.odom.privatediary.report

import com.odom.privatediary.bench.TextGenerator
import com.odom.privatediary.data.WeeklyReportEntity
import com.odom.privatediary.pipeline.ModelGate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeeklyReportGeneratorTest {
    private val mon = LocalDate.of(2026, 10, 19)

    private class FakeGenerator(private val output: () -> String) : TextGenerator {
        val calls = mutableListOf<String>()
        override suspend fun load() { calls += "load" }
        override suspend fun generate(system: String, prompt: String): String { calls += "generate"; return output() }
        override fun release() { calls += "release" }
    }

    private fun generator(
        entries: List<com.odom.privatediary.data.EntryEntity>,
        fake: FakeGenerator,
        saved: MutableList<WeeklyReportEntity>,
        ranges: MutableList<Pair<String, String>> = mutableListOf(),
    ) = WeeklyReportGenerator(
        entries = { s, e -> ranges += s to e; entries },
        save = { saved += it },
        generatorFactory = { fake },
        gate = ModelGate(),
        now = { 42L },
    )

    @Test fun saves_cleaned_report_keyed_by_week() = runTest {
        val fake = FakeGenerator { "```\n이번 주는 평온했어요.\n```" }
        val saved = mutableListOf<WeeklyReportEntity>()
        val ranges = mutableListOf<Pair<String, String>>()
        val out = generator(listOf(entry("2026-10-20")), fake, saved, ranges).generate(mon)
        val report = WeeklyReportEntity("week-2026-10-19", "2026-10-19", "이번 주는 평온했어요.", 42L)
        assertEquals(WeeklyReportGenerator.Outcome.Saved(report), out)
        assertEquals(listOf(report), saved)
        assertEquals(listOf("2026-10-19" to "2026-10-25"), ranges)
        assertEquals(listOf("load", "generate", "release"), fake.calls)
    }

    @Test fun no_usable_entries_skips_model() = runTest {
        val fake = FakeGenerator { "x" }
        val saved = mutableListOf<WeeklyReportEntity>()
        val out = generator(listOf(entry("2026-10-20", status = "failed")), fake, saved).generate(mon)
        assertEquals(WeeklyReportGenerator.Outcome.NoEntries, out)
        assertTrue(fake.calls.isEmpty())
        assertTrue(saved.isEmpty())
    }

    @Test fun generation_error_propagates_and_releases() = runTest {
        val fake = FakeGenerator { error("engine") }
        val saved = mutableListOf<WeeklyReportEntity>()
        val r = runCatching { generator(listOf(entry("2026-10-20")), fake, saved).generate(mon) }
        assertTrue(r.isFailure)
        assertEquals(listOf("load", "generate", "release"), fake.calls)
        assertTrue(saved.isEmpty())
    }

    @Test fun blank_output_is_a_failure() = runTest {
        val saved = mutableListOf<WeeklyReportEntity>()
        val r = runCatching { generator(listOf(entry("2026-10-20")), FakeGenerator { "  " }, saved).generate(mon) }
        assertTrue(r.isFailure)
        assertTrue(saved.isEmpty())
    }
}
