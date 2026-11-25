package com.example

import com.example.domain.model.DailyReward
import com.example.domain.model.Habit
import com.example.domain.model.HabitCategory
import com.example.domain.model.HabitLog
import com.example.domain.model.RepeatDays
import com.example.domain.model.RewardType
import com.example.domain.usecase.BadgeEvaluationInput
import com.example.domain.usecase.BadgeEvaluator
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class BadgeEvaluatorTest {

    @Test
    fun `evaluates streak, early bird, night owl, stacker, tiny starter, comeback, chest collector, and challenge finisher badges`() {
        val zone = ZoneId.of("UTC")
        val start = LocalDate.of(2026, 11, 1)
        val today = start.plusDays(11)

        val h1 = Habit(
            id = "h1",
            name = "Stretch",
            icon = "🧘",
            colorHex = 1L,
            repeatDays = RepeatDays.ALL,
            createdAt = start.toString(),
            category = HabitCategory.FITNESS,
            challengeDays = 7,
            challengeStart = start.toString()
        )
        val h2 = Habit(
            id = "h2",
            name = "Read",
            icon = "📖",
            colorHex = 2L,
            repeatDays = RepeatDays.ALL,
            createdAt = start.toString(),
            category = HabitCategory.LEARNING,
            stackAfterHabitId = "h1"
        )
        val h3 = Habit(
            id = "h3",
            name = "Hydrate",
            icon = "💧",
            colorHex = 3L,
            repeatDays = RepeatDays.ALL,
            createdAt = start.toString(),
            category = HabitCategory.HEALTH
        )

        val earlyMs = ZonedDateTime.of(today, LocalTime.of(6, 30), zone).toInstant().toEpochMilli()
        val nightMs = ZonedDateTime.of(today, LocalTime.of(22, 15), zone).toInstant().toEpochMilli()

        val logs = mutableListOf<HabitLog>()
        // Complete all 3 on days 0..6 (finishes 7-day challenge), skip day 7 (missed!), then complete days 8..11
        for (d in 0L..11L) {
            if (d == 7L) continue
            val dateStr = start.plusDays(d).toString()
            logs.add(HabitLog("h1", dateStr, done = true, completedAtMs = earlyMs))
            logs.add(HabitLog("h2", dateStr, done = true, completedAtMs = nightMs, tiny = (d == 11L)))
            logs.add(HabitLog("h3", dateStr, done = true, completedAtMs = earlyMs))
        }

        val rewards = (0L..6L).map {
            DailyReward(start.plusDays(it).toString(), RewardType.BONUS_XP, 20, openedAtMs = 100L)
        }

        val unlocked = BadgeEvaluator.evaluateUnlockedBadgeIds(
            BadgeEvaluationInput(
                habits = listOf(h1, h2, h3),
                logs = logs,
                currentStreak = 4,
                bestStreak = 7,
                level = 5,
                dailyRewards = rewards,
                skippedDates = emptySet(),
                frozenDates = emptySet(),
                today = today
            ),
            zoneId = zone
        )

        assertTrue("first_checkin" in unlocked)
        assertTrue("streak_7" in unlocked)
        assertTrue("level_5" in unlocked)
        assertTrue("early_bird" in unlocked)
        assertTrue("night_owl" in unlocked)
        assertTrue("comeback" in unlocked)
        assertTrue("challenge_finisher" in unlocked)
        assertTrue("habit_stacker" in unlocked)
        assertTrue("tiny_starter" in unlocked)
        assertTrue("daily_collector" in unlocked)
        assertTrue("well_rounded" in unlocked)
    }
}
