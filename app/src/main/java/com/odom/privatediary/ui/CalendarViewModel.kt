package com.odom.privatediary.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.odom.privatediary.data.DiaryDatabase
import com.odom.privatediary.data.DiaryRepository
import com.odom.privatediary.data.EntryEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth

class CalendarViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = DiaryRepository(DiaryDatabase.get(app).entryDao())

    val entries: StateFlow<List<EntryEntity>> = repo.observeEntries()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _month = MutableStateFlow(YearMonth.now())
    val month: StateFlow<YearMonth> = _month.asStateFlow()

    fun prevMonth() { _month.value = _month.value.minusMonths(1) }
    fun nextMonth() { _month.value = _month.value.plusMonths(1) }
}
