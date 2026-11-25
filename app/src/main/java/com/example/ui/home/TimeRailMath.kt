package com.example.ui.home

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.abs

object TimeRailMath {
    const val MAX_HISTORY_DAYS = 3650L
    const val MAX_FUTURE_DAYS = 14L
    const val DEFAULT_FLING_VELOCITY_SCALE = 0.45f
    const val DEFAULT_MAX_ABS_FLING_VELOCITY = 6000f
    const val TODAY_PILL_THRESHOLD_DAYS = 3L

    fun minRailDate(today: LocalDate): LocalDate = today.minusDays(MAX_HISTORY_DAYS)

    fun maxRailDate(today: LocalDate): LocalDate = today.plusDays(MAX_FUTURE_DAYS)

    fun totalRailDaysCount(): Int = (MAX_HISTORY_DAYS + MAX_FUTURE_DAYS + 1L).toInt()

    fun dateToRailIndex(
        date: LocalDate,
        minDate: LocalDate,
        totalDaysCount: Int = totalRailDaysCount()
    ): Int {
        val raw = ChronoUnit.DAYS.between(minDate, date).toInt()
        return raw.coerceIn(0, (totalDaysCount - 1).coerceAtLeast(0))
    }

    fun railIndexToDate(
        index: Int,
        minDate: LocalDate,
        totalDaysCount: Int = totalRailDaysCount()
    ): LocalDate {
        val clamped = index.coerceIn(0, (totalDaysCount - 1).coerceAtLeast(0))
        return minDate.plusDays(clamped.toLong())
    }

    fun scaleAndClampFlingVelocity(
        velocityPxPerSec: Float,
        scale: Float = DEFAULT_FLING_VELOCITY_SCALE,
        maxAbs: Float = DEFAULT_MAX_ABS_FLING_VELOCITY
    ): Float {
        if (!velocityPxPerSec.isFinite()) return 0f
        val safeMax = maxAbs.coerceAtLeast(0f)
        val scaled = velocityPxPerSec * scale
        return scaled.coerceIn(-safeMax, safeMax)
    }

    fun shouldShowTodayPill(selectedDate: LocalDate, today: LocalDate): Boolean =
        abs(ChronoUnit.DAYS.between(selectedDate, today)) > TODAY_PILL_THRESHOLD_DAYS

    fun monthCalendarCells(yearMonth: YearMonth): List<LocalDate?> {
        val firstDay = yearMonth.atDay(1)
        val leadingBlanks = firstDay.dayOfWeek.value - 1
        val totalDays = yearMonth.lengthOfMonth()
        return buildList {
            repeat(leadingBlanks) { add(null) }
            for (day in 1..totalDays) add(yearMonth.atDay(day))
            while (size % 7 != 0) add(null)
        }
    }
}
