package com.odom.privatediary.report

import android.content.Context
import com.odom.privatediary.bench.LiteRtTextGenerator
import com.odom.privatediary.bench.ModelPaths
import com.odom.privatediary.data.DiaryDatabase
import com.odom.privatediary.pipeline.ModelGate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

sealed interface ReportJob {
    data object Idle : ReportJob
    /** 녹음 분석 등 다른 모델 작업이 끝나길 기다리는 중. */
    data class Waiting(val week: LocalDate) : ReportJob
    data class Running(val week: LocalDate) : ReportJob
    data class NoEntries(val week: LocalDate) : ReportJob
    data class Failed(val week: LocalDate, val message: String) : ReportJob
}

/** 회고 생성을 앱 범위 스코프에서 돌려 화면 회전·이탈에도 계속되게 한다(ViewModel 스코프 밖). */
class WeeklyReportService private constructor(context: Context) {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val db = DiaryDatabase.get(app)
    private val generator = WeeklyReportGenerator(
        entries = { start, end -> db.entryDao().between(start, end).first() },
        save = { db.weeklyReportDao().upsert(it) },
        generatorFactory = {
            val paths = ModelPaths.from(app)
            check(paths.gemmaReady()) { "AI 모델이 아직 준비되지 않았어요" }
            LiteRtTextGenerator(paths.gemmaModel.absolutePath, app.cacheDir.absolutePath)
        },
        gate = ModelGate.shared,
    )

    private val _state = MutableStateFlow<ReportJob>(ReportJob.Idle)
    val state: StateFlow<ReportJob> = _state.asStateFlow()
    private var job: Job? = null

    /** 이미 생성 중이면 무시한다. */
    @Synchronized
    fun generate(week: LocalDate) {
        if (job?.isActive == true) return
        _state.value = ReportJob.Running(week)
        job = scope.launch {
            _state.value = try {
                when (
                    generator.generate(
                        week,
                        onWait = { _state.value = ReportJob.Waiting(week) },
                        onRunning = { _state.value = ReportJob.Running(week) },
                    )
                ) {
                    WeeklyReportGenerator.Outcome.NoEntries -> ReportJob.NoEntries(week)
                    is WeeklyReportGenerator.Outcome.Saved -> ReportJob.Idle
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ReportJob.Failed(week, e.message ?: "회고를 만들지 못했어요")
            }
        }
    }

    companion object {
        @Volatile private var instance: WeeklyReportService? = null

        fun get(context: Context): WeeklyReportService = instance ?: synchronized(this) {
            instance ?: WeeklyReportService(context).also { instance = it }
        }
    }
}
