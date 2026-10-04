package com.odom.privatediary.data

import com.odom.privatediary.bench.AnalysisResult

object EntryMapper {
    /** 전사문 + 분석 결과를 Entry 엔티티로 매핑한다. id/createdAt/date는 호출자가 결정(테스트 가능). */
    fun toEntry(
        id: String,
        date: String,
        createdAt: Long,
        durationSec: Int,
        transcript: String,
        analysis: AnalysisResult,
        audioPath: String? = null,
    ): EntryEntity = EntryEntity(
        id = id,
        date = date,
        createdAt = createdAt,
        durationSec = durationSec,
        audioPath = audioPath,
        transcript = transcript,
        summary = analysis.summary.ifBlank { null },
        emotion = analysis.emotion,
        intensity = if (analysis.parseOk) analysis.intensity else null,
        causes = analysis.causes,
        keywords = analysis.keywords,
        reply = analysis.reply.ifBlank { null },
        crisis = analysis.crisis,
        analysisStatus = if (analysis.parseOk) "done" else "failed",
    )
}
