package com.odom.privatediary.report

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/** 월~일 주 단위 계산. */
object Week {
    fun start(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    fun end(start: LocalDate): LocalDate = start.plusDays(6)
    fun days(start: LocalDate): List<LocalDate> = (0L..6L).map { start.plusDays(it) }

    /** 월=0 … 일=6 한 글자 요일. */
    fun dayLabel(date: LocalDate): String = "월화수목금토일"[date.dayOfWeek.value - 1].toString()
}
