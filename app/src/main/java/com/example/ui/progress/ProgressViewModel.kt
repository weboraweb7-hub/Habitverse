package com.example.ui.progress

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.prefs.UserPreferencesRepository
import com.example.data.repository.HabitRepository
import com.example.domain.model.DailySheetRow
import com.example.domain.model.DayCompletion
import com.example.domain.model.HabitStats
import com.example.domain.model.LocalInsights
import com.example.domain.model.TimeRange
import com.example.domain.usecase.CsvExporter
import com.example.domain.usecase.CurrentDateProvider
import com.example.domain.usecase.HabitCalculator
import com.example.domain.usecase.HabitStrength
import com.example.domain.usecase.HabitStrengthCalculator
import com.example.domain.usecase.XpCalculator
import com.example.domain.usecase.XpProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class WeeklyRecapData(
    val dateRangeLabel: String,
    val weekPercent: Int,
    val weekDeltaPercent: Int,
    val weekCompleted: Int,
    val weekScheduled: Int,
    val currentStreak: Int,
    val bestStreak: Int,
    val topHabitName: String?,
    val topHabitIcon: String?,
    val topHabitStrengthPercent: Int,
    val topHabitStrengthLabel: String?,
    val level: Int,
    val totalXp: Int,
    val averageMoodEmoji: String?,
    val unlockedBadgeCount: Int
)

data class ProgressUiState(
    val selectedRange: TimeRange = TimeRange.DAYS_7,
    val today: LocalDate = LocalDate.now(),
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val completionPercent: Int = 0,
    val weeklyBars: List<DayCompletion> = emptyList(),
    val currentMonth: YearMonth = YearMonth.now(),
    val monthCompletions: Map<LocalDate, DayCompletion> = emptyMap(),
    val perHabitStats: List<HabitStats> = emptyList(),
    val dailySheetRows: List<DailySheetRow> = emptyList(),
    val localInsights: LocalInsights? = null,
    val xpProgress: XpProgress = XpCalculator.buildXpProgress(0),
    val habitStrengths: List<HabitStrength> = emptyList(),
    val skyStars: List<HabitCalculator.SkyStar> = emptyList(),
    val weeklyRecap: WeeklyRecapData? = null,
    val unlockedBadgesById: Map<String, String> = emptyMap()
)

@HiltViewModel
class ProgressViewModel @Inject constructor(
    private val habitRepository: HabitRepository,
    private val currentDateProvider: CurrentDateProvider,
    private val csvExporter: CsvExporter,
    private val prefsRepository: UserPreferencesRepository
) : ViewModel() {

    private val _selectedRange = MutableStateFlow(TimeRange.DAYS_7)

    private val extrasFlow = combine(
        habitRepository.observeAllDailyRewards(),
        habitRepository.observeAllUnlockedBadges(),
        prefsRepository.userPrefs
    ) { rewards, badges, prefs ->
        Triple(rewards, badges, prefs)
    }

    val uiState: StateFlow<ProgressUiState> = combine(
        habitRepository.observeAllHabits(),
        habitRepository.observeAllLogs(),
        habitRepository.observeAllNotes(),
        combine(
            habitRepository.observeSkipDays(),
            habitRepository.observeStreakFreezes()
        ) { skips, freezes ->
            skips.mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }.toSet() to
                freezes.mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }.toSet()
        },
        combine(_selectedRange, currentDateProvider.today, extrasFlow) { r, t, ex ->
            Triple(r, t, ex)
        }
    ) { habits, logs, notes, (skippedDates, frozenDates), (range, today, extras) ->
        val (rewards, unlockedBadges, prefs) = extras
        val logsByKey = HabitCalculator.indexLogs(logs)
        val earliestDate = habits.minOfOrNull { it.createdDate } ?: today.minusDays(6)
        val startDate = when (range) {
            TimeRange.DAYS_7 -> today.minusDays(6)
            TimeRange.DAYS_30 -> today.minusDays(29)
            TimeRange.ALL_TIME -> earliestDate
        }

        val currentStreak = HabitCalculator.calculateCurrentStreak(habits, logs, today, skippedDates, frozenDates)
        val bestStreak = HabitCalculator.calculateBestStreak(habits, logs, today, skippedDates, frozenDates)
        val rate = HabitCalculator.calculateCompletionRate(habits, logs, startDate, today, skippedDates)

        val weeklyBars = (6L downTo 0L).map { offset ->
            HabitCalculator.dayCompletion(habits, logsByKey, today.minusDays(offset), skippedDates, frozenDates)
        }

        val ym = YearMonth.from(today)
        val monthCompletions = (1..ym.lengthOfMonth()).associate { day ->
            val d = ym.atDay(day)
            d to HabitCalculator.dayCompletion(habits, logsByKey, d, skippedDates, frozenDates)
        }

        val activeHabits = habits.filter { it.archivedAt == null }
        val perHabitStats = activeHabits.map { habit ->
            HabitCalculator.calculateHabitStats(habit, logs, startDate, today, today, skippedDates, frozenDates)
        }

        val habitStrengths = activeHabits.map { habit ->
            HabitStrengthCalculator.calculateStrength(
                habit = habit,
                logsByKey = logsByKey,
                today = today,
                skippedDates = skippedDates,
                frozenDates = frozenDates
            )
        }.sortedByDescending { it.scorePercent }

        val sheetStart = maxOf(
            when (range) {
                TimeRange.DAYS_7 -> today.minusDays(6)
                TimeRange.DAYS_30 -> today.minusDays(29)
                TimeRange.ALL_TIME -> earliestDate
            },
            today.minusDays(365)
        )
        val dailySheet = HabitCalculator.buildDailySheet(habits, logs, notes, sheetStart, today, skippedDates, frozenDates)
        val insights = HabitCalculator.computeLocalInsights(habits, logs, today, skippedDates, notes)
        val totalXp = XpCalculator.calculateTotalXp(
            habits = habits,
            logs = logs,
            dailyRewards = rewards,
            plantBonusXpTotal = prefs.plantBonusXpTotal,
            skippedDates = skippedDates,
            frozenDates = frozenDates,
            today = today
        )
        val xpProgress = XpCalculator.buildXpProgress(totalXp)
        val skyStars = HabitCalculator.buildConsistencySky(
            habits = habits,
            logs = logs,
            today = today,
            skippedDates = skippedDates,
            frozenDates = frozenDates,
            daysCount = 90
        )

        val weeklyRecap = if (activeHabits.isNotEmpty() && insights != null) {
            val fmt = DateTimeFormatter.ofPattern("MMM d")
            val weekStart = today.minusDays(6)
            val weekMoods = notes.filter { entry ->
                val d = runCatching { LocalDate.parse(entry.date) }.getOrNull()
                d != null && !d.isBefore(weekStart) && !d.isAfter(today) && entry.mood != null
            }.mapNotNull { it.mood }
            val avgMoodEmoji = if (weekMoods.isNotEmpty()) {
                when (kotlin.math.round(weekMoods.average()).toInt().coerceIn(1, 5)) {
                    1 -> "😫"
                    2 -> "😕"
                    3 -> "😐"
                    4 -> "🙂"
                    else -> "🤩"
                }
            } else null

            val topStrength = habitStrengths.firstOrNull()
            WeeklyRecapData(
                dateRangeLabel = "${weekStart.format(fmt)} – ${today.format(fmt)}",
                weekPercent = insights.weeklyPercent,
                weekDeltaPercent = insights.weeklyDeltaPercent,
                weekCompleted = insights.weeklyCompleted,
                weekScheduled = insights.weeklyScheduled,
                currentStreak = currentStreak,
                bestStreak = bestStreak,
                topHabitName = topStrength?.habit?.name,
                topHabitIcon = topStrength?.habit?.icon,
                topHabitStrengthPercent = topStrength?.scorePercent ?: 0,
                topHabitStrengthLabel = topStrength?.label,
                level = xpProgress.level,
                totalXp = xpProgress.totalXp,
                averageMoodEmoji = avgMoodEmoji,
                unlockedBadgeCount = unlockedBadges.size
            )
        } else {
            null
        }

        ProgressUiState(
            selectedRange = range,
            today = today,
            currentStreak = currentStreak,
            bestStreak = bestStreak,
            completionPercent = (rate * 100f).toInt().coerceIn(0, 100),
            weeklyBars = weeklyBars,
            currentMonth = ym,
            monthCompletions = monthCompletions,
            perHabitStats = perHabitStats,
            dailySheetRows = dailySheet,
            localInsights = insights,
            xpProgress = xpProgress,
            habitStrengths = habitStrengths,
            skyStars = skyStars,
            weeklyRecap = weeklyRecap,
            unlockedBadgesById = unlockedBadges.associate { it.badgeId to it.unlockedAt }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = ProgressUiState()
    )

    fun selectRange(range: TimeRange) {
        _selectedRange.value = range
    }

    fun saveDayNote(date: LocalDate, note: String) {
        viewModelScope.launch {
            habitRepository.saveDayNote(date, note)
        }
    }

    fun buildExportCsvShareIntent(): Intent {
        return csvExporter.createShareIntent(uiState.value.dailySheetRows)
    }
}
