package com.example

import com.example.domain.model.DayCompletionStatus
import com.example.domain.model.DayNote
import com.example.domain.model.Habit
import com.example.domain.model.HabitCategory
import com.example.domain.model.HabitLog
import com.example.domain.model.HabitType
import com.example.domain.model.RepeatDays
import com.example.domain.model.TimeOfDay
import com.example.domain.usecase.HabitCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class HabitCalculatorTest {

    private fun habit(
        id: String = "h1",
        name: String = "Read",
        repeatDays: Int = RepeatDays.ALL,
        createdAt: LocalDate = LocalDate.of(2026, 11, 1),
        archivedAt: LocalDate? = null,
        timeOfDay: TimeOfDay = TimeOfDay.ANYTIME,
        type: HabitType = HabitType.YES_NO,
        targetValue: Int = 1,
        sortOrder: Int = 0
    ) = Habit(
        id = id,
        name = name,
        icon = "📖",
        colorHex = 0xFF0D9488,
        repeatDays = repeatDays,
        createdAt = createdAt.toString(),
        archivedAt = archivedAt?.toString(),
        sortOrder = sortOrder,
        type = type,
        targetValue = targetValue,
        timeOfDay = timeOfDay,
        category = HabitCategory.GENERAL
    )

    @Test
    fun `repeat days bitmask schedules only selected weekdays`() {
        val monWedFri = RepeatDays.fromDays(
            setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)
        )
        assertTrue(RepeatDays.isScheduledOn(monWedFri, DayOfWeek.MONDAY))
        assertFalse(RepeatDays.isScheduledOn(monWedFri, DayOfWeek.TUESDAY))
        assertTrue(RepeatDays.isScheduledOn(monWedFri, DayOfWeek.WEDNESDAY))
        assertFalse(RepeatDays.isScheduledOn(monWedFri, DayOfWeek.THURSDAY))
        assertTrue(RepeatDays.isScheduledOn(monWedFri, DayOfWeek.FRIDAY))
        assertFalse(RepeatDays.isScheduledOn(monWedFri, DayOfWeek.SATURDAY))
        assertFalse(RepeatDays.isScheduledOn(monWedFri, DayOfWeek.SUNDAY))

        assertEquals("Daily", RepeatDays.formatSummary(RepeatDays.ALL))
        assertEquals("Weekdays", RepeatDays.formatSummary(RepeatDays.WEEKDAYS))
        assertEquals("Weekends", RepeatDays.formatSummary(RepeatDays.WEEKENDS))
    }

    @Test
    fun `toggleDay never leaves repeatDays empty`() {
        val onlyMonday = RepeatDays.fromDays(setOf(DayOfWeek.MONDAY))
        val afterToggle = RepeatDays.toggleDay(onlyMonday, DayOfWeek.MONDAY)
        assertEquals(onlyMonday, afterToggle)
    }

    @Test
    fun `habit respects createdAt and archivedAt boundaries`() {
        val created = LocalDate.of(2026, 11, 10)
        val archived = LocalDate.of(2026, 11, 15)
        val h = habit(createdAt = created, archivedAt = archived)

        assertFalse(HabitCalculator.isHabitActiveOn(h, LocalDate.of(2026, 11, 9)))
        assertTrue(HabitCalculator.isHabitActiveOn(h, LocalDate.of(2026, 11, 10)))
        assertTrue(HabitCalculator.isHabitActiveOn(h, LocalDate.of(2026, 11, 14)))
        assertFalse(HabitCalculator.isHabitActiveOn(h, LocalDate.of(2026, 11, 15)))
        assertFalse(HabitCalculator.isHabitActiveOn(h, LocalDate.of(2026, 11, 16)))
    }

    @Test
    fun `current streak counts consecutive completed days and skips rest days`() {
        val today = LocalDate.of(2026, 11, 18)
        val weekdayHabit = habit(
            id = "w1",
            repeatDays = RepeatDays.WEEKDAYS,
            createdAt = LocalDate.of(2026, 11, 9)
        )

        val logs = listOf(
            HabitLog("w1", "2026-11-13", true),
            HabitLog("w1", "2026-11-16", true),
            HabitLog("w1", "2026-11-17", true)
        )

        assertEquals(3, HabitCalculator.calculateCurrentStreak(listOf(weekdayHabit), logs, today))

        val withToday = logs + HabitLog("w1", "2026-11-18", true)
        assertEquals(4, HabitCalculator.calculateCurrentStreak(listOf(weekdayHabit), withToday, today))
    }

    @Test
    fun `skip days and streak freezes protect streak without breaking`() {
        val today = LocalDate.of(2026, 11, 18)
        val dailyHabit = habit(id = "d1", createdAt = LocalDate.of(2026, 11, 10))
        // Completed Nov 14, Nov 15; missed Nov 16 (skipped) and Nov 17 (frozen); completed Nov 18
        val logs = listOf(
            HabitLog("d1", "2026-11-14", true),
            HabitLog("d1", "2026-11-15", true),
            HabitLog("d1", "2026-11-18", true)
        )
        val skips = setOf(LocalDate.of(2026, 11, 16))
        val freezes = setOf(LocalDate.of(2026, 11, 17))

        // Without skips/freezes streak is 1 (only today)
        assertEquals(1, HabitCalculator.calculateCurrentStreak(listOf(dailyHabit), logs, today))

        // With skip on Nov 16 + freeze on Nov 17, streak stays 3 (Nov 14, Nov 15, Nov 18)
        assertEquals(
            3,
            HabitCalculator.calculateCurrentStreak(
                habits = listOf(dailyHabit),
                logs = logs,
                today = today,
                skippedDates = skips,
                frozenDates = freezes
            )
        )
        assertEquals(
            3,
            HabitCalculator.calculateBestStreak(
                habits = listOf(dailyHabit),
                logs = logs,
                today = today,
                skippedDates = skips,
                frozenDates = freezes
            )
        )
    }

    @Test
    fun `findProtectableMissedDate locates most recent unprotected missed day within 3 days`() {
        val today = LocalDate.of(2026, 11, 18)
        val dailyHabit = habit(id = "d1", createdAt = LocalDate.of(2026, 11, 1))
        val logs = listOf(
            HabitLog("d1", "2026-11-15", true),
            HabitLog("d1", "2026-11-17", true)
        )

        // Nov 16 was missed -> returns Nov 16
        val missed = HabitCalculator.findProtectableMissedDate(
            habits = listOf(dailyHabit),
            logs = logs,
            today = today,
            skippedDates = emptySet(),
            frozenDates = emptySet()
        )
        assertEquals(LocalDate.of(2026, 11, 16), missed)

        // Once Nov 16 is frozen, no protectable missed date remains
        val afterFreeze = HabitCalculator.findProtectableMissedDate(
            habits = listOf(dailyHabit),
            logs = logs,
            today = today,
            skippedDates = emptySet(),
            frozenDates = setOf(LocalDate.of(2026, 11, 16))
        )
        assertNull(afterFreeze)
    }

    @Test
    fun `scheduledHabitsForDate sorts by time of day then sortOrder`() {
        val date = LocalDate.of(2026, 11, 18)
        val evening = habit(id = "e", timeOfDay = TimeOfDay.EVENING, sortOrder = 0)
        val morning = habit(id = "m", timeOfDay = TimeOfDay.MORNING, sortOrder = 5)
        val afternoon = habit(id = "a", timeOfDay = TimeOfDay.AFTERNOON, sortOrder = 2)

        val sorted = HabitCalculator.scheduledHabitsForDate(listOf(evening, afternoon, morning), date)
        assertEquals(listOf("m", "a", "e"), sorted.map { it.id })
    }

    @Test
    fun `completion rate and day status including SKIPPED and FROZEN calculate accurately`() {
        val today = LocalDate.of(2026, 11, 18)
        val h1 = habit(id = "h1", createdAt = LocalDate.of(2026, 11, 15))
        val h2 = habit(id = "h2", createdAt = LocalDate.of(2026, 11, 15))
        val habits = listOf(h1, h2)

        val logs = listOf(
            HabitLog("h1", "2026-11-16", true),
            HabitLog("h2", "2026-11-16", true),
            HabitLog("h1", "2026-11-17", true)
        )
        val logsByKey = HabitCalculator.indexLogs(logs)
        val skips = setOf(LocalDate.of(2026, 11, 15))
        val freezes = setOf(LocalDate.of(2026, 11, 18))

        assertEquals(
            DayCompletionStatus.SKIPPED,
            HabitCalculator.dayCompletion(habits, logsByKey, LocalDate.of(2026, 11, 15), skips, freezes).status(today)
        )
        assertEquals(
            DayCompletionStatus.FULL,
            HabitCalculator.dayCompletion(habits, logsByKey, LocalDate.of(2026, 11, 16), skips, freezes).status(today)
        )
        assertEquals(
            DayCompletionStatus.PARTIAL,
            HabitCalculator.dayCompletion(habits, logsByKey, LocalDate.of(2026, 11, 17), skips, freezes).status(today)
        )
        assertEquals(
            DayCompletionStatus.FROZEN,
            HabitCalculator.dayCompletion(habits, logsByKey, LocalDate.of(2026, 11, 18), skips, freezes).status(today)
        )
        assertEquals(
            DayCompletionStatus.FUTURE,
            HabitCalculator.dayCompletion(habits, logsByKey, LocalDate.of(2026, 11, 19), skips, freezes).status(today)
        )

        // Skipped Nov 15 excluded from completion denominator -> Nov 16(2) + Nov 17(1) + Nov 18(0) out of 6 = 50%
        val rate = HabitCalculator.calculateCompletionRate(
            habits = habits,
            logs = logs,
            startDate = LocalDate.of(2026, 11, 15),
            endDate = today,
            skippedDates = skips
        )
        assertEquals(0.5f, rate, 0.001f)
    }

    @Test
    fun `computeLocalInsights identifies best weekday, most missed habit, and weekly summary`() {
        val today = LocalDate.of(2026, 11, 18) // Wednesday
        val h1 = habit(id = "h1", name = "Hydrate", createdAt = LocalDate.of(2026, 11, 11))
        val h2 = habit(id = "h2", name = "Meditate", createdAt = LocalDate.of(2026, 11, 11))

        val logs = listOf(
            // Hydrate done on Wed Nov 11, Thu Nov 12, Wed Nov 18
            HabitLog("h1", "2026-11-11", true),
            HabitLog("h1", "2026-11-12", true),
            HabitLog("h1", "2026-11-18", true),
            // Meditate done only on Wed Nov 18 -> lower completion rate than Hydrate
            HabitLog("h2", "2026-11-18", true)
        )

        val insights = HabitCalculator.computeLocalInsights(
            habits = listOf(h1, h2),
            logs = logs,
            today = today
        )!!

        assertEquals("Wednesdays", insights.bestWeekdayLabel)
        assertEquals(75, insights.bestWeekdayPercent) // Wed 11: 1/2, Wed 18: 2/2 -> 3/4 = 75%
        assertEquals("Meditate", insights.mostMissedHabitName)
        assertEquals(3, insights.weeklyCompleted)
    }

    @Test
    fun `daily sheet pairs completion rows with notes`() {
        val today = LocalDate.of(2026, 11, 18)
        val h = habit(id = "h1", createdAt = LocalDate.of(2026, 11, 17))
        val logs = listOf(HabitLog("h1", "2026-11-18", true))
        val notes = listOf(DayNote("2026-11-18", "Felt great!"))

        val rows = HabitCalculator.buildDailySheet(
            habits = listOf(h),
            logs = logs,
            notes = notes,
            startDate = LocalDate.of(2026, 11, 17),
            endDate = today
        )

        assertEquals(2, rows.size)
        assertEquals(today, rows[0].date)
        assertEquals(1, rows[0].doneCount)
        assertEquals(1, rows[0].totalCount)
        assertEquals(100, rows[0].percent)
        assertEquals("Felt great!", rows[0].note)

        assertEquals(LocalDate.of(2026, 11, 17), rows[1].date)
        assertEquals(0, rows[1].doneCount)
        assertEquals(1, rows[1].totalCount)
        assertEquals(0, rows[1].percent)
    }
}
