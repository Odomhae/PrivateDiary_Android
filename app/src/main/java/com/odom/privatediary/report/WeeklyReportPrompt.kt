package com.odom.privatediary.report

import com.odom.privatediary.data.EntryEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter

object WeeklyReportPrompt {
    val SYSTEM_INSTRUCTION = """
        당신은 사용자의 한 주 음성 일기를 되짚어 주는 보조자입니다.
        날짜별 요약을 읽고 한국어 한 문단(3~5문장)으로 이번 주를 돌아보는 글을 쓰세요.
        조언, 진단, 상담, 권유는 하지 않습니다. 사용자가 말한 내용과 감정의 흐름만 담담히 되짚습니다.
        마크다운, 목록, 제목, 따옴표 없이 문단 하나만 출력합니다.
    """.trimIndent()

    private val MD = DateTimeFormatter.ofPattern("MM/dd")

    /** 분석 완료 엔트리를 시간순 한 줄씩. 위기 엔트리는 요약 없이 "힘든 날이 있었음"만. 쓸 줄이 없으면 null. */
    fun build(entries: List<EntryEntity>): String? {
        val lines = entries.sortedBy { it.createdAt }.mapNotNull { e ->
            val date = LocalDate.parse(e.date)
            val head = "${Week.dayLabel(date)} ${date.format(MD)}"
            when {
                e.crisis -> "$head · 힘든 날이 있었음"
                e.analysisStatus != "done" -> null
                else -> listOfNotNull(
                    head,
                    e.emotion?.let { em -> e.intensity?.let { "$em($it)" } ?: em },
                    e.summary,
                ).joinToString(" · ")
            }
        }
        if (lines.isEmpty()) return null
        return buildString {
            appendLine("이번 주 일기 요약:")
            lines.forEach { appendLine(it) }
            appendLine()
            append("위 내용을 바탕으로 이번 주 회고 문단을 써 주세요.")
        }
    }
}
