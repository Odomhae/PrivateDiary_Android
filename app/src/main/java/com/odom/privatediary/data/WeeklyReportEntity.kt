package com.odom.privatediary.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "weekly_reports")
data class WeeklyReportEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "week_start") val weekStart: String,
    val body: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
