package com.example

import com.example.domain.model.DailyReward
import com.example.domain.model.Habit
import com.example.domain.model.HabitLog
import com.example.domain.model.HabitType
import com.example.domain.model.RepeatDays
import com.example.domain.model.RewardType
import com.example.domain.usecase.XpCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class XpCalculatorTest {

    @Test
    fun `xpForLevel and levelFromXp follow quadratic curve accurately`() {
        assertEquals(0, XpCalculator.xpForLevel(1))
        assertEquals(100, XpCalculator.xpForLevel(2))
        assertEquals(300, XpCalculator.xpForLevel(3))
        assertEquals(600, XpCalculator.xpForLevel(4))

        assertEquals(1, XpCalculator.levelFromXp(0))
        assertEquals(1, XpCalculator.levelFromXp(99))
        assertEquals(2, XpCalculator.levelFromXp(100))
        assertEquals(2, XpCalculator.levelFromXp(299))
        assertEquals(3, XpCalculator.levelFromXp(300))
        assertEquals(4, XpCalculator.levelFromXp(600))
    }

    @Test
    fun `calculateTotalXp awards 10 per normal win, 5 per tiny win, perfect day bonus, chest XP, and plant bonus`() {
        val d1 = LocalDate.of(2026, 11, 20)
        val d2 = LocalDate.of(2026, 11, 21)
        val h1 = Habit(id = "h1", name = "Read", icon = "📖", colorHex = 1L, repeatDays = RepeatDays.ALL, createdAt = d1.toString())
        val h2 = Habit(id = "h2", name = "Water", icon = "💧", colorHex = 2L, repeatDays = RepeatDays.ALL, type = HabitType.COUNT, targetValue = 8, createdAt = d1.toString())

        val logs = listOf(
            // Day 1: both done (h1 normal=10, h2 target reached done=10) -> perfect day streak 1 (+21)
            HabitLog("h1", d1.toString(), done = true, progressValue = 1),
            HabitLog("h2", d1.toString(), done = true, progressValue = 8),
            // Day 2: h1 tiny win (5), h2 partial not done (0)
            HabitLog("h1", d2.toString(), done = true, progressValue = 1, tiny = true),
            HabitLog("h2", d2.toString(), done = false, progressValue = 4)
        )
        val rewards = listOf(
            DailyReward(d1.toString(), RewardType.BONUS_XP, 25, openedAtMs = 100L),
            DailyReward(d2.toString(), RewardType.STREAK_FREEZE, 1, openedAtMs = 200L)
        )

        val total = XpCalculator.calculateTotalXp(
            habits = listOf(h1, h2),
            logs = logs,
            dailyRewards = rewards,
            plantBonusXpTotal = 5,
            skippedDates = emptySet(),
            frozenDates = emptySet(),
            today = d2
        )
        // Check-ins: 10 + 10 + 5 = 25
        // Perfect day bonus day 1 (streak=1): round(20 * (1 + 1/30)) = 21
        // Chest XP: 25
        // Plant bonus: 5
        assertEquals(25 + 21 + 25 + 5, total)
    }

    @Test
    fun `isComebackEligibleToday detects yesterday missed without skip or freeze`() {
        val yesterday = LocalDate.of(2026, 11, 23)
        val today = LocalDate.of(2026, 11, 24)
        val habit = Habit(id = "h1", name = "Walk", icon = "🚶", colorHex = 1L, repeatDays = RepeatDays.ALL, createdAt = yesterday.toString())

        assertTrue(XpCalculator.isComebackEligibleToday(listOf(habit), emptyList(), today, emptySet(), emptySet()))
        assertFalse(XpCalculator.isComebackEligibleToday(listOf(habit), emptyList(), today, setOf(yesterday), emptySet()))
        assertFalse(XpCalculator.isComebackEligibleToday(listOf(habit), emptyList(), today, emptySet(), setOf(yesterday)))
    }
}
