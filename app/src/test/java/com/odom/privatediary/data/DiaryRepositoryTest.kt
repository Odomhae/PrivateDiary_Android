package com.odom.privatediary.data

import com.odom.privatediary.bench.AnalysisResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class DiaryRepositoryTest {
    private class FakeEntryDao : EntryDao {
        val upserted = mutableListOf<EntryEntity>()
        val deleted = mutableListOf<String>()
        override suspend fun upsert(entry: EntryEntity) { upserted += entry }
        override suspend fun byId(id: String) = upserted.find { it.id == id }
        override suspend fun byDate(date: String) = upserted.filter { it.date == date }
        override fun observeAll(): Flow<List<EntryEntity>> = flowOf(upserted.toList())
        override suspend fun updateTranscript(id: String, transcript: String) {}
        override suspend fun delete(id: String) { deleted += id }
    }

    private val analysis = AnalysisResult(
        "요약", "평온", 2, listOf("일"), listOf("면접"), "공감", false, "{}", true,
    )

    @Test fun saveEntry_builds_entity_with_injected_id_and_date_then_upserts() = runTest {
        val dao = FakeEntryDao()
        val repo = DiaryRepository(
            dao,
            now = { 1_700_000_000_000L },  // 2023-11-14 UTC
            idGen = { "fixed-id" },
            zone = ZoneId.of("UTC"),
        )
        val id = repo.saveEntry("전사문", analysis, durationSec = 42)
        assertEquals("fixed-id", id)
        assertEquals(1, dao.upserted.size)
        val e = dao.upserted[0]
        assertEquals("fixed-id", e.id)
        assertEquals("2023-11-14", e.date)
        assertEquals("전사문", e.transcript)
        assertEquals("done", e.analysisStatus)
        assertEquals(42, e.durationSec)
        assertEquals(listOf("일"), e.causes)
    }

    @Test fun delete_delegates_to_dao() = runTest {
        val dao = FakeEntryDao()
        DiaryRepository(dao).deleteEntry("x")
        assertTrue(dao.deleted.contains("x"))
    }
}
