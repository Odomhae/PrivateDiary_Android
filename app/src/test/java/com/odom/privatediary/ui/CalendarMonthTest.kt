package com.odom.privatediary.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalendarMonthTest {
    @Test fun february_2026_starts_on_sunday_no_leading_blanks() {
        val cells = CalendarMonth.cells(2026, 2) // 2026-02-01 is Sunday, 28 days
        assertEquals(28, cells.size)
        assertEquals(1, cells[0].day)
        assertEquals(28, cells[27].day)
    }

    @Test fun january_2026_has_four_leading_blanks() {
        val cells = CalendarMonth.cells(2026, 1) // 2026-01-01 is Thursday (offset 4), 31 days
        assertEquals(35, cells.size)
        (0..3).forEach { assertNull(cells[it].day) }
        assertEquals(1, cells[4].day)
        assertEquals(31, cells[34].day)
    }
}
