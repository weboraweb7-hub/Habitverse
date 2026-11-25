package com.example

import com.example.domain.model.DayNote
import com.example.domain.model.Habit
import com.example.domain.model.HabitLog
import com.example.domain.model.RepeatDays
import com.example.domain.usecase.HabitCalculator
import com.example.work.StreakAtRiskNudgeEvaluator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ProgressAndNudgeTest {

    private val today = LocalDate.of(2026, 6, 15)

    private fun sampleHabit(createdDaysAgo: Long = 30) = Habit(
        id = "h1",
        name = "Read 10 pages",
        icon = "📖",
        colorHex = 0xFF0D9488,
        repeatDays = RepeatDays.ALL,
        createdAt = today.minusDays(createdDaysAgo).toString()
    )

    @Test
    fun `mood insight returns null when fewer than 14 mood logs exist`() {
        val habit = sampleHabit()
        val notes = (1..13).map { i ->
            DayNote(date = today.minusDays(i.toLong()).toString(), note = "", mood = 5)
        }
        val logs = (1..13).map { i ->
            HabitLog(habitId = "h1", date = today.minusDays(i.toLong()).toString(), done = true)
        }
        val insight = HabitCalculator.computeMoodInsight(
            habits = listOf(habit),
            logsByKey = HabitCalculator.indexLogs(logs),
            notes = notes
        )
        assertNull(insight)
    }

    @Test
    fun `mood insight returns positive lift string when 14+ mood logs exist`() {
        val habit1 = sampleHabit()
        val habit2 = sampleHabit().copy(id = "h2", name = "Meditate")
        val notes = mutableListOf<DayNote>()
        val logs = mutableListOf<HabitLog>()

        // 7 high mood days: both done (100%)
        for (i in 1..7) {
            val d = today.minusDays(i.toLong()).toString()
            notes.add(DayNote(date = d, note = "", mood = 5))
            logs.add(HabitLog(habitId = "h1", date = d, done = true))
            logs.add(HabitLog(habitId = "h2", date = d, done = true))
        }
        // 7 low mood days: only 1 of 2 done (50%)
        for (i in 8..14) {
            val d = today.minusDays(i.toLong()).toString()
            notes.add(DayNote(date = d, note = "", mood = 2))
            logs.add(HabitLog(habitId = "h1", date = d, done = true))
        }

        val insight = HabitCalculator.computeMoodInsight(
            habits = listOf(habit1, habit2),
            logsByKey = HabitCalculator.indexLogs(logs),
            notes = notes
        )
        assertNotNull(insight)
        assertTrue(insight!!.contains("100% more habits"))
    }

    @Test
    fun `consistency sky generates 90 stars and links consecutive streak days`() {
        val habit = sampleHabit(createdDaysAgo = 90)
        val logs = listOf(
            HabitLog(habitId = "h1", date = today.minusDays(2).toString(), done = true),
            HabitLog(habitId = "h1", date = today.minusDays(1).toString(), done = true)
        )
        val stars = HabitCalculator.buildConsistencySky(
            habits = listOf(habit),
            logs = logs,
            today = today,
            daysCount = 90
        )
        assertEquals(90, stars.size)
        val dayMinus2 = stars.first { it.date == today.minusDays(2) }
        val dayMinus1 = stars.first { it.date == today.minusDays(1) }
        val dayToday = stars.first { it.date == today }

        assertTrue(dayMinus2.isStreakDay)
        assertFalse(dayMinus2.connectedToPrev)
        assertTrue(dayMinus1.isStreakDay)
        assertTrue(dayMinus1.connectedToPrev)
        assertFalse(dayToday.isStreakDay)
        assertFalse(dayToday.connectedToPrev)
    }

    @Test
    fun `streak at risk nudge respects quiet hours, streak threshold, and completion state`() {
        // Eligible at 20:00 with streak=5, incomplete today
        assertTrue(
            StreakAtRiskNudgeEvaluator.shouldSendNudge(
                hour = 20,
                currentStreak = 5,
                scheduledToday = 3,
                completedToday = 1,
                isSkippedToday = false,
                isFrozenToday = false,
                lastNudgeDate = "",
                today = today
            )
        )
        // Quiet hours 22:00..06:59
        assertFalse(
            StreakAtRiskNudgeEvaluator.shouldSendNudge(22, 5, 3, 1, false, false, "", today)
        )
        assertFalse(
            StreakAtRiskNudgeEvaluator.shouldSendNudge(6, 5, 3, 1, false, false, "", today)
        )
        // Streak < 3
        assertFalse(
            StreakAtRiskNudgeEvaluator.shouldSendNudge(20, 2, 3, 1, false, false, "", today)
        )
        // All done today
        assertFalse(
            StreakAtRiskNudgeEvaluator.shouldSendNudge(20, 5, 3, 3, false, false, "", today)
        )
        // Skipped or frozen today
        assertFalse(
            StreakAtRiskNudgeEvaluator.shouldSendNudge(20, 5, 3, 1, true, false, "", today)
        )
        assertFalse(
            StreakAtRiskNudgeEvaluator.shouldSendNudge(20, 5, 3, 1, false, true, "", today)
        )
        // Already sent today
        assertFalse(
            StreakAtRiskNudgeEvaluator.shouldSendNudge(20, 5, 3, 1, false, false, today.toString(), today)
        )
    }

    @Test
    fun `streak nudge rotates 6 distinct encouraging copy variants`() {
        val habit = sampleHabit()
        val variants = (0L..5L).map { offset ->
            StreakAtRiskNudgeEvaluator.buildCopy(
                currentStreak = 7,
                remainingCount = 2,
                nextHabit = habit,
                today = today.plusDays(offset)
            )
        }.toSet()
        assertEquals(6, variants.size)
    }
}
