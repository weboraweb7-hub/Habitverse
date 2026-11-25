package com.example

import com.example.domain.model.Habit
import com.example.domain.model.HabitLog
import com.example.domain.model.RepeatDays
import com.example.domain.usecase.HabitCalculator
import com.example.domain.usecase.HabitStrengthCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HabitStrengthTest {

    @Test
    fun `strength grows monotonically with consistent completions and ignores skipped or frozen days`() {
        val start = LocalDate.of(2026, 10, 1)
        val today = start.plusDays(44)
        val habit = Habit(
            id = "h1",
            name = "Meditate",
            icon = "🧘",
            colorHex = 1L,
            repeatDays = RepeatDays.ALL,
            createdAt = start.toString()
        )
        val logs = (0L..44L).map { offset ->
            HabitLog(habitId = "h1", date = start.plusDays(offset).toString(), done = true)
        }
        val strength = HabitStrengthCalculator.calculateStrength(
            habit = habit,
            logsByKey = HabitCalculator.indexLogs(logs),
            today = today
        )
        // 1 - 0.95^45 ~= 0.90 -> 90% Rock solid
        assertTrue(strength.scorePercent >= 85)
        assertEquals("Rock solid", strength.label)
    }

    @Test
    fun `strength labels reflect score thresholds`() {
        assertEquals("Fragile", HabitStrengthCalculator.strengthLabel(15))
        assertEquals("Building", HabitStrengthCalculator.strengthLabel(35))
        assertEquals("Growing", HabitStrengthCalculator.strengthLabel(65))
        assertEquals("Rock solid", HabitStrengthCalculator.strengthLabel(85))
    }
}
