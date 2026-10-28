package com.odom.privatediary.report

import com.odom.privatediary.data.EntryEntity

/** 테스트용 엔트리. 기본은 분석 완료(done)·위기 아님. */
fun entry(
    date: String,
    createdAt: Long = 0,
    emotion: String? = "평온",
    intensity: Int? = 2,
    summary: String? = "요약",
    crisis: Boolean = false,
    status: String = "done",
    id: String = "$date-$createdAt",
) = EntryEntity(
    id = id, date = date, createdAt = createdAt, durationSec = 10, audioPath = null,
    transcript = "전사", summary = summary, emotion = emotion, intensity = intensity,
    causes = emptyList(), keywords = emptyList(), reply = null, crisis = crisis, analysisStatus = status,
)
