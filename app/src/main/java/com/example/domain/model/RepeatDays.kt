package com.example.domain.model

import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

object RepeatDays {
    const val ALL: Int = 0b1111111 // 127 (Mon..Sun)
    const val WEEKDAYS: Int = 0b0011111 // 31 (Mon..Fri)
    const val WEEKENDS: Int = 0b1100000 // 96 (Sat..Sun)

    fun bitFor(dayOfWeek: DayOfWeek): Int = 1 shl (dayOfWeek.value - 1)

    fun isScheduledOn(mask: Int, dayOfWeek: DayOfWeek): Boolean =
        (mask and bitFor(dayOfWeek)) != 0

    fun fromDays(days: Set<DayOfWeek>): Int {
        var mask = 0
        for (day in days) {
            mask = mask or bitFor(day)
        }
        return mask
    }

    fun toDays(mask: Int): Set<DayOfWeek> =
        DayOfWeek.entries.filter { isScheduledOn(mask, it) }.toSet()

    fun toggleDay(mask: Int, dayOfWeek: DayOfWeek): Int {
        val bit = bitFor(dayOfWeek)
        val toggled = mask xor bit
        return if (toggled == 0) mask else toggled // Always keep at least 1 day selected
    }

    fun formatSummary(mask: Int, locale: Locale = Locale.getDefault()): String {
        return when (mask) {
            ALL -> "Daily"
            WEEKDAYS -> "Weekdays"
            WEEKENDS -> "Weekends"
            else -> {
                val active = DayOfWeek.entries.filter { isScheduledOn(mask, it) }
                if (active.isEmpty()) "None"
                else active.joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) }
            }
        }
    }
}
