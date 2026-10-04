package com.odom.privatediary.data

import com.odom.privatediary.bench.AnalysisResult
import kotlinx.coroutines.flow.Flow
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
    suspend fun deleteEntry(id: String) = entryDao.delete(id)
}
