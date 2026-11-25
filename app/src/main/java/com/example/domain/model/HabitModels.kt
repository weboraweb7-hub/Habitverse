package com.example.domain.model

import kotlinx.serialization.Serializable
import java.time.LocalDate

enum class HabitType(val label: String) {
    YES_NO("Yes / No"),
    COUNT("Count Target"),
    TIMER("Timer");

    companion object {
        fun fromName(raw: String?): HabitType =
            entries.firstOrNull { it.name == raw } ?: YES_NO
    }
}

enum class TimeOfDay(val label: String, val icon: String, val sortRank: Int) {
    MORNING("Morning", "🌅", 0),
    AFTERNOON("Afternoon", "☀️", 1),
    EVENING("Evening", "🌙", 2),
    ANYTIME("Anytime", "✨", 3);

    companion object {
        fun fromName(raw: String?): TimeOfDay =
            entries.firstOrNull { it.name == raw } ?: ANYTIME
    }
}

enum class HabitCategory(val label: String) {
    HEALTH("Health"),
    FITNESS("Fitness"),
    MINDFULNESS("Mindfulness"),
    LEARNING("Learning"),
    SLEEP("Sleep"),
    PRODUCTIVITY("Productivity"),
    GENERAL("General");

    companion object {
        fun fromName(raw: String?): HabitCategory =
            entries.firstOrNull { it.name == raw } ?: GENERAL
    }
}

enum class FlameSkin(val id: String, val title: String) {
    CLASSIC("CLASSIC", "Amber Flame"),
    COBALT("COBALT", "Cobalt Flare"),
    EMERALD("EMERALD", "Aurora Emerald"),
    GOLD("GOLD", "Solar Gold");

    companion object {
        fun fromId(raw: String?): FlameSkin =
            entries.firstOrNull { it.id == raw } ?: CLASSIC
    }
}

enum class RewardType {
    BONUS_XP,
    STREAK_FREEZE,
    RARE_QUOTE,
    FLAME_SKIN,
    LEGENDARY_XP;

    companion object {
        fun fromName(raw: String?): RewardType =
            entries.firstOrNull { it.name == raw } ?: BONUS_XP
    }
}

data class DailyReward(
    val date: String,
    val rewardType: RewardType,
    val rewardValue: Int,
    val openedAtMs: Long? = null
)

data class UnlockedBadge(
    val badgeId: String,
    val unlockedAt: String
)

data class Habit(
    val id: String,
    val name: String,
    val icon: String,
    val colorHex: Long,
    val repeatDays: Int,
    val reminderTime: String? = null,
    val createdAt: String,
    val archivedAt: String? = null,
    val sortOrder: Int = 0,
    val updatedAt: Long = 0L,
    val type: HabitType = HabitType.YES_NO,
    val targetValue: Int = 1,
    val unitLabel: String = "",
    val timeOfDay: TimeOfDay = TimeOfDay.ANYTIME,
    val category: HabitCategory = HabitCategory.GENERAL,
    val cueText: String = "",
    val tinyVersion: String = "",
    val stackAfterHabitId: String? = null,
    val challengeDays: Int = 0,
    val challengeStart: String? = null
) {
    val createdDate: LocalDate
        get() = LocalDate.parse(createdAt)

    val archivedDate: LocalDate?
        get() = archivedAt?.let { LocalDate.parse(it) }

    val challengeStartDate: LocalDate?
        get() = challengeStart?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    val effectiveTargetProgress: Int
        get() = when (type) {
            HabitType.YES_NO -> 1
            HabitType.COUNT -> targetValue.coerceAtLeast(1)
            HabitType.TIMER -> (targetValue.coerceAtLeast(1)) * 60
        }

    fun formatTargetLabel(): String = when (type) {
        HabitType.YES_NO -> "Check-in"
        HabitType.COUNT -> {
            val unit = unitLabel.ifBlank { "times" }
            "$targetValue $unit"
        }
        HabitType.TIMER -> "$targetValue min"
    }
}

data class HabitLog(
    val habitId: String,
    val date: String,
    val done: Boolean,
    val progressValue: Int = if (done) 1 else 0,
    val updatedAt: Long = 0L,
    val completedAtMs: Long? = null,
    val tiny: Boolean = false
)

data class HabitReminder(
    val id: String,
    val habitId: String,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean = true
)

data class DayNote(
    val date: String,
    val note: String,
    val updatedAt: Long = 0L,
    val mood: Int? = null
)

data class SkipDay(
    val date: String,
    val reason: String = "Vacation / Rest day"
)

data class StreakFreeze(
    val date: String,
    val appliedAt: Long = System.currentTimeMillis()
)

@Serializable
data class Quote(
    val text: String,
    val book: String,
    val author: String
)

enum class TimeRange(val label: String, val days: Long?) {
    DAYS_7("7 Days", 7L),
    DAYS_30("30 Days", 30L),
    ALL_TIME("All Time", null)
}

enum class DayCompletionStatus {
    NONE,
    PARTIAL,
    FULL,
    SKIPPED,
    FROZEN,
    FUTURE
}

data class DayCompletion(
    val date: LocalDate,
    val scheduledCount: Int,
    val completedCount: Int,
    val isSkipped: Boolean = false,
    val isFrozen: Boolean = false
) {
    val ratio: Float
        get() = if (scheduledCount == 0) 0f else completedCount.toFloat() / scheduledCount.toFloat()

    val percent: Int
        get() = (ratio * 100f).toInt().coerceIn(0, 100)

    fun status(today: LocalDate): DayCompletionStatus {
        if (date.isAfter(today)) return DayCompletionStatus.FUTURE
        if (isSkipped) return DayCompletionStatus.SKIPPED
        if (scheduledCount == 0) return DayCompletionStatus.NONE
        if (completedCount >= scheduledCount) return DayCompletionStatus.FULL
        if (isFrozen) return DayCompletionStatus.FROZEN
        if (completedCount == 0) return DayCompletionStatus.NONE
        return DayCompletionStatus.PARTIAL
    }
}

data class HabitStats(
    val habit: Habit,
    val currentStreak: Int,
    val completionPercent: Int,
    val completedDays: Int,
    val scheduledDays: Int
)

data class DailySheetRow(
    val date: LocalDate,
    val doneCount: Int,
    val totalCount: Int,
    val percent: Int,
    val note: String,
    val isSkipped: Boolean = false,
    val isFrozen: Boolean = false,
    val mood: Int? = null
)

data class LocalInsights(
    val bestWeekdayLabel: String,
    val bestWeekdayPercent: Int,
    val mostMissedHabitName: String?,
    val mostMissedPercent: Int?,
    val weeklyCompleted: Int,
    val weeklyScheduled: Int,
    val weeklyPercent: Int,
    val weeklyDeltaPercent: Int,
    val moodInsightText: String? = null
)

data class SuggestedHabit(
    val name: String,
    val icon: String,
    val colorHex: Long,
    val repeatDays: Int = RepeatDays.ALL,
    val type: HabitType = HabitType.YES_NO,
    val targetValue: Int = 1,
    val unitLabel: String = "",
    val timeOfDay: TimeOfDay = TimeOfDay.ANYTIME,
    val category: HabitCategory = HabitCategory.GENERAL,
    val cueText: String = "",
    val tinyVersion: String = ""
)

data class StarterPack(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: String,
    val category: HabitCategory,
    val habits: List<SuggestedHabit>
)

object StarterPacks {
    val packs: List<StarterPack> = listOf(
        StarterPack(
            id = "fitness",
            title = "Fitness",
            subtitle = "Hydration, daily movement & strength",
            icon = "💪",
            category = HabitCategory.FITNESS,
            habits = listOf(
                SuggestedHabit(
                    name = "Drink water",
                    icon = "💧",
                    colorHex = 0xFF0EA5E9,
                    type = HabitType.COUNT,
                    targetValue = 8,
                    unitLabel = "glasses",
                    timeOfDay = TimeOfDay.MORNING,
                    category = HabitCategory.HEALTH
                ),
                SuggestedHabit(
                    name = "Morning stretch",
                    icon = "🧘",
                    colorHex = 0xFF10B981,
                    type = HabitType.TIMER,
                    targetValue = 10,
                    unitLabel = "min",
                    timeOfDay = TimeOfDay.MORNING,
                    category = HabitCategory.FITNESS
                ),
                SuggestedHabit(
                    name = "Workout",
                    icon = "🏃",
                    colorHex = 0xFF0D9488,
                    type = HabitType.TIMER,
                    targetValue = 30,
                    unitLabel = "min",
                    timeOfDay = TimeOfDay.AFTERNOON,
                    category = HabitCategory.FITNESS
                )
            )
        ),
        StarterPack(
            id = "study",
            title = "Study",
            subtitle = "Deep focus, reading & active recall",
            icon = "🎓",
            category = HabitCategory.LEARNING,
            habits = listOf(
                SuggestedHabit(
                    name = "Deep focus session",
                    icon = "🎯",
                    colorHex = 0xFF6366F1,
                    type = HabitType.TIMER,
                    targetValue = 25,
                    unitLabel = "min",
                    timeOfDay = TimeOfDay.MORNING,
                    category = HabitCategory.LEARNING
                ),
                SuggestedHabit(
                    name = "Read pages",
                    icon = "📖",
                    colorHex = 0xFFF59E0B,
                    type = HabitType.COUNT,
                    targetValue = 15,
                    unitLabel = "pages",
                    timeOfDay = TimeOfDay.EVENING,
                    category = HabitCategory.LEARNING
                ),
                SuggestedHabit(
                    name = "Review daily notes",
                    icon = "✍️",
                    colorHex = 0xFF8B5CF6,
                    type = HabitType.YES_NO,
                    targetValue = 1,
                    timeOfDay = TimeOfDay.EVENING,
                    category = HabitCategory.PRODUCTIVITY
                )
            )
        ),
        StarterPack(
            id = "sleep",
            title = "Sleep",
            subtitle = "Calm evening wind-down & rest",
            icon = "😴",
            category = HabitCategory.SLEEP,
            habits = listOf(
                SuggestedHabit(
                    name = "Evening wind-down",
                    icon = "🌿",
                    colorHex = 0xFF14B8A6,
                    type = HabitType.TIMER,
                    targetValue = 15,
                    unitLabel = "min",
                    timeOfDay = TimeOfDay.EVENING,
                    category = HabitCategory.MINDFULNESS
                ),
                SuggestedHabit(
                    name = "Screens off 30m early",
                    icon = "🌙",
                    colorHex = 0xFF6366F1,
                    type = HabitType.YES_NO,
                    targetValue = 1,
                    timeOfDay = TimeOfDay.EVENING,
                    category = HabitCategory.SLEEP
                ),
                SuggestedHabit(
                    name = "Sleep before 11 PM",
                    icon = "😴",
                    colorHex = 0xFF0D9488,
                    type = HabitType.YES_NO,
                    targetValue = 1,
                    timeOfDay = TimeOfDay.EVENING,
                    category = HabitCategory.SLEEP,
                    cueText = "When my 10:30 PM wind-down cue chimes",
                    tinyVersion = "Get into bed with lights dimmed"
                )
            )
        ),
        StarterPack(
            id = "deep_work",
            title = "Deep Work",
            subtitle = "Distraction-free blocks & daily shutdown",
            icon = "🎯",
            category = HabitCategory.PRODUCTIVITY,
            habits = listOf(
                SuggestedHabit(
                    name = "90m Deep focus",
                    icon = "🎯",
                    colorHex = 0xFF6366F1,
                    type = HabitType.TIMER,
                    targetValue = 90,
                    unitLabel = "min",
                    timeOfDay = TimeOfDay.MORNING,
                    category = HabitCategory.PRODUCTIVITY,
                    cueText = "After I sit at my desk with coffee",
                    tinyVersion = "10 minutes on my #1 priority"
                ),
                SuggestedHabit(
                    name = "Phone in another room",
                    icon = "📵",
                    colorHex = 0xFF0D9488,
                    type = HabitType.YES_NO,
                    targetValue = 1,
                    timeOfDay = TimeOfDay.MORNING,
                    category = HabitCategory.PRODUCTIVITY,
                    tinyVersion = "Turn on Do Not Disturb for 15 min"
                ),
                SuggestedHabit(
                    name = "Daily shutdown note",
                    icon = "✍️",
                    colorHex = 0xFF8B5CF6,
                    type = HabitType.YES_NO,
                    targetValue = 1,
                    timeOfDay = TimeOfDay.EVENING,
                    category = HabitCategory.PRODUCTIVITY,
                    tinyVersion = "Write tomorrow's top 1 task"
                )
            )
        ),
        StarterPack(
            id = "hydration",
            title = "Hydration",
            subtitle = "Morning mineral glass & steady water intake",
            icon = "💧",
            category = HabitCategory.HEALTH,
            habits = listOf(
                SuggestedHabit(
                    name = "Morning glass of water",
                    icon = "💧",
                    colorHex = 0xFF0EA5E9,
                    type = HabitType.YES_NO,
                    targetValue = 1,
                    timeOfDay = TimeOfDay.MORNING,
                    category = HabitCategory.HEALTH,
                    cueText = "Right after waking up",
                    tinyVersion = "Take 3 big sips of water"
                ),
                SuggestedHabit(
                    name = "Daily hydration",
                    icon = "🥤",
                    colorHex = 0xFF14B8A6,
                    type = HabitType.COUNT,
                    targetValue = 8,
                    unitLabel = "glasses",
                    timeOfDay = TimeOfDay.AFTERNOON,
                    category = HabitCategory.HEALTH,
                    tinyVersion = "Refill water bottle once"
                )
            )
        ),
        StarterPack(
            id = "digital_detox",
            title = "Digital Detox",
            subtitle = "Calm mornings & screen-free evenings",
            icon = "🌿",
            category = HabitCategory.MINDFULNESS,
            habits = listOf(
                SuggestedHabit(
                    name = "No scrolling first 30m",
                    icon = "☀️",
                    colorHex = 0xFFF59E0B,
                    type = HabitType.YES_NO,
                    targetValue = 1,
                    timeOfDay = TimeOfDay.MORNING,
                    category = HabitCategory.MINDFULNESS,
                    tinyVersion = "Wait 5 minutes before checking messages"
                ),
                SuggestedHabit(
                    name = "Charge phone outside bedroom",
                    icon = "🌙",
                    colorHex = 0xFF6366F1,
                    type = HabitType.YES_NO,
                    targetValue = 1,
                    timeOfDay = TimeOfDay.EVENING,
                    category = HabitCategory.SLEEP,
                    tinyVersion = "Plug phone across the room"
                ),
                SuggestedHabit(
                    name = "Analog wind-down",
                    icon = "📖",
                    colorHex = 0xFF10B981,
                    type = HabitType.TIMER,
                    targetValue = 15,
                    unitLabel = "min",
                    timeOfDay = TimeOfDay.EVENING,
                    category = HabitCategory.MINDFULNESS,
                    tinyVersion = "Read 1 page of a paperback"
                )
            )
        ),
        StarterPack(
            id = "student_exam_week",
            title = "Student Exam Week",
            subtitle = "Pomodoro recall, practice problems & rest",
            icon = "📚",
            category = HabitCategory.LEARNING,
            habits = listOf(
                SuggestedHabit(
                    name = "Active recall block",
                    icon = "🧠",
                    colorHex = 0xFF8B5CF6,
                    type = HabitType.TIMER,
                    targetValue = 45,
                    unitLabel = "min",
                    timeOfDay = TimeOfDay.MORNING,
                    category = HabitCategory.LEARNING,
                    tinyVersion = "Review 5 flashcards"
                ),
                SuggestedHabit(
                    name = "Practice questions",
                    icon = "✍️",
                    colorHex = 0xFF0EA5E9,
                    type = HabitType.COUNT,
                    targetValue = 10,
                    unitLabel = "problems",
                    timeOfDay = TimeOfDay.AFTERNOON,
                    category = HabitCategory.LEARNING,
                    tinyVersion = "Solve 1 practice problem"
                ),
                SuggestedHabit(
                    name = "Afternoon walk break",
                    icon = "🚶",
                    colorHex = 0xFF10B981,
                    type = HabitType.TIMER,
                    targetValue = 15,
                    unitLabel = "min",
                    timeOfDay = TimeOfDay.AFTERNOON,
                    category = HabitCategory.HEALTH,
                    tinyVersion = "Step outside for 2 minutes"
                )
            )
        ),
        StarterPack(
            id = "gentle_mornings",
            title = "Gentle Mornings",
            subtitle = "Sunlight, breath & unhurried start",
            icon = "🌅",
            category = HabitCategory.MINDFULNESS,
            habits = listOf(
                SuggestedHabit(
                    name = "Morning sunlight",
                    icon = "☀️",
                    colorHex = 0xFFF59E0B,
                    type = HabitType.TIMER,
                    targetValue = 10,
                    unitLabel = "min",
                    timeOfDay = TimeOfDay.MORNING,
                    category = HabitCategory.HEALTH,
                    tinyVersion = "Open curtains & look outside for 1 min"
                ),
                SuggestedHabit(
                    name = "3 calm breaths",
                    icon = "🌿",
                    colorHex = 0xFF14B8A6,
                    type = HabitType.YES_NO,
                    targetValue = 1,
                    timeOfDay = TimeOfDay.MORNING,
                    category = HabitCategory.MINDFULNESS,
                    cueText = "Before unlocking my phone",
                    tinyVersion = "Take 1 slow exhale"
                ),
                SuggestedHabit(
                    name = "1-line gratitude",
                    icon = "✨",
                    colorHex = 0xFFEC4899,
                    type = HabitType.YES_NO,
                    targetValue = 1,
                    timeOfDay = TimeOfDay.MORNING,
                    category = HabitCategory.MINDFULNESS,
                    tinyVersion = "Think of 1 thing I appreciate"
                )
            )
        )
    )
}

object SuggestedHabits {
    val items: List<SuggestedHabit> = listOf(
        SuggestedHabit(
            name = "Drink water",
            icon = "💧",
            colorHex = 0xFF0EA5E9,
            type = HabitType.COUNT,
            targetValue = 8,
            unitLabel = "glasses",
            timeOfDay = TimeOfDay.MORNING,
            category = HabitCategory.HEALTH
        ),
        SuggestedHabit(
            name = "Walk 20 minutes",
            icon = "🚶",
            colorHex = 0xFF10B981,
            type = HabitType.TIMER,
            targetValue = 20,
            unitLabel = "min",
            timeOfDay = TimeOfDay.AFTERNOON,
            category = HabitCategory.FITNESS
        ),
        SuggestedHabit(
            name = "Read 10 pages",
            icon = "📖",
            colorHex = 0xFFF59E0B,
            type = HabitType.COUNT,
            targetValue = 10,
            unitLabel = "pages",
            timeOfDay = TimeOfDay.EVENING,
            category = HabitCategory.LEARNING
        ),
        SuggestedHabit(
            name = "Meditate",
            icon = "🧘",
            colorHex = 0xFF14B8A6,
            type = HabitType.TIMER,
            targetValue = 10,
            unitLabel = "min",
            timeOfDay = TimeOfDay.MORNING,
            category = HabitCategory.MINDFULNESS
        ),
        SuggestedHabit(
            name = "Sleep before 11 PM",
            icon = "😴",
            colorHex = 0xFF6366F1,
            type = HabitType.YES_NO,
            targetValue = 1,
            timeOfDay = TimeOfDay.EVENING,
            category = HabitCategory.SLEEP
        )
    )
}

object HabitPalette {
    val icons: List<String> = listOf(
        "💧", "🚶", "📖", "🧘",
        "😴", "🏃", "🥗", "✍️",
        "💪", "🧠", "🎯", "☀️",
        "🧹", "🎸", "🌿", "💊"
    )

    val colors: List<Long> = listOf(
        0xFF0D9488,
        0xFF10B981,
        0xFF0EA5E9,
        0xFF6366F1,
        0xFFF59E0B,
        0xFFEC4899,
        0xFF8B5CF6,
        0xFF14B8A6
    )
}
