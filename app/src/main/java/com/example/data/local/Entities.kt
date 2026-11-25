package com.example.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.DailyReward
import com.example.domain.model.DayNote
import com.example.domain.model.Habit
import com.example.domain.model.HabitCategory
import com.example.domain.model.HabitLog
import com.example.domain.model.HabitReminder
import com.example.domain.model.HabitType
import com.example.domain.model.RewardType
import com.example.domain.model.SkipDay
import com.example.domain.model.StreakFreeze
import com.example.domain.model.TimeOfDay
import com.example.domain.model.UnlockedBadge

@Entity(tableName = "habits")
data class HabitEntity(
    @PrimaryKey val id: String,
    val name: String,
    val icon: String,
    val colorHex: Long,
    val repeatDays: Int,
    val reminderTime: String? = null,
    val createdAt: String,
    val archivedAt: String? = null,
    val sortOrder: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
    val type: String = HabitType.YES_NO.name,
    val targetValue: Int = 1,
    val unitLabel: String = "",
    val timeOfDay: String = TimeOfDay.ANYTIME.name,
    val category: String = HabitCategory.GENERAL.name,
    val cueText: String = "",
    val tinyVersion: String = "",
    val stackAfterHabitId: String? = null,
    val challengeDays: Int = 0,
    val challengeStart: String? = null
)

@Entity(
    tableName = "habit_logs",
    indices = [Index(value = ["habitId", "date"], unique = true)]
)
data class HabitLogEntity(
    @PrimaryKey val id: String,
    val habitId: String,
    val date: String,
    val done: Boolean,
    val progressValue: Int = 0,
    val updatedAt: Long = System.currentTimeMillis(),
    val completedAtMs: Long? = null,
    val tiny: Boolean = false
)

@Entity(tableName = "day_notes")
data class DayNoteEntity(
    @PrimaryKey val date: String,
    val note: String,
    val updatedAt: Long = System.currentTimeMillis(),
    val mood: Int? = null
)

@Entity(
    tableName = "habit_reminders",
    indices = [Index(value = ["habitId"])]
)
data class HabitReminderEntity(
    @PrimaryKey val id: String,
    val habitId: String,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean = true
)

@Entity(tableName = "skip_days")
data class SkipDayEntity(
    @PrimaryKey val date: String,
    val reason: String = "Vacation / Rest day"
)

@Entity(tableName = "streak_freezes")
data class StreakFreezeEntity(
    @PrimaryKey val date: String,
    val appliedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "daily_rewards")
data class DailyRewardEntity(
    @PrimaryKey val date: String,
    val rewardType: String,
    val rewardValue: Int,
    val openedAtMs: Long? = null
)

@Entity(tableName = "badges_unlocked")
data class BadgeUnlockedEntity(
    @PrimaryKey val badgeId: String,
    val unlockedAt: String
)

fun HabitEntity.toDomain(): Habit = Habit(
    id = id,
    name = name,
    icon = icon,
    colorHex = colorHex,
    repeatDays = repeatDays,
    reminderTime = reminderTime,
    createdAt = createdAt,
    archivedAt = archivedAt,
    sortOrder = sortOrder,
    updatedAt = updatedAt,
    type = HabitType.fromName(type),
    targetValue = targetValue,
    unitLabel = unitLabel,
    timeOfDay = TimeOfDay.fromName(timeOfDay),
    category = HabitCategory.fromName(category),
    cueText = cueText,
    tinyVersion = tinyVersion,
    stackAfterHabitId = stackAfterHabitId,
    challengeDays = challengeDays,
    challengeStart = challengeStart
)

fun Habit.toEntity(): HabitEntity = HabitEntity(
    id = id,
    name = name,
    icon = icon,
    colorHex = colorHex,
    repeatDays = repeatDays,
    reminderTime = reminderTime,
    createdAt = createdAt,
    archivedAt = archivedAt,
    sortOrder = sortOrder,
    updatedAt = updatedAt,
    type = type.name,
    targetValue = targetValue,
    unitLabel = unitLabel,
    timeOfDay = timeOfDay.name,
    category = category.name,
    cueText = cueText,
    tinyVersion = tinyVersion,
    stackAfterHabitId = stackAfterHabitId,
    challengeDays = challengeDays,
    challengeStart = challengeStart
)

fun HabitLogEntity.toDomain(): HabitLog = HabitLog(
    habitId = habitId,
    date = date,
    done = done,
    progressValue = progressValue,
    updatedAt = updatedAt,
    completedAtMs = completedAtMs,
    tiny = tiny
)

fun HabitReminderEntity.toDomain(): HabitReminder = HabitReminder(
    id = id,
    habitId = habitId,
    hour = hour,
    minute = minute,
    enabled = enabled
)

fun DayNoteEntity.toDomain(): DayNote = DayNote(
    date = date,
    note = note,
    updatedAt = updatedAt,
    mood = mood
)

fun SkipDayEntity.toDomain(): SkipDay = SkipDay(
    date = date,
    reason = reason
)

fun StreakFreezeEntity.toDomain(): StreakFreeze = StreakFreeze(
    date = date,
    appliedAt = appliedAt
)

fun DailyRewardEntity.toDomain(): DailyReward = DailyReward(
    date = date,
    rewardType = RewardType.fromName(rewardType),
    rewardValue = rewardValue,
    openedAtMs = openedAtMs
)

fun BadgeUnlockedEntity.toDomain(): UnlockedBadge = UnlockedBadge(
    badgeId = badgeId,
    unlockedAt = unlockedAt
)
