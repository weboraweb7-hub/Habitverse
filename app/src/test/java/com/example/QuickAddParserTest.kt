package com.example

import com.example.domain.model.HabitCategory
import com.example.domain.model.HabitType
import com.example.domain.model.RepeatDays
import com.example.domain.model.TimeOfDay
import com.example.domain.usecase.QuickAddParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek

class QuickAddParserTest {

    @Test
    fun `parses count, unit, 12h time, and daily repeat`() {
        val parsed = QuickAddParser.parse("Drink 8 glasses of water at 9am daily")!!
        assertEquals(HabitType.COUNT, parsed.type)
        assertEquals(8, parsed.targetValue)
        assertEquals("glasses", parsed.unitLabel)
        assertEquals(9, parsed.reminderHour)
        assertEquals(0, parsed.reminderMinute)
        assertEquals(RepeatDays.ALL, parsed.repeatDays)
        assertEquals(TimeOfDay.MORNING, parsed.timeOfDay)
        assertEquals(HabitCategory.HEALTH, parsed.category)
    }

    @Test
    fun `parses timer minutes, 24h time, and weekdays`() {
        val parsed = QuickAddParser.parse("Deep work 45 min at 14:30 weekdays")!!
        assertEquals(HabitType.TIMER, parsed.type)
        assertEquals(45, parsed.targetValue)
        assertEquals("min", parsed.unitLabel)
        assertEquals(14, parsed.reminderHour)
        assertEquals(30, parsed.reminderMinute)
        assertEquals(RepeatDays.WEEKDAYS, parsed.repeatDays)
        assertEquals(TimeOfDay.AFTERNOON, parsed.timeOfDay)
        assertEquals(HabitCategory.PRODUCTIVITY, parsed.category)
    }

    @Test
    fun `parses specific weekdays and evening slot`() {
        val parsed = QuickAddParser.parse("Read 20 pages evening mon wed fri")!!
        assertEquals(HabitType.COUNT, parsed.type)
        assertEquals(20, parsed.targetValue)
        assertEquals("pages", parsed.unitLabel)
        assertEquals(TimeOfDay.EVENING, parsed.timeOfDay)
        val expectedMask = RepeatDays.bitFor(DayOfWeek.MONDAY) or
            RepeatDays.bitFor(DayOfWeek.WEDNESDAY) or
            RepeatDays.bitFor(DayOfWeek.FRIDAY)
        assertEquals(expectedMask, parsed.repeatDays)
    }

    @Test
    fun `plain habit falls back cleanly and blank returns null`() {
        assertNull(QuickAddParser.parse("   "))
        val plain = QuickAddParser.parse("Call parents")
        assertNotNull(plain)
        assertEquals("Call parents", plain!!.name)
        assertEquals(HabitType.YES_NO, plain.type)
        assertEquals(RepeatDays.ALL, plain.repeatDays)
        assertEquals(TimeOfDay.ANYTIME, plain.timeOfDay)
    }
}
