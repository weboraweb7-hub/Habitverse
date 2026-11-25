package com.example

import com.example.ui.home.TimeRailMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

class TimeRailMathTest {

    @Test
    fun `scaleAndClampFlingVelocity scales down and clamps extreme flings`() {
        assertEquals(0f, TimeRailMath.scaleAndClampFlingVelocity(0f), 0.001f)
        assertEquals(450f, TimeRailMath.scaleAndClampFlingVelocity(1000f), 0.001f)
        assertEquals(-450f, TimeRailMath.scaleAndClampFlingVelocity(-1000f), 0.001f)
        assertEquals(6000f, TimeRailMath.scaleAndClampFlingVelocity(50_000f), 0.001f)
        assertEquals(-6000f, TimeRailMath.scaleAndClampFlingVelocity(-50_000f), 0.001f)
        assertEquals(0f, TimeRailMath.scaleAndClampFlingVelocity(Float.NaN), 0.001f)
    }

    @Test
    fun `dateToRailIndex and railIndexToDate round-trip and clamp within range`() {
        val today = LocalDate.of(2026, 11, 24)
        val minDate = TimeRailMath.minRailDate(today)
        val total = TimeRailMath.totalRailDaysCount()

        val todayIdx = TimeRailMath.dateToRailIndex(today, minDate, total)
        assertEquals(TimeRailMath.MAX_HISTORY_DAYS.toInt(), todayIdx)
        assertEquals(today, TimeRailMath.railIndexToDate(todayIdx, minDate, total))

        val futureMax = TimeRailMath.maxRailDate(today)
        val maxIdx = TimeRailMath.dateToRailIndex(futureMax, minDate, total)
        assertEquals(total - 1, maxIdx)
        assertEquals(futureMax, TimeRailMath.railIndexToDate(maxIdx, minDate, total))

        // Out-of-bounds clamp
        assertEquals(0, TimeRailMath.dateToRailIndex(minDate.minusDays(50), minDate, total))
        assertEquals(total - 1, TimeRailMath.dateToRailIndex(futureMax.plusDays(50), minDate, total))
        assertEquals(minDate, TimeRailMath.railIndexToDate(-10, minDate, total))
        assertEquals(futureMax, TimeRailMath.railIndexToDate(total + 99, minDate, total))
    }

    @Test
    fun `shouldShowTodayPill returns true only when more than 3 days away from today`() {
        val today = LocalDate.of(2026, 11, 24)
        assertFalse(TimeRailMath.shouldShowTodayPill(today, today))
        assertFalse(TimeRailMath.shouldShowTodayPill(today.minusDays(3), today))
        assertFalse(TimeRailMath.shouldShowTodayPill(today.plusDays(3), today))
        assertTrue(TimeRailMath.shouldShowTodayPill(today.minusDays(4), today))
        assertTrue(TimeRailMath.shouldShowTodayPill(today.plusDays(4), today))
    }

    @Test
    fun `monthCalendarCells builds full 7-column weeks for month`() {
        val ym = YearMonth.of(2026, 11)
        val cells = TimeRailMath.monthCalendarCells(ym)
        assertEquals(0, cells.size % 7)
        assertEquals(30, cells.count { it != null })
        assertEquals(LocalDate.of(2026, 11, 1), cells.first { it != null })
        assertEquals(LocalDate.of(2026, 11, 30), cells.last { it != null })
    }
}
