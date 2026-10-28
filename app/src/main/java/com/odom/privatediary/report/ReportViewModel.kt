package com.odom.privatediary.report

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.odom.privatediary.data.DiaryDatabase
import com.odom.privatediary.data.EntryEntity
import com.odom.privatediary.data.WeeklyReportEntity
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class ReportViewModel(app: Application) : AndroidViewModel(app) {
    private val db = DiaryDatabase.get(app)
    private val service = WeeklyReportService.get(app)

    val thisWeek: LocalDate = Week.start(LocalDate.now())
    private val _week = MutableStateFlow(thisWeek)
    val week: StateFlow<LocalDate> = _week.asStateFlow()

    val entries: StateFlow<List<EntryEntity>> = _week
        .flatMapLatest { db.entryDao().between(it.toString(), Week.end(it).toString()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val report: StateFlow<WeeklyReportEntity?> = _week
        .flatMapLatest { db.weeklyReportDao().byWeek(it.toString()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 생성은 앱 범위 서비스가 한다 — 화면을 나가도 계속된다. */
    val job: StateFlow<ReportJob> = service.state

    fun previousWeek() {
        _week.value = _week.value.minusWeeks(1)
    }

    /** 이번 주보다 미래로는 가지 않는다. */
    fun nextWeek() {
        if (_week.value < thisWeek) _week.value = _week.value.plusWeeks(1)
    }

    fun generate() = service.generate(_week.value)
}
