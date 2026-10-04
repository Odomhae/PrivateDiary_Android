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

    @Query("UPDATE entries SET transcript = :transcript WHERE id = :id")
    suspend fun updateTranscript(id: String, transcript: String)

    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun delete(id: String)
}
