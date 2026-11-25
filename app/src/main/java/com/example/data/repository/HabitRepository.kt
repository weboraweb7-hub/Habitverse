package com.example.data.repository

import android.content.Context
import com.example.data.local.BadgeUnlockedDao
import com.example.data.local.BadgeUnlockedEntity
import com.example.data.local.DailyRewardDao
import com.example.data.local.DailyRewardEntity
import com.example.data.local.DayNoteDao
import com.example.data.local.DayNoteEntity
import com.example.data.local.HabitDao
import com.example.data.local.HabitEntity
import com.example.data.local.HabitLogDao
import com.example.data.local.HabitLogEntity
import com.example.data.local.HabitReminderDao
import com.example.data.local.HabitReminderEntity
import com.example.data.local.SkipDayDao
import com.example.data.local.SkipDayEntity
import com.example.data.local.StreakFreezeDao
import com.example.data.local.StreakFreezeEntity
import com.example.data.local.toDomain
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
import com.example.domain.model.SuggestedHabit
import com.example.domain.model.TimeOfDay
import com.example.domain.model.UnlockedBadge
import com.example.widget.HabitWorksWidgetUpdater
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class ReminderSlotInput(
    val hour: Int,
    val minute: Int
)

@Singleton
class HabitRepository @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val habitDao: HabitDao,
    private val habitLogDao: HabitLogDao,
    private val dayNoteDao: DayNoteDao,
    private val habitReminderDao: HabitReminderDao,
    private val skipDayDao: SkipDayDao,
    private val streakFreezeDao: StreakFreezeDao,
    private val dailyRewardDao: DailyRewardDao,
    private val badgeUnlockedDao: BadgeUnlockedDao
) {
    fun observeAllHabits(): Flow<List<Habit>> =
        habitDao.observeAllHabits().map { list -> list.map { it.toDomain() } }

    fun observeActiveHabits(): Flow<List<Habit>> =
        habitDao.observeActiveHabits().map { list -> list.map { it.toDomain() } }

    fun observeAllLogs(): Flow<List<HabitLog>> =
        habitLogDao.observeAllLogs().map { list -> list.map { it.toDomain() } }

    fun observeAllNotes(): Flow<List<DayNote>> =
        dayNoteDao.observeAllNotes().map { list -> list.map { it.toDomain() } }

    fun observeAllReminders(): Flow<List<HabitReminder>> =
        habitReminderDao.observeAllReminders().map { list -> list.map { it.toDomain() } }

    fun observeSkipDays(): Flow<List<SkipDay>> =
        skipDayDao.observeAllSkipDays().map { list -> list.map { it.toDomain() } }

    fun observeStreakFreezes(): Flow<List<StreakFreeze>> =
        streakFreezeDao.observeAllFreezes().map { list -> list.map { it.toDomain() } }

    fun observeAllDailyRewards(): Flow<List<DailyReward>> =
        dailyRewardDao.observeAllRewards().map { list -> list.map { it.toDomain() } }

    fun observeAllUnlockedBadges(): Flow<List<UnlockedBadge>> =
        badgeUnlockedDao.observeAllBadges().map { list -> list.map { it.toDomain() } }

    suspend fun getRemindersForHabit(habitId: String): List<HabitReminder> =
        habitReminderDao.getForHabitOnce(habitId).map { it.toDomain() }

    suspend fun getAllRemindersOnce(): List<HabitReminder> =
        habitReminderDao.getAllRemindersOnce().map { it.toDomain() }

    suspend fun getHabitById(habitId: String): Habit? =
        habitDao.getById(habitId)?.toDomain()

    suspend fun addHabit(
        name: String,
        icon: String,
        colorHex: Long,
        repeatDays: Int,
        type: HabitType = HabitType.YES_NO,
        targetValue: Int = 1,
        unitLabel: String = "",
        timeOfDay: TimeOfDay = TimeOfDay.ANYTIME,
        category: HabitCategory = HabitCategory.GENERAL,
        cueText: String = "",
        tinyVersion: String = "",
        stackAfterHabitId: String? = null,
        challengeDays: Int = 0,
        challengeStart: String? = null,
        reminders: List<ReminderSlotInput> = emptyList(),
        createdDate: LocalDate = LocalDate.now()
    ): Pair<Habit, List<HabitReminder>> {
        val nextSortOrder = habitDao.maxSortOrder() + 1
        val now = System.currentTimeMillis()
        val habitId = UUID.randomUUID().toString()
        val cleanTarget = if (type == HabitType.YES_NO) 1 else targetValue.coerceAtLeast(1)
        val primaryReminder = reminders.firstOrNull()?.let {
            String.format(Locale.US, "%02d:%02d", it.hour, it.minute)
        }
        val cleanChallengeDays = challengeDays.coerceAtLeast(0)
        val effectiveChallengeStart = if (cleanChallengeDays > 0) {
            challengeStart ?: createdDate.toString()
        } else {
            null
        }
        val entity = HabitEntity(
            id = habitId,
            name = name.trim().take(80),
            icon = icon,
            colorHex = colorHex,
            repeatDays = repeatDays,
            reminderTime = primaryReminder,
            createdAt = createdDate.toString(),
            archivedAt = null,
            sortOrder = nextSortOrder,
            updatedAt = now,
            type = type.name,
            targetValue = cleanTarget,
            unitLabel = unitLabel.trim().take(24),
            timeOfDay = timeOfDay.name,
            category = category.name,
            cueText = cueText.trim().take(120),
            tinyVersion = tinyVersion.trim().take(100),
            stackAfterHabitId = stackAfterHabitId?.takeIf { it.isNotBlank() && it != habitId },
            challengeDays = cleanChallengeDays,
            challengeStart = effectiveChallengeStart
        )
        habitDao.upsert(entity)
        val savedReminders = saveRemindersForHabit(habitId, reminders)
        HabitWorksWidgetUpdater.updateAll(appContext)
        return entity.toDomain() to savedReminders
    }

    suspend fun updateHabit(
        habitId: String,
        name: String,
        icon: String,
        colorHex: Long,
        repeatDays: Int,
        type: HabitType,
        targetValue: Int,
        unitLabel: String,
        timeOfDay: TimeOfDay,
        category: HabitCategory,
        cueText: String = "",
        tinyVersion: String = "",
        stackAfterHabitId: String? = null,
        challengeDays: Int = 0,
        reminders: List<ReminderSlotInput>
    ): List<HabitReminder> {
        val existing = habitDao.getById(habitId) ?: return emptyList()
        val cleanTarget = if (type == HabitType.YES_NO) 1 else targetValue.coerceAtLeast(1)
        val primaryReminder = reminders.firstOrNull()?.let {
            String.format(Locale.US, "%02d:%02d", it.hour, it.minute)
        }
        val cleanChallengeDays = challengeDays.coerceAtLeast(0)
        val nextChallengeStart = when {
            cleanChallengeDays <= 0 -> null
            existing.challengeDays == cleanChallengeDays && !existing.challengeStart.isNullOrBlank() -> existing.challengeStart
            else -> LocalDate.now().toString()
        }
        val validStackId = validateStackTarget(habitId, stackAfterHabitId)
        val updated = existing.copy(
            name = name.trim().take(80),
            icon = icon,
            colorHex = colorHex,
            repeatDays = repeatDays,
            reminderTime = primaryReminder,
            updatedAt = System.currentTimeMillis(),
            type = type.name,
            targetValue = cleanTarget,
            unitLabel = unitLabel.trim().take(24),
            timeOfDay = timeOfDay.name,
            category = category.name,
            cueText = cueText.trim().take(120),
            tinyVersion = tinyVersion.trim().take(100),
            stackAfterHabitId = validStackId,
            challengeDays = cleanChallengeDays,
            challengeStart = nextChallengeStart
        )
        habitDao.upsert(updated)
        val savedReminders = saveRemindersForHabit(habitId, reminders)
        HabitWorksWidgetUpdater.updateAll(appContext)
        return savedReminders
    }

    private suspend fun validateStackTarget(habitId: String, targetId: String?): String? {
        if (targetId.isNullOrBlank() || targetId == habitId) return null
        val allById = habitDao.getAllHabitsOnce().associateBy { it.id }
        var cursor: String? = targetId
        val visited = mutableSetOf(habitId)
        while (cursor != null) {
            if (!visited.add(cursor)) return null // Cycle detected
            cursor = allById[cursor]?.stackAfterHabitId
        }
        return targetId
    }

    suspend fun updateHabitReminderTimeSingle(habitId: String, hour: Int, minute: Int): List<HabitReminder> {
        val slot = ReminderSlotInput(hour.coerceIn(0, 23), minute.coerceIn(0, 59))
        val formatted = String.format(Locale.US, "%02d:%02d", slot.hour, slot.minute)
        habitDao.setReminderTime(habitId, formatted, System.currentTimeMillis())
        return saveRemindersForHabit(habitId, listOf(slot))
    }

    suspend fun reorderHabits(orderedIds: List<String>) {
        val now = System.currentTimeMillis()
        orderedIds.forEachIndexed { idx, id ->
            habitDao.setSortOrder(id, idx, now)
        }
        HabitWorksWidgetUpdater.updateAll(appContext)
    }

    suspend fun duplicateHabit(habit: Habit, createdDate: LocalDate = LocalDate.now()): Pair<Habit, List<HabitReminder>> {
        val reminders = getRemindersForHabit(habit.id).map { ReminderSlotInput(it.hour, it.minute) }
        return addHabit(
            name = "${habit.name} (copy)".take(80),
            icon = habit.icon,
            colorHex = habit.colorHex,
            repeatDays = habit.repeatDays,
            type = habit.type,
            targetValue = habit.targetValue,
            unitLabel = habit.unitLabel,
            timeOfDay = habit.timeOfDay,
            category = habit.category,
            cueText = habit.cueText,
            tinyVersion = habit.tinyVersion,
            stackAfterHabitId = null,
            challengeDays = habit.challengeDays,
            reminders = reminders,
            createdDate = createdDate
        )
    }

    private suspend fun saveRemindersForHabit(
        habitId: String,
        slots: List<ReminderSlotInput>
    ): List<HabitReminder> {
        habitReminderDao.deleteForHabit(habitId)
        val uniqueSlots = slots.distinctBy { it.hour to it.minute }
        val entities = uniqueSlots.map { slot ->
            HabitReminderEntity(
                id = UUID.randomUUID().toString(),
                habitId = habitId,
                hour = slot.hour.coerceIn(0, 23),
                minute = slot.minute.coerceIn(0, 59),
                enabled = true
            )
        }
        if (entities.isNotEmpty()) {
            habitReminderDao.upsertAll(entities)
        }
        return entities.map { it.toDomain() }
    }

    suspend fun addStarterPack(
        suggestions: List<SuggestedHabit>,
        createdDate: LocalDate = LocalDate.now()
    ): Int {
        val existingNames = habitDao.getActiveHabitsOnce().map { it.name.lowercase() }.toSet()
        var nextSortOrder = habitDao.maxSortOrder() + 1
        val now = System.currentTimeMillis()
        val toInsert = suggestions
            .filter { it.name.lowercase() !in existingNames }
            .map { s ->
                HabitEntity(
                    id = UUID.randomUUID().toString(),
                    name = s.name,
                    icon = s.icon,
                    colorHex = s.colorHex,
                    repeatDays = s.repeatDays,
                    reminderTime = null,
                    createdAt = createdDate.toString(),
                    archivedAt = null,
                    sortOrder = nextSortOrder++,
                    updatedAt = now,
                    type = s.type.name,
                    targetValue = s.targetValue,
                    unitLabel = s.unitLabel,
                    timeOfDay = s.timeOfDay.name,
                    category = s.category.name,
                    cueText = s.cueText,
                    tinyVersion = s.tinyVersion
                )
            }
        if (toInsert.isNotEmpty()) {
            habitDao.upsertAll(toInsert)
            HabitWorksWidgetUpdater.updateAll(appContext)
        }
        return toInsert.size
    }

    suspend fun archiveHabit(habitId: String, archivedDate: LocalDate = LocalDate.now()) {
        habitDao.setArchivedAt(
            id = habitId,
            archivedAt = archivedDate.toString(),
            updatedAt = System.currentTimeMillis()
        )
        HabitWorksWidgetUpdater.updateAll(appContext)
    }

    suspend fun restoreHabit(habitId: String) {
        habitDao.setArchivedAt(
            id = habitId,
            archivedAt = null,
            updatedAt = System.currentTimeMillis()
        )
        HabitWorksWidgetUpdater.updateAll(appContext)
    }

    suspend fun toggleHabitLog(habitId: String, date: LocalDate) {
        val habit = habitDao.getById(habitId)?.toDomain() ?: return
        val dateStr = date.toString()
        val current = habitLogDao.getLog(habitId, dateStr)
        val wasDone = current?.done ?: false
        val nextDone = !wasDone
        val now = System.currentTimeMillis()
        val nextProgress = if (nextDone) habit.effectiveTargetProgress else 0
        val nextCompletedAt = if (nextDone) now else null
        habitLogDao.upsert(
            HabitLogEntity(
                id = "${dateStr}_${habitId}",
                habitId = habitId,
                date = dateStr,
                done = nextDone,
                progressValue = nextProgress,
                updatedAt = now,
                completedAtMs = nextCompletedAt,
                tiny = false
            )
        )
        HabitWorksWidgetUpdater.updateAll(appContext)
    }

    suspend fun markHabitDone(habitId: String, date: LocalDate) {
        val habit = habitDao.getById(habitId)?.toDomain() ?: return
        val dateStr = date.toString()
        val current = habitLogDao.getLog(habitId, dateStr)
        val now = System.currentTimeMillis()
        val completedAt = if (current?.done == true) current.completedAtMs ?: now else now
        habitLogDao.upsert(
            HabitLogEntity(
                id = "${dateStr}_${habitId}",
                habitId = habitId,
                date = dateStr,
                done = true,
                progressValue = habit.effectiveTargetProgress,
                updatedAt = now,
                completedAtMs = completedAt,
                tiny = false
            )
        )
        HabitWorksWidgetUpdater.updateAll(appContext)
    }

    suspend fun markHabitTinyDone(habitId: String, date: LocalDate) {
        val habit = habitDao.getById(habitId)?.toDomain() ?: return
        val dateStr = date.toString()
        val current = habitLogDao.getLog(habitId, dateStr)
        val now = System.currentTimeMillis()
        val completedAt = if (current?.done == true) current.completedAtMs ?: now else now
        habitLogDao.upsert(
            HabitLogEntity(
                id = "${dateStr}_${habitId}",
                habitId = habitId,
                date = dateStr,
                done = true,
                progressValue = current?.progressValue?.coerceAtLeast(1) ?: 1.coerceAtMost(habit.effectiveTargetProgress),
                updatedAt = now,
                completedAtMs = completedAt,
                tiny = true
            )
        )
        HabitWorksWidgetUpdater.updateAll(appContext)
    }

    /**
     * Updates progress for COUNT or TIMER habits.
     * Preserves `completedAtMs` rules strictly:
     * - Set `completedAtMs` ONLY when transitioning not-done -> done.
     * - Clear `completedAtMs` when transitioning done -> not-done.
     * - Never overwrite `completedAtMs` during timer ticks!
     */
    suspend fun updateHabitProgress(habitId: String, date: LocalDate, newProgress: Int) {
        val habit = habitDao.getById(habitId)?.toDomain() ?: return
        val dateStr = date.toString()
        val current = habitLogDao.getLog(habitId, dateStr)
        val wasDone = current?.done ?: false
        val clamped = newProgress.coerceAtLeast(0)
        val isDone = clamped >= habit.effectiveTargetProgress
        val now = System.currentTimeMillis()
        val nextCompletedAt = when {
            !wasDone && isDone -> now
            wasDone && !isDone -> null
            wasDone && isDone -> current?.completedAtMs
            else -> null
        }
        val wasTiny = current?.tiny ?: false
        habitLogDao.upsert(
            HabitLogEntity(
                id = "${dateStr}_${habitId}",
                habitId = habitId,
                date = dateStr,
                done = isDone,
                progressValue = clamped,
                updatedAt = now,
                completedAtMs = nextCompletedAt,
                tiny = if (isDone && wasTiny && clamped < habit.effectiveTargetProgress) true else false
            )
        )
        HabitWorksWidgetUpdater.updateAll(appContext)
    }

    suspend fun saveDayNote(date: LocalDate, note: String) {
        val existing = dayNoteDao.getByDate(date.toString())
        dayNoteDao.upsert(
            DayNoteEntity(
                date = date.toString(),
                note = note.trim().take(280),
                updatedAt = System.currentTimeMillis(),
                mood = existing?.mood
            )
        )
    }

    suspend fun saveDayMood(date: LocalDate, mood: Int) {
        val clampedMood = mood.coerceIn(1, 5)
        val existing = dayNoteDao.getByDate(date.toString())
        dayNoteDao.upsert(
            DayNoteEntity(
                date = date.toString(),
                note = existing?.note.orEmpty(),
                updatedAt = System.currentTimeMillis(),
                mood = clampedMood
            )
        )
    }

    suspend fun toggleSkipDay(date: LocalDate, reason: String = "Vacation / Rest day") {
        val key = date.toString()
        val existing = skipDayDao.getAllSkipDaysOnce().any { it.date == key }
        if (existing) {
            skipDayDao.deleteByDate(key)
        } else {
            skipDayDao.upsert(SkipDayEntity(date = key, reason = reason))
        }
        HabitWorksWidgetUpdater.updateAll(appContext)
    }

    suspend fun setVacationRange(startDate: LocalDate, daysCount: Int, reason: String = "Vacation mode") {
        val entities = (0 until daysCount.coerceAtLeast(1)).map { offset ->
            SkipDayEntity(
                date = startDate.plusDays(offset.toLong()).toString(),
                reason = reason
            )
        }
        skipDayDao.upsertAll(entities)
        HabitWorksWidgetUpdater.updateAll(appContext)
    }

    suspend fun clearSkipDay(date: LocalDate) {
        skipDayDao.deleteByDate(date.toString())
        HabitWorksWidgetUpdater.updateAll(appContext)
    }

    suspend fun applyStreakFreeze(date: LocalDate) {
        streakFreezeDao.upsert(
            StreakFreezeEntity(
                date = date.toString(),
                appliedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun getDailyRewardByDate(date: LocalDate): DailyReward? =
        dailyRewardDao.getByDate(date.toString())?.toDomain()

    suspend fun saveDailyReward(date: LocalDate, rewardType: RewardType, rewardValue: Int, openedAtMs: Long) {
        dailyRewardDao.upsert(
            DailyRewardEntity(
                date = date.toString(),
                rewardType = rewardType.name,
                rewardValue = rewardValue,
                openedAtMs = openedAtMs
            )
        )
    }

    suspend fun unlockBadgesIfNew(badgeIds: List<String>, date: LocalDate): List<String> {
        if (badgeIds.isEmpty()) return emptyList()
        val existing = badgeUnlockedDao.getAllBadgesOnce().map { it.badgeId }.toSet()
        val newlyUnlocked = badgeIds.distinct().filter { it !in existing }
        if (newlyUnlocked.isNotEmpty()) {
            val dateStr = date.toString()
            badgeUnlockedDao.insertAllIgnore(
                newlyUnlocked.map { BadgeUnlockedEntity(badgeId = it, unlockedAt = dateStr) }
            )
        }
        return newlyUnlocked
    }
}
