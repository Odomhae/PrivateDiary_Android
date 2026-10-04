package com.odom.privatediary.ui

import com.odom.privatediary.data.EntryEntity

object EntryGrouping {
    fun byDate(entries: List<EntryEntity>): Map<String, List<EntryEntity>> = entries.groupBy { it.date }

    /** 그날 대표 감정 = 가장 최근(createdAt) 엔트리의 emotion. */
    fun dominantEmotion(entries: List<EntryEntity>): String? =
        entries.maxByOrNull { it.createdAt }?.emotion
}
