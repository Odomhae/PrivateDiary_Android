package com.odom.privatediary.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: EntryEntity)

    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun byId(id: String): EntryEntity?

    @Query("SELECT * FROM entries WHERE date = :date ORDER BY created_at DESC")
    suspend fun byDate(date: String): List<EntryEntity>

    @Query("SELECT * FROM entries ORDER BY created_at DESC")
    fun observeAll(): Flow<List<EntryEntity>>

    /** crisis는 SQL에서 OR — 기존 감지를 해제하지 않고, 읽기-쓰기 경합도 없다. */
    @Query("UPDATE entries SET transcript = :transcript, crisis = (crisis OR :crisis) WHERE id = :id")
    suspend fun updateTranscript(id: String, transcript: String, crisis: Boolean)

    /** [pattern]은 `SearchQuery.toLikePattern`으로 만든 `\` 이스케이프 LIKE 패턴. */
    @Query(
        "SELECT * FROM entries WHERE transcript LIKE :pattern ESCAPE '\\' " +
            "OR summary LIKE :pattern ESCAPE '\\' ORDER BY created_at DESC",
    )
    fun search(pattern: String): Flow<List<EntryEntity>>

    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun delete(id: String)
}
