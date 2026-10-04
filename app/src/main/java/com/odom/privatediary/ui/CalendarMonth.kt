package com.odom.privatediary.ui

import java.time.YearMonth

data class CalendarCell(val day: Int?)

object CalendarMonth {
    /** 일요일 시작 그리드. 앞 공백(day=null) + 1..말일. */
    fun cells(year: Int, month: Int): List<CalendarCell> {
        val ym = YearMonth.of(year, month)
        val lead = ym.atDay(1).dayOfWeek.value % 7 // SUN=7%7=0, MON=1 .. SAT=6
        val blanks = List(lead) { CalendarCell(null) }
        val days = (1..ym.lengthOfMonth()).map { CalendarCell(it) }
        return blanks + days
    }
}
