package com.odom.privatediary.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WeeklyReportDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(report: WeeklyReportEntity)

    @Query("SELECT * FROM weekly_reports ORDER BY created_at DESC")
    fun observeAll(): Flow<List<WeeklyReportEntity>>

    @Query("SELECT * FROM weekly_reports WHERE week_start = :weekStart ORDER BY created_at DESC LIMIT 1")
    fun byWeek(weekStart: String): Flow<WeeklyReportEntity?>
}
