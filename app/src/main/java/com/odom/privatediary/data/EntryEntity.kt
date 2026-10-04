package com.odom.privatediary.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "entries")
data class EntryEntity(
    @PrimaryKey val id: String,
    val date: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "duration_sec") val durationSec: Int,
    @ColumnInfo(name = "audio_path") val audioPath: String?,
    val transcript: String,
    val summary: String?,
    val emotion: String?,
    val intensity: Int?,
    val causes: List<String>,
    val keywords: List<String>,
    val reply: String?,
    val crisis: Boolean,
    @ColumnInfo(name = "analysis_status") val analysisStatus: String,
)
