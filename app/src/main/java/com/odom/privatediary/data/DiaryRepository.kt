package com.odom.privatediary.data

import com.odom.privatediary.bench.AnalysisResult
import com.odom.privatediary.safety.CrisisDetector
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/** Entry 영속화/조회를 담당. EntryDao 인터페이스에만 의존해 fake로 테스트 가능하다.
 *  now/idGen/zone은 주입 가능해 날짜·id 생성을 결정적으로 테스트한다. */
class DiaryRepository(
    private val entryDao: EntryDao,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val idGen: () -> String = { UUID.randomUUID().toString() },
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(zone)

    suspend fun saveEntry(
        transcript: String,
        analysis: AnalysisResult,
        durationSec: Int,
        audioPath: String? = null,
    ): String {
        val createdAt = now()
        val id = idGen()
        val date = dateFormat.format(Instant.ofEpochMilli(createdAt))
        entryDao.upsert(EntryMapper.toEntry(id, date, createdAt, durationSec, transcript, analysis, audioPath))
        return id
    }

    fun observeEntries(): Flow<List<EntryEntity>> = entryDao.observeAll()
    suspend fun entriesOn(date: String): List<EntryEntity> = entryDao.byDate(date)
    suspend fun entry(id: String): EntryEntity? = entryDao.byId(id)

    /** 수정한 전사문도 위기 표현을 재검사한다. 감지는 켜기만 하고 해제하지 않는다(DAO가 OR). */
    suspend fun updateTranscript(id: String, transcript: String) =
        entryDao.updateTranscript(id, transcript, CrisisDetector.detect(transcript))

    /** DB 변경(삭제·수정)에 반응하는 검색 결과. 빈 검색어는 빈 리스트. */
    fun search(query: String): Flow<List<EntryEntity>> =
        SearchQuery.toLikePattern(query)?.let { entryDao.search(it) } ?: flowOf(emptyList())

    suspend fun deleteEntry(id: String) = entryDao.delete(id)
}
