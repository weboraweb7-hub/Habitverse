package com.example.domain.usecase

import com.example.domain.model.HabitCategory
import com.example.domain.model.HabitPalette
import com.example.domain.model.HabitType
import com.example.domain.model.RepeatDays
import com.example.domain.model.TimeOfDay
import java.time.DayOfWeek
import java.util.Locale

data class QuickAddParsedHabit(
    val name: String,
    val icon: String,
    val colorHex: Long,
    val type: HabitType,
    val targetValue: Int,
    val unitLabel: String,
    val repeatDays: Int,
    val timeOfDay: TimeOfDay,
    val category: HabitCategory,
    val reminderHour: Int? = null,
    val reminderMinute: Int? = null
)

object QuickAddParser {

    private val TIME_AT_REGEX = Regex(
        """(?i)(?:^|\s)(?:at\s+)?(\d{1,2})(?::(\d{2}))?\s*(am|pm)(?=\s|$)"""
    )
    private val TIME_24H_REGEX = Regex(
        """(?i)(?:^|\s)at\s+(\d{1,2}):(\d{2})(?=\s|$)"""
    )
    private val TIMER_REGEX = Regex(
        """(?i)\b(\d{1,3})\s*(?:min|mins|minute|minutes|m)\b"""
    )
    private val COUNT_REGEX = Regex(
        """(?i)\b(\d{1,4})\s*(glasses|glass|cups|cup|pages|page|pushups|push-ups|squats|steps|km|miles|ml|oz|reps|sets|chapters|chapter|items|times)\b"""
    )
    private val REPEAT_PHRASE_REGEX = Regex(
        """(?i)\b(every\s*day|everyday|daily|weekdays|weekday|weekends|weekend|mon-fri|sat-sun|mondays|tuesdays|wednesdays|thursdays|fridays|saturdays|sundays|mon|tue|tues|wed|thu|thur|thurs|fri|sat|sun)\b"""
    )
    private val SLOT_PHRASE_REGEX = Regex(
        """(?i)\b(in\s+the\s+morning|in\s+the\s+afternoon|in\s+the\s+evening|every\s+morning|every\s+evening|morning|afternoon|evening|night)\b"""
    )

    fun parse(rawInput: String): QuickAddParsedHabit? {
        val trimmed = rawInput.trim()
        if (trimmed.isBlank()) return null

        var working = trimmed
        var reminderHour: Int? = null
        var reminderMinute: Int? = null

        // 1. Parse time (12h with am/pm first, then 24h "at HH:MM")
        val amPmMatch = TIME_AT_REGEX.find(working)
        if (amPmMatch != null) {
            val rawHour = amPmMatch.groupValues[1].toIntOrNull()
            val rawMin = amPmMatch.groupValues[2].toIntOrNull() ?: 0
            val amPm = amPmMatch.groupValues[3].lowercase(Locale.US)
            if (rawHour != null && rawHour in 1..12 && rawMin in 0..59) {
                reminderHour = when {
                    amPm == "am" && rawHour == 12 -> 0
                    amPm == "pm" && rawHour != 12 -> rawHour + 12
                    else -> rawHour
                }
                reminderMinute = rawMin
                working = working.removeRange(amPmMatch.range).trim()
            }
        } else {
            val h24Match = TIME_24H_REGEX.find(working)
            if (h24Match != null) {
                val rawHour = h24Match.groupValues[1].toIntOrNull()
                val rawMin = h24Match.groupValues[2].toIntOrNull() ?: 0
                if (rawHour != null && rawHour in 0..23 && rawMin in 0..59) {
                    reminderHour = rawHour
                    reminderMinute = rawMin
                    working = working.removeRange(h24Match.range).trim()
                }
            }
        }

        // 2. Parse repeat days
        var repeatDays = RepeatDays.ALL
        var foundSpecificWeekdays = 0
        val repeatMatches = REPEAT_PHRASE_REGEX.findAll(working).toList()
        if (repeatMatches.isNotEmpty()) {
            var overrideMask: Int? = null
            repeatMatches.forEach { m ->
                when (m.groupValues[1].lowercase(Locale.US).replace(" ", "")) {
                    "everyday", "daily" -> overrideMask = RepeatDays.ALL
                    "weekdays", "weekday", "mon-fri" -> overrideMask = RepeatDays.WEEKDAYS
                    "weekends", "weekend", "sat-sun" -> {
                        overrideMask = RepeatDays.WEEKENDS
                    }
                    "mon", "mondays" -> foundSpecificWeekdays = foundSpecificWeekdays or RepeatDays.bitFor(DayOfWeek.MONDAY)
                    "tue", "tues", "tuesdays" -> foundSpecificWeekdays = foundSpecificWeekdays or RepeatDays.bitFor(DayOfWeek.TUESDAY)
                    "wed", "wednesdays" -> foundSpecificWeekdays = foundSpecificWeekdays or RepeatDays.bitFor(DayOfWeek.WEDNESDAY)
                    "thu", "thur", "thurs", "thursdays" -> foundSpecificWeekdays = foundSpecificWeekdays or RepeatDays.bitFor(DayOfWeek.THURSDAY)
                    "fri", "fridays" -> foundSpecificWeekdays = foundSpecificWeekdays or RepeatDays.bitFor(DayOfWeek.FRIDAY)
                    "sat", "saturdays" -> foundSpecificWeekdays = foundSpecificWeekdays or RepeatDays.bitFor(DayOfWeek.SATURDAY)
                    "sun", "sundays" -> foundSpecificWeekdays = foundSpecificWeekdays or RepeatDays.bitFor(DayOfWeek.SUNDAY)
                }
            }
            repeatDays = when {
                foundSpecificWeekdays != 0 -> foundSpecificWeekdays
                overrideMask != null -> overrideMask!!
                else -> RepeatDays.ALL
            }
            working = working.replace(REPEAT_PHRASE_REGEX, " ").trim()
        }

        // 3. Parse TimeOfDay keyword
        var timeOfDay: TimeOfDay? = null
        val slotMatch = SLOT_PHRASE_REGEX.find(working)
        if (slotMatch != null) {
            val token = slotMatch.groupValues[1].lowercase(Locale.US)
            timeOfDay = when {
                "morning" in token -> TimeOfDay.MORNING
                "afternoon" in token -> TimeOfDay.AFTERNOON
                "evening" in token || "night" in token -> TimeOfDay.EVENING
                else -> TimeOfDay.ANYTIME
            }
            working = working.replace(SLOT_PHRASE_REGEX, " ").trim()
        }
        if (timeOfDay == null && reminderHour != null) {
            timeOfDay = when {
                reminderHour < 12 -> TimeOfDay.MORNING
                reminderHour < 17 -> TimeOfDay.AFTERNOON
                else -> TimeOfDay.EVENING
            }
        }

        // 4. Parse Timer or Count target
        var type = HabitType.YES_NO
        var targetValue = 1
        var unitLabel = ""

        val timerMatch = TIMER_REGEX.find(working)
        val countMatch = COUNT_REGEX.find(working)

        if (timerMatch != null) {
            val mins = timerMatch.groupValues[1].toIntOrNull()?.coerceIn(1, 300) ?: 15
            type = HabitType.TIMER
            targetValue = mins
            unitLabel = "min"
        } else if (countMatch != null) {
            val count = countMatch.groupValues[1].toIntOrNull()?.coerceIn(1, 9999) ?: 1
            val unit = countMatch.groupValues[2].lowercase(Locale.US)
            type = HabitType.COUNT
            targetValue = count
            unitLabel = unit
        }

        // 5. Clean up habit name
        val cleanedName = working
            .replace(Regex("""\s+(?:for|on|each|every)\s*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s{2,}"""), " ")
            .trim()
            .ifBlank { trimmed }
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            .take(80)

        val lower = trimmed.lowercase(Locale.US)
        val (icon, category, colorHex) = inferMetadata(lower)

        return QuickAddParsedHabit(
            name = cleanedName,
            icon = icon,
            colorHex = colorHex,
            type = type,
            targetValue = targetValue,
            unitLabel = unitLabel,
            repeatDays = repeatDays,
            timeOfDay = timeOfDay ?: TimeOfDay.ANYTIME,
            category = category,
            reminderHour = reminderHour,
            reminderMinute = reminderMinute
        )
    }

    private fun inferMetadata(lower: String): Triple<String, HabitCategory, Long> = when {
        "water" in lower || "hydrate" in lower || "glass" in lower ->
            Triple("💧", HabitCategory.HEALTH, HabitPalette.colors[2])
        "read" in lower || "book" in lower || "page" in lower || "study" in lower ->
            Triple("📖", HabitCategory.LEARNING, HabitPalette.colors[4])
        "meditat" in lower || "breath" in lower || "journal" in lower || "gratitude" in lower ->
            Triple("🧘", HabitCategory.MINDFULNESS, HabitPalette.colors[7])
        "run" in lower || "jog" in lower || "workout" in lower || "gym" in lower || "pushup" in lower ->
            Triple("🏃", HabitCategory.FITNESS, HabitPalette.colors[1])
        "walk" in lower || "steps" in lower ->
            Triple("🚶", HabitCategory.FITNESS, HabitPalette.colors[1])
        "sleep" in lower || "bed" in lower || "wind down" in lower || "wind-down" in lower ->
            Triple("😴", HabitCategory.SLEEP, HabitPalette.colors[3])
        "focus" in lower || "deep work" in lower || "code" in lower || "write" in lower ->
            Triple("🎯", HabitCategory.PRODUCTIVITY, HabitPalette.colors[6])
        "stretch" in lower || "yoga" in lower ->
            Triple("💪", HabitCategory.FITNESS, HabitPalette.colors[0])
        else ->
            Triple("✨", HabitCategory.GENERAL, HabitPalette.colors[0])
    }
}
