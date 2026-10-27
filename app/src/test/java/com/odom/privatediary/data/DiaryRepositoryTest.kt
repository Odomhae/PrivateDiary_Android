package com.odom.privatediary.data

import com.odom.privatediary.bench.AnalysisResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        override suspend fun updateTranscript(id: String, transcript: String, crisis: Boolean) {
            val i = upserted.indexOfFirst { it.id == id }
            // 실제 쿼리와 같이 crisis = (crisis OR :crisis)
            if (i >= 0) upserted[i] = upserted[i].copy(transcript = transcript, crisis = upserted[i].crisis || crisis)
        }
        val searched = mutableListOf<String>()
        override fun search(pattern: String): Flow<List<EntryEntity>> { searched += pattern; return flowOf(upserted.toList()) }
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

    private suspend fun repoWith(dao: FakeEntryDao, crisis: Boolean): DiaryRepository {
        val repo = DiaryRepository(dao, idGen = { "e1" })
        repo.saveEntry("평범한 하루", analysis.copy(crisis = crisis), durationSec = 10)
        return repo
    }

    @Test fun updateTranscript_sets_crisis_when_rule_matches() = runTest {
        val dao = FakeEntryDao()
        repoWith(dao, crisis = false).updateTranscript("e1", "또 자해를 했어")
        assertTrue(dao.upserted[0].crisis)
        assertEquals("또 자해를 했어", dao.upserted[0].transcript)
    }

    @Test fun updateTranscript_never_clears_existing_crisis() = runTest {
        val dao = FakeEntryDao()
        repoWith(dao, crisis = true).updateTranscript("e1", "오늘은 괜찮았어")
        assertTrue(dao.upserted[0].crisis)
    }

    @Test fun updateTranscript_keeps_false_for_ordinary_text() = runTest {
        val dao = FakeEntryDao()
        repoWith(dao, crisis = false).updateTranscript("e1", "오늘은 괜찮았어")
        assertFalse(dao.upserted[0].crisis)
    }

    @Test fun updateTranscript_ignores_unknown_id() = runTest {
        val dao = FakeEntryDao()
        DiaryRepository(dao).updateTranscript("nope", "자살")
        assertTrue(dao.upserted.isEmpty())
    }

    @Test fun search_blank_query_skips_dao() = runTest {
        val dao = FakeEntryDao()
        assertTrue(DiaryRepository(dao).search("   ").first().isEmpty())
        assertTrue(dao.searched.isEmpty())
    }

    @Test fun search_passes_escaped_like_pattern() = runTest {
        val dao = FakeEntryDao()
        DiaryRepository(dao).search(" 100% ")
        assertEquals(listOf("%100\\%%"), dao.searched)
    }
}
