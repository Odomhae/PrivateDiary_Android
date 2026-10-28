package com.odom.privatediary.report

import com.odom.privatediary.bench.TextGenerator
import com.odom.privatediary.data.EntryEntity
import com.odom.privatediary.data.WeeklyReportEntity
import com.odom.privatediary.pipeline.ModelGate
import java.time.LocalDate

/** 한 주의 일기로 회고 문단을 만들어 저장한다. 의존성은 람다로 주입해 JVM 테스트한다. */
class WeeklyReportGenerator(
    private val entries: suspend (start: String, end: String) -> List<EntryEntity>,
    private val save: suspend (WeeklyReportEntity) -> Unit,
    private val generatorFactory: () -> TextGenerator,
    private val gate: ModelGate,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    sealed interface Outcome {
        data object NoEntries : Outcome
        data class Saved(val report: WeeklyReportEntity) : Outcome
    }

    /** 생성 실패·빈 본문은 예외. 모델은 [gate] 안에서만 올리고 항상 해제한다. */
    suspend fun generate(
        weekStart: LocalDate,
        onWait: () -> Unit = {},
        onRunning: () -> Unit = {},
    ): Outcome {
        val start = weekStart.toString()
        // 엔트리는 게이트 안에서 읽는다 — 대기하는 동안 저장된 방금 녹음한 일기도 포함되도록.
        val raw = gate.withModel(onWait) {
            val prompt = WeeklyReportPrompt.build(entries(start, Week.end(weekStart).toString()))
                ?: return@withModel null
            onRunning()
            val generator = generatorFactory()
            try {
                generator.load()
                generator.generate(WeeklyReportPrompt.SYSTEM_INSTRUCTION, prompt)
            } finally {
                generator.release()
            }
        } ?: return Outcome.NoEntries
        val body = ReportBody.clean(raw) ?: error("빈 회고가 생성됐습니다")
        val report = WeeklyReportEntity(id = "week-$start", weekStart = start, body = body, createdAt = now())
        save(report)
        return Outcome.Saved(report)
    }
}
