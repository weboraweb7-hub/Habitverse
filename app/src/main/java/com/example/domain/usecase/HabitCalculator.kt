package com.example.domain.usecase

import com.example.domain.model.DailySheetRow
import com.example.domain.model.DayCompletion
import com.example.domain.model.DayNote
import com.example.domain.model.Habit
import com.example.domain.model.HabitLog
import com.example.domain.model.HabitStats
import com.example.domain.model.LocalInsights
import com.example.domain.model.RepeatDays
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.min

object HabitCalculator {

    /**
     * A habit counts for [date] only if:
     * - created on or before [date]
     * - not archived on or before [date]
     * - [date]'s weekday is included in [habit.repeatDays]
     */
    fun isHabitActiveOn(habit: Habit, date: LocalDate): Boolean {
        val created = habit.createdDate
        if (date.isBefore(created)) return false
        val archived = habit.archivedDate
        if (archived != null && !date.isBefore(archived)) return false
        return RepeatDays.isScheduledOn(habit.repeatDays, date.dayOfWeek)
    }

    fun scheduledHabitsForDate(habits: List<Habit>, date: LocalDate): List<Habit> =
        habits.filter { isHabitActiveOn(it, date) }
            .sortedWith(compareBy<Habit> { it.timeOfDay.sortRank }.thenBy { it.sortOrder })

    fun indexLogs(logs: List<HabitLog>): Map<Pair<String, String>, HabitLog> =
        logs.associateBy { it.habitId to it.date }

    fun dayCompletion(
        habits: List<Habit>,
        logsByKey: Map<Pair<String, String>, HabitLog>,
        date: LocalDate,
        skippedDates: Set<LocalDate> = emptySet(),
        frozenDates: Set<LocalDate> = emptySet()
    ): DayCompletion {
        val isSkipped = date in skippedDates
        val isFrozen = date in frozenDates
        val scheduled = scheduledHabitsForDate(habits, date)
        if (scheduled.isEmpty()) {
            return DayCompletion(date, 0, 0, isSkipped = isSkipped, isFrozen = isFrozen)
        }
        val dateKey = date.toString()
        val done = scheduled.count { habit -> logsByKey[habit.id to dateKey]?.done == true }
        return DayCompletion(
            date = date,
            scheduledCount = scheduled.size,
            completedCount = done,
            isSkipped = isSkipped,
            isFrozen = isFrozen
        )
    }

    fun calculateCurrentStreak(
        habits: List<Habit>,
        logs: List<HabitLog>,
        today: LocalDate,
        skippedDates: Set<LocalDate> = emptySet(),
        frozenDates: Set<LocalDate> = emptySet()
    ): Int {
        if (habits.isEmpty()) return 0
        val earliestDate = habits.minOf { it.createdDate }
        if (today.isBefore(earliestDate)) return 0

        val logsByKey = indexLogs(logs)
        val todayCompletion = dayCompletion(habits, logsByKey, today, skippedDates, frozenDates)

        var cursor = if (todayCompletion.scheduledCount > 0 &&
            todayCompletion.completedCount >= todayCompletion.scheduledCount
        ) {
            today
        } else {
            today.minusDays(1)
        }

        var streak = 0
        while (!cursor.isBefore(earliestDate)) {
            val comp = dayCompletion(habits, logsByKey, cursor, skippedDates, frozenDates)
            if (comp.scheduledCount == 0) {
                cursor = cursor.minusDays(1)
                continue
            }
            if (comp.completedCount >= comp.scheduledCount) {
                streak++
                cursor = cursor.minusDays(1)
            } else if (comp.isSkipped || comp.isFrozen) {
                // Skip/vacation day or streak freeze preserves the streak without breaking it
                cursor = cursor.minusDays(1)
            } else {
                break
            }
        }
        return streak
    }

    fun calculateBestStreak(
        habits: List<Habit>,
        logs: List<HabitLog>,
        today: LocalDate,
        skippedDates: Set<LocalDate> = emptySet(),
        frozenDates: Set<LocalDate> = emptySet()
    ): Int {
        if (habits.isEmpty()) return 0
        val earliestDate = habits.minOf { it.createdDate }
        if (today.isBefore(earliestDate)) return 0

        val logsByKey = indexLogs(logs)
        var best = 0
        var running = 0
        var cursor = earliestDate
        while (!cursor.isAfter(today)) {
            val comp = dayCompletion(habits, logsByKey, cursor, skippedDates, frozenDates)
            if (comp.scheduledCount > 0) {
                if (comp.completedCount >= comp.scheduledCount) {
                    running++
                    if (running > best) best = running
                } else if (!comp.isSkipped && !comp.isFrozen) {
                    running = 0
                }
            }
            cursor = cursor.plusDays(1)
        }
        return best
    }

    fun calculateCompletionRate(
        habits: List<Habit>,
        logs: List<HabitLog>,
        startDate: LocalDate,
        endDate: LocalDate,
        skippedDates: Set<LocalDate> = emptySet()
    ): Float {
        if (habits.isEmpty() || endDate.isBefore(startDate)) return 0f
        val logsByKey = indexLogs(logs)
        var totalScheduled = 0
        var totalDone = 0
        var cursor = startDate
        while (!cursor.isAfter(endDate)) {
            if (cursor !in skippedDates) {
                val comp = dayCompletion(habits, logsByKey, cursor)
                totalScheduled += comp.scheduledCount
                totalDone += comp.completedCount
            }
            cursor = cursor.plusDays(1)
        }
        return if (totalScheduled == 0) 0f else totalDone.toFloat() / totalScheduled.toFloat()
    }

    fun calculateHabitStats(
        habit: Habit,
        logs: List<HabitLog>,
        startDate: LocalDate,
        endDate: LocalDate,
        today: LocalDate,
        skippedDates: Set<LocalDate> = emptySet(),
        frozenDates: Set<LocalDate> = emptySet()
    ): HabitStats {
        val logsByKey = indexLogs(logs)
        val effectiveStart = if (startDate.isBefore(habit.createdDate)) habit.createdDate else startDate
        val maxActiveDate = habit.archivedDate?.minusDays(1) ?: today
        val effectiveEnd = if (endDate.isAfter(maxActiveDate)) maxActiveDate else endDate

        var scheduled = 0
        var done = 0
        if (!effectiveEnd.isBefore(effectiveStart)) {
            var cursor = effectiveStart
            while (!cursor.isAfter(effectiveEnd)) {
                if (cursor !in skippedDates && isHabitActiveOn(habit, cursor)) {
                    scheduled++
                    if (logsByKey[habit.id to cursor.toString()]?.done == true) {
                        done++
                    }
                }
                cursor = cursor.plusDays(1)
            }
        }

        // Current streak for this single habit
        var streak = 0
        val streakEnd = minOf(today, maxActiveDate)
        if (!streakEnd.isBefore(habit.createdDate)) {
            val checkToday = isHabitActiveOn(habit, today) &&
                logsByKey[habit.id to today.toString()]?.done == true
            var cursor = if (checkToday || today.isAfter(streakEnd)) streakEnd else streakEnd.minusDays(1)
            while (!cursor.isBefore(habit.createdDate)) {
                if (!isHabitActiveOn(habit, cursor)) {
                    cursor = cursor.minusDays(1)
                    continue
                }
                val isDone = logsByKey[habit.id to cursor.toString()]?.done == true
                if (isDone) {
                    streak++
                    cursor = cursor.minusDays(1)
                } else if (cursor in skippedDates || cursor in frozenDates) {
                    cursor = cursor.minusDays(1)
                } else {
                    break
                }
            }
        }

        val percent = if (scheduled == 0) 0 else ((done.toFloat() / scheduled.toFloat()) * 100f).toInt()
        return HabitStats(
            habit = habit,
            currentStreak = streak,
            completionPercent = percent.coerceIn(0, 100),
            completedDays = done,
            scheduledDays = scheduled
        )
    }

    fun buildDailySheet(
        habits: List<Habit>,
        logs: List<HabitLog>,
        notes: List<DayNote>,
        startDate: LocalDate,
        endDate: LocalDate,
        skippedDates: Set<LocalDate> = emptySet(),
        frozenDates: Set<LocalDate> = emptySet()
    ): List<DailySheetRow> {
        if (endDate.isBefore(startDate)) return emptyList()
        val logsByKey = indexLogs(logs)
        val notesByDate = notes.associateBy { it.date }
        val totalDays = ChronoUnit.DAYS.between(startDate, endDate).toInt()
        val rows = ArrayList<DailySheetRow>(totalDays + 1)
        var cursor = endDate
        while (!cursor.isBefore(startDate)) {
            val comp = dayCompletion(habits, logsByKey, cursor, skippedDates, frozenDates)
            val noteEntry = notesByDate[cursor.toString()]
            val noteText = noteEntry?.note.orEmpty()
            rows.add(
                DailySheetRow(
                    date = cursor,
                    doneCount = comp.completedCount,
                    totalCount = comp.scheduledCount,
                    percent = comp.percent,
                    note = noteText,
                    isSkipped = comp.isSkipped,
                    isFrozen = comp.isFrozen,
                    mood = noteEntry?.mood
                )
            )
            cursor = cursor.minusDays(1)
        }
        return rows
    }

    fun findProtectableMissedDate(
        habits: List<Habit>,
        logs: List<HabitLog>,
        today: LocalDate,
        skippedDates: Set<LocalDate>,
        frozenDates: Set<LocalDate>
    ): LocalDate? {
        if (habits.isEmpty()) return null
        val logsByKey = indexLogs(logs)
        for (daysAgo in 1L..3L) {
            val candidate = today.minusDays(daysAgo)
            val comp = dayCompletion(habits, logsByKey, candidate, skippedDates, frozenDates)
            if (comp.scheduledCount > 0 &&
                comp.completedCount < comp.scheduledCount &&
                !comp.isSkipped &&
                !comp.isFrozen
            ) {
                return candidate
            }
        }
        return null
    }

    fun computeLocalInsights(
        habits: List<Habit>,
        logs: List<HabitLog>,
        today: LocalDate,
        skippedDates: Set<LocalDate> = emptySet(),
        notes: List<DayNote> = emptyList()
    ): LocalInsights? {
        val activeHabits = habits.filter { it.archivedAt == null }
        if (activeHabits.isEmpty()) return null

        val logsByKey = indexLogs(logs)
        val earliest = habits.minOf { it.createdDate }
        val windowStart = maxOf(earliest, today.minusDays(29))

        // 1. Best weekday across last 30 days
        val weekdayScheduled = mutableMapOf<DayOfWeek, Int>()
        val weekdayDone = mutableMapOf<DayOfWeek, Int>()
        var cursor = windowStart
        while (!cursor.isAfter(today)) {
            if (cursor !in skippedDates) {
                val comp = dayCompletion(habits, logsByKey, cursor)
                if (comp.scheduledCount > 0) {
                    val dow = cursor.dayOfWeek
                    weekdayScheduled[dow] = (weekdayScheduled[dow] ?: 0) + comp.scheduledCount
                    weekdayDone[dow] = (weekdayDone[dow] ?: 0) + comp.completedCount
                }
            }
            cursor = cursor.plusDays(1)
        }

        val bestWeekdayEntry = weekdayScheduled.entries
            .filter { it.value > 0 }
            .maxByOrNull { (dow, sched) ->
                (weekdayDone[dow] ?: 0).toFloat() / sched.toFloat()
            }

        val bestWeekdayLabel = bestWeekdayEntry?.key
            ?.getDisplayName(TextStyle.FULL, Locale.getDefault())
            ?.let { "${it}s" } ?: "Today"
        val bestWeekdayPercent = bestWeekdayEntry?.let { (dow, sched) ->
            (((weekdayDone[dow] ?: 0).toFloat() / sched.toFloat()) * 100f).toInt().coerceIn(0, 100)
        } ?: 0

        // 2. Most-missed active habit (last 30 days, >= 2 scheduled days)
        val habitRates = activeHabits.map { habit ->
            calculateHabitStats(habit, logs, windowStart, today, today, skippedDates)
        }.filter { it.scheduledDays >= 2 }

        val mostMissed = habitRates
            .filter { it.completionPercent < 100 }
            .minByOrNull { it.completionPercent }

        // 3. Weekly summary (last 7 days vs prior 7 days)
        var weekDone = 0
        var weekSched = 0
        for (d in 0L..6L) {
            val day = today.minusDays(d)
            if (day !in skippedDates) {
                val comp = dayCompletion(habits, logsByKey, day)
                weekDone += comp.completedCount
                weekSched += comp.scheduledCount
            }
        }
        val weekPercent = if (weekSched == 0) 0 else ((weekDone.toFloat() / weekSched.toFloat()) * 100f).toInt().coerceIn(0, 100)

        var prevWeekDone = 0
        var prevWeekSched = 0
        for (d in 7L..13L) {
            val day = today.minusDays(d)
            if (day !in skippedDates) {
                val comp = dayCompletion(habits, logsByKey, day)
                prevWeekDone += comp.completedCount
                prevWeekSched += comp.scheduledCount
            }
        }
        val prevPercent = if (prevWeekSched == 0) weekPercent else ((prevWeekDone.toFloat() / prevWeekSched.toFloat()) * 100f).toInt().coerceIn(0, 100)

        val moodInsight = computeMoodInsight(habits, logsByKey, notes, skippedDates)

        return LocalInsights(
            bestWeekdayLabel = bestWeekdayLabel,
            bestWeekdayPercent = bestWeekdayPercent,
            mostMissedHabitName = mostMissed?.habit?.name,
            mostMissedPercent = mostMissed?.completionPercent,
            weeklyCompleted = weekDone,
            weeklyScheduled = weekSched,
            weeklyPercent = weekPercent,
            weeklyDeltaPercent = weekPercent - prevPercent,
            moodInsightText = moodInsight
        )
    }

    fun computeMoodInsight(
        habits: List<Habit>,
        logsByKey: Map<Pair<String, String>, HabitLog>,
        notes: List<DayNote>,
        skippedDates: Set<LocalDate> = emptySet()
    ): String? {
        val moodDays = notes.filter { it.mood != null }
        if (moodDays.size < 14) return null

        val highMoodRatios = mutableListOf<Float>()
        val lowMoodRatios = mutableListOf<Float>()
        moodDays.forEach { entry ->
            val date = runCatching { LocalDate.parse(entry.date) }.getOrNull() ?: return@forEach
            if (date in skippedDates) return@forEach
            val comp = dayCompletion(habits, logsByKey, date)
            if (comp.scheduledCount <= 0) return@forEach
            val mood = entry.mood ?: return@forEach
            when {
                mood >= 4 -> highMoodRatios.add(comp.ratio)
                mood <= 2 -> lowMoodRatios.add(comp.ratio)
            }
        }

        if (highMoodRatios.isEmpty()) return null
        val highAvg = highMoodRatios.average().toFloat()
        if (lowMoodRatios.isEmpty()) {
            val pct = (highAvg * 100f).toInt().coerceIn(0, 100)
            return "On upbeat days (🙂/🤩), you average $pct% completion."
        }
        val lowAvg = lowMoodRatios.average().toFloat()
        if (lowAvg > 0f && highAvg > lowAvg) {
            val liftPct = (((highAvg - lowAvg) / lowAvg) * 100f).toInt().coerceAtLeast(1)
            return "You complete $liftPct% more habits on 😊 days."
        }
        val highPct = (highAvg * 100f).toInt().coerceIn(0, 100)
        val lowPct = (lowAvg * 100f).toInt().coerceIn(0, 100)
        return "You hit $highPct% on high-energy days and $lowPct% on low-energy days."
    }

    data class SkyStar(
        val date: LocalDate,
        val completedCount: Int,
        val scheduledCount: Int,
        val ratio: Float,
        val isStreakDay: Boolean,
        val connectedToPrev: Boolean
    )

    fun buildConsistencySky(
        habits: List<Habit>,
        logs: List<HabitLog>,
        today: LocalDate,
        skippedDates: Set<LocalDate> = emptySet(),
        frozenDates: Set<LocalDate> = emptySet(),
        daysCount: Int = 90
    ): List<SkyStar> {
        val count = daysCount.coerceAtLeast(1)
        val logsByKey = indexLogs(logs)
        val start = today.minusDays((count - 1).toLong())
        val result = ArrayList<SkyStar>(count)
        var prevStreak = false
        for (i in 0 until count) {
            val date = start.plusDays(i.toLong())
            val comp = dayCompletion(habits, logsByKey, date, skippedDates, frozenDates)
            val isStreakDay = (comp.scheduledCount > 0 && comp.completedCount >= comp.scheduledCount) ||
                comp.isSkipped || comp.isFrozen
            val connected = isStreakDay && prevStreak
            result.add(
                SkyStar(
                    date = date,
                    completedCount = comp.completedCount,
                    scheduledCount = comp.scheduledCount,
                    ratio = comp.ratio.coerceIn(0f, 1f),
                    isStreakDay = isStreakDay,
                    connectedToPrev = connected
                )
            )
            prevStreak = isStreakDay
        }
        return result
    }

    data class SmartReminderSuggestion(
        val habit: Habit,
        val suggestedHour: Int,
        val suggestedMinute: Int,
        val currentReminderFormatted: String?
    )

    fun findSmartReminderSuggestion(
        habits: List<Habit>,
        logs: List<HabitLog>,
        remindersByHabit: Map<String, List<com.example.domain.model.HabitReminder>>,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): SmartReminderSuggestion? {
        val active = habits.filter { it.archivedAt == null }
        for (habit in active) {
            val minutesList = logs
                .filter { it.habitId == habit.id && it.done && it.completedAtMs != null }
                .map { log ->
                    val zdt = Instant.ofEpochMilli(log.completedAtMs!!).atZone(zoneId)
                    zdt.hour * 60 + zdt.minute
                }
                .sorted()
            if (minutesList.size < 10) continue

            val medianMinutes = minutesList[minutesList.size / 2]
            val roundedMinutes = ((medianMinutes + 2) / 5) * 5 % 1440
            val suggestedHour = (roundedMinutes / 60) % 24
            val suggestedMinute = roundedMinutes % 60

            val existing = remindersByHabit[habit.id].orEmpty().firstOrNull()
            val currentMinutes = when {
                existing != null -> existing.hour * 60 + existing.minute
                !habit.reminderTime.isNullOrBlank() -> {
                    val parts = habit.reminderTime.split(":")
                    val h = parts.getOrNull(0)?.toIntOrNull()
                    val m = parts.getOrNull(1)?.toIntOrNull()
                    if (h != null && m != null) h * 60 + m else null
                }
                else -> null
            }

            if (currentMinutes == null) {
                return SmartReminderSuggestion(
                    habit = habit,
                    suggestedHour = suggestedHour,
                    suggestedMinute = suggestedMinute,
                    currentReminderFormatted = null
                )
            }

            val rawDiff = abs(roundedMinutes - currentMinutes)
            val circularDiff = min(rawDiff, 1440 - rawDiff)
            if (circularDiff >= 45) {
                return SmartReminderSuggestion(
                    habit = habit,
                    suggestedHour = suggestedHour,
                    suggestedMinute = suggestedMinute,
                    currentReminderFormatted = String.format(
                        Locale.US,
                        "%02d:%02d",
                        currentMinutes / 60,
                        currentMinutes % 60
                    )
                )
            }
        }
        return null
    }

    data class ChallengeProgress(
        val dayNumber: Int,
        val totalDays: Int,
        val isCompleted: Boolean
    )

    fun challengeProgress(habit: Habit, date: LocalDate): ChallengeProgress? {
        val start = habit.challengeStartDate ?: return null
        if (habit.challengeDays <= 0 || date.isBefore(start)) return null
        val elapsed = ChronoUnit.DAYS.between(start, date).toInt() + 1
        return ChallengeProgress(
            dayNumber = elapsed.coerceIn(1, habit.challengeDays),
            totalDays = habit.challengeDays,
            isCompleted = elapsed >= habit.challengeDays
        )
    }
}
