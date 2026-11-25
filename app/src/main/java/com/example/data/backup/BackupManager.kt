package com.example.data.backup

import androidx.room.withTransaction
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
import com.example.data.local.HabitWorksDatabase
import com.example.data.local.SkipDayDao
import com.example.data.local.SkipDayEntity
import com.example.data.local.StreakFreezeDao
import com.example.data.local.StreakFreezeEntity
import com.example.data.prefs.UserPreferencesRepository
import com.example.domain.model.FlameSkin
import com.example.domain.model.HabitCategory
import com.example.domain.model.HabitType
import com.example.domain.model.RewardType
import com.example.domain.model.TimeOfDay
import com.example.ui.theme.AppThemeOption
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class BackupPrefsSnapshot(
    val firstName: String = "",
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 8,
    val reminderMinute: Int = 30,
    val streakFreezeTokens: Int = 0,
    val selectedThemeId: String = AppThemeOption.FOREST_TEAL.id,
    val themeUnlockExpiresAtMs: Long = 0L,
    val installSeed: Long = 0L,
    val activeFlameSkinId: String = FlameSkin.CLASSIC.id,
    val unlockedFlameSkinIds: List<String> = listOf(FlameSkin.CLASSIC.id),
    val plantBonusXpTotal: Int = 0,
    val lastPlantBonusDate: String = "",
    val lastAutoShieldStreakMilestone: Int = 0
)

@Serializable
data class BackupHabit(
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

@Serializable
data class BackupHabitLog(
    val id: String,
    val habitId: String,
    val date: String,
    val done: Boolean,
    val progressValue: Int = 0,
    val updatedAt: Long = 0L,
    val completedAtMs: Long? = null,
    val tiny: Boolean = false
)

@Serializable
data class BackupDayNote(
    val date: String,
    val note: String,
    val updatedAt: Long = 0L,
    val mood: Int? = null
)

@Serializable
data class BackupHabitReminder(
    val id: String,
    val habitId: String,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean = true
)

@Serializable
data class BackupSkipDay(
    val date: String,
    val reason: String = "Vacation / Rest day"
)

@Serializable
data class BackupStreakFreeze(
    val date: String,
    val appliedAt: Long = 0L
)

@Serializable
data class BackupDailyReward(
    val date: String,
    val rewardType: String,
    val rewardValue: Int,
    val openedAtMs: Long? = null
)

@Serializable
data class BackupUnlockedBadge(
    val badgeId: String,
    val unlockedAt: String
)

@Serializable
data class HabitWorksBackup(
    val app: String = "HabitWorks",
    val schemaVersion: Int = 3,
    val exportedAtEpochMs: Long = System.currentTimeMillis(),
    val userPrefs: BackupPrefsSnapshot = BackupPrefsSnapshot(),
    val habits: List<BackupHabit> = emptyList(),
    val logs: List<BackupHabitLog> = emptyList(),
    val dayNotes: List<BackupDayNote> = emptyList(),
    val habitReminders: List<BackupHabitReminder> = emptyList(),
    val skipDays: List<BackupSkipDay> = emptyList(),
    val streakFreezes: List<BackupStreakFreeze> = emptyList(),
    val dailyRewards: List<BackupDailyReward> = emptyList(),
    val badgesUnlocked: List<BackupUnlockedBadge> = emptyList()
)

@Singleton
class BackupManager @Inject constructor(
    private val database: HabitWorksDatabase,
    private val habitDao: HabitDao,
    private val habitLogDao: HabitLogDao,
    private val dayNoteDao: DayNoteDao,
    private val habitReminderDao: HabitReminderDao,
    private val skipDayDao: SkipDayDao,
    private val streakFreezeDao: StreakFreezeDao,
    private val dailyRewardDao: DailyRewardDao,
    private val badgeUnlockedDao: BadgeUnlockedDao,
    private val prefsRepository: UserPreferencesRepository,
    private val json: Json
) {
    private val exportJson = Json(json) {
        prettyPrint = true
        encodeDefaults = true
    }

    suspend fun exportToJson(): String {
        val prefs = prefsRepository.userPrefs.first()
        val backup = HabitWorksBackup(
            app = "HabitWorks",
            schemaVersion = 3,
            exportedAtEpochMs = System.currentTimeMillis(),
            userPrefs = BackupPrefsSnapshot(
                firstName = prefs.firstName,
                reminderEnabled = prefs.reminderEnabled,
                reminderHour = prefs.reminderHour,
                reminderMinute = prefs.reminderMinute,
                streakFreezeTokens = prefs.streakFreezeTokens,
                selectedThemeId = prefs.selectedThemeId,
                themeUnlockExpiresAtMs = prefs.themeUnlockExpiresAtMs,
                installSeed = prefs.installSeed,
                activeFlameSkinId = prefs.activeFlameSkinId,
                unlockedFlameSkinIds = prefs.unlockedFlameSkinIds.toList(),
                plantBonusXpTotal = prefs.plantBonusXpTotal,
                lastPlantBonusDate = prefs.lastPlantBonusDate,
                lastAutoShieldStreakMilestone = prefs.lastAutoShieldStreakMilestone
            ),
            habits = habitDao.getAllHabitsOnce().map {
                BackupHabit(
                    id = it.id,
                    name = it.name,
                    icon = it.icon,
                    colorHex = it.colorHex,
                    repeatDays = it.repeatDays,
                    reminderTime = it.reminderTime,
                    createdAt = it.createdAt,
                    archivedAt = it.archivedAt,
                    sortOrder = it.sortOrder,
                    updatedAt = it.updatedAt,
                    type = it.type,
                    targetValue = it.targetValue,
                    unitLabel = it.unitLabel,
                    timeOfDay = it.timeOfDay,
                    category = it.category,
                    cueText = it.cueText,
                    tinyVersion = it.tinyVersion,
                    stackAfterHabitId = it.stackAfterHabitId,
                    challengeDays = it.challengeDays,
                    challengeStart = it.challengeStart
                )
            },
            logs = habitLogDao.getAllLogsOnce().map {
                BackupHabitLog(
                    id = it.id,
                    habitId = it.habitId,
                    date = it.date,
                    done = it.done,
                    progressValue = it.progressValue,
                    updatedAt = it.updatedAt,
                    completedAtMs = it.completedAtMs,
                    tiny = it.tiny
                )
            },
            dayNotes = dayNoteDao.getAllNotesOnce().map {
                BackupDayNote(
                    date = it.date,
                    note = it.note,
                    updatedAt = it.updatedAt,
                    mood = it.mood
                )
            },
            habitReminders = habitReminderDao.getAllRemindersOnce().map {
                BackupHabitReminder(
                    id = it.id,
                    habitId = it.habitId,
                    hour = it.hour,
                    minute = it.minute,
                    enabled = it.enabled
                )
            },
            skipDays = skipDayDao.getAllSkipDaysOnce().map {
                BackupSkipDay(date = it.date, reason = it.reason)
            },
            streakFreezes = streakFreezeDao.getAllFreezesOnce().map {
                BackupStreakFreeze(date = it.date, appliedAt = it.appliedAt)
            },
            dailyRewards = dailyRewardDao.getAllRewardsOnce().map {
                BackupDailyReward(
                    date = it.date,
                    rewardType = it.rewardType,
                    rewardValue = it.rewardValue,
                    openedAtMs = it.openedAtMs
                )
            },
            badgesUnlocked = badgeUnlockedDao.getAllBadgesOnce().map {
                BackupUnlockedBadge(badgeId = it.badgeId, unlockedAt = it.unlockedAt)
            }
        )
        return exportJson.encodeToString(backup)
    }

    fun validateAndParse(rawJson: String): Result<HabitWorksBackup> {
        if (rawJson.isBlank()) {
            return Result.failure(IllegalArgumentException("Backup file is empty."))
        }
        return runCatching {
            val parsed = json.decodeFromString<HabitWorksBackup>(rawJson)
            require(parsed.app == "HabitWorks") {
                "File is not a valid HabitWorks backup."
            }
            require(parsed.schemaVersion in 1..3) {
                "Unsupported backup version: ${parsed.schemaVersion}"
            }
            parsed.habits.forEach { h ->
                require(h.id.isNotBlank()) { "Backup contains a habit with an empty ID." }
                require(h.name.isNotBlank()) { "Backup contains a habit with an empty name." }
                require(h.targetValue >= 1) { "Invalid target value for habit '${h.name}'." }
                LocalDate.parse(h.createdAt)
                if (h.archivedAt != null) LocalDate.parse(h.archivedAt)
                if (h.challengeStart != null) LocalDate.parse(h.challengeStart)
            }
            parsed.logs.forEach { l ->
                require(l.id.isNotBlank() && l.habitId.isNotBlank()) { "Invalid log entry in backup." }
                LocalDate.parse(l.date)
            }
            parsed.dayNotes.forEach { n ->
                LocalDate.parse(n.date)
                if (n.mood != null) require(n.mood in 1..5) { "Invalid mood value in backup." }
            }
            parsed.habitReminders.forEach { r ->
                require(r.id.isNotBlank() && r.habitId.isNotBlank()) { "Invalid reminder entry." }
                require(r.hour in 0..23 && r.minute in 0..59) { "Invalid reminder time." }
            }
            parsed.skipDays.forEach { s -> LocalDate.parse(s.date) }
            parsed.streakFreezes.forEach { f -> LocalDate.parse(f.date) }
            parsed.dailyRewards.forEach { dr -> LocalDate.parse(dr.date) }
            parsed.badgesUnlocked.forEach { b ->
                require(b.badgeId.isNotBlank()) { "Invalid badge entry in backup." }
                LocalDate.parse(b.unlockedAt)
            }
            parsed
        }
    }

    suspend fun restoreFromBackup(backup: HabitWorksBackup) {
        database.withTransaction {
            habitDao.deleteAll()
            habitLogDao.deleteAll()
            dayNoteDao.deleteAll()
            habitReminderDao.deleteAll()
            skipDayDao.deleteAll()
            streakFreezeDao.deleteAll()
            dailyRewardDao.deleteAll()
            badgeUnlockedDao.deleteAll()

            if (backup.habits.isNotEmpty()) {
                habitDao.upsertAll(
                    backup.habits.map {
                        HabitEntity(
                            id = it.id,
                            name = it.name.trim().take(80),
                            icon = it.icon,
                            colorHex = it.colorHex,
                            repeatDays = it.repeatDays,
                            reminderTime = it.reminderTime,
                            createdAt = it.createdAt,
                            archivedAt = it.archivedAt,
                            sortOrder = it.sortOrder,
                            updatedAt = it.updatedAt,
                            type = HabitType.fromName(it.type).name,
                            targetValue = it.targetValue.coerceAtLeast(1),
                            unitLabel = it.unitLabel.take(24),
                            timeOfDay = TimeOfDay.fromName(it.timeOfDay).name,
                            category = HabitCategory.fromName(it.category).name,
                            cueText = it.cueText.take(120),
                            tinyVersion = it.tinyVersion.take(100),
                            stackAfterHabitId = it.stackAfterHabitId,
                            challengeDays = it.challengeDays.coerceAtLeast(0),
                            challengeStart = it.challengeStart
                        )
                    }
                )
            }
            if (backup.logs.isNotEmpty()) {
                habitLogDao.upsertAll(
                    backup.logs.map {
                        HabitLogEntity(
                            id = it.id,
                            habitId = it.habitId,
                            date = it.date,
                            done = it.done,
                            progressValue = it.progressValue.coerceAtLeast(0),
                            updatedAt = it.updatedAt,
                            completedAtMs = it.completedAtMs,
                            tiny = it.tiny
                        )
                    }
                )
            }
            if (backup.dayNotes.isNotEmpty()) {
                dayNoteDao.upsertAll(
                    backup.dayNotes.map {
                        DayNoteEntity(
                            date = it.date,
                            note = it.note.take(280),
                            updatedAt = it.updatedAt,
                            mood = it.mood?.coerceIn(1, 5)
                        )
                    }
                )
            }
            if (backup.habitReminders.isNotEmpty()) {
                habitReminderDao.upsertAll(
                    backup.habitReminders.map {
                        HabitReminderEntity(
                            id = it.id,
                            habitId = it.habitId,
                            hour = it.hour,
                            minute = it.minute,
                            enabled = it.enabled
                        )
                    }
                )
            }
            if (backup.skipDays.isNotEmpty()) {
                skipDayDao.upsertAll(
                    backup.skipDays.map { SkipDayEntity(date = it.date, reason = it.reason) }
                )
            }
            if (backup.streakFreezes.isNotEmpty()) {
                streakFreezeDao.upsertAll(
                    backup.streakFreezes.map { StreakFreezeEntity(date = it.date, appliedAt = it.appliedAt) }
                )
            }
            if (backup.dailyRewards.isNotEmpty()) {
                dailyRewardDao.upsertAll(
                    backup.dailyRewards.map {
                        DailyRewardEntity(
                            date = it.date,
                            rewardType = RewardType.fromName(it.rewardType).name,
                            rewardValue = it.rewardValue,
                            openedAtMs = it.openedAtMs
                        )
                    }
                )
            }
            if (backup.badgesUnlocked.isNotEmpty()) {
                badgeUnlockedDao.upsertAll(
                    backup.badgesUnlocked.map {
                        BadgeUnlockedEntity(badgeId = it.badgeId, unlockedAt = it.unlockedAt)
                    }
                )
            }
        }

        prefsRepository.restoreFromBackup(
            firstName = backup.userPrefs.firstName,
            reminderEnabled = backup.userPrefs.reminderEnabled,
            reminderHour = backup.userPrefs.reminderHour.coerceIn(0, 23),
            reminderMinute = backup.userPrefs.reminderMinute.coerceIn(0, 59),
            streakFreezeTokens = backup.userPrefs.streakFreezeTokens,
            selectedThemeId = AppThemeOption.fromId(backup.userPrefs.selectedThemeId).id,
            themeUnlockExpiresAtMs = backup.userPrefs.themeUnlockExpiresAtMs,
            installSeed = backup.userPrefs.installSeed,
            activeFlameSkinId = backup.userPrefs.activeFlameSkinId,
            unlockedFlameSkinIds = backup.userPrefs.unlockedFlameSkinIds.toSet(),
            plantBonusXpTotal = backup.userPrefs.plantBonusXpTotal,
            lastPlantBonusDate = backup.userPrefs.lastPlantBonusDate,
            lastAutoShieldStreakMilestone = backup.userPrefs.lastAutoShieldStreakMilestone
        )
    }

    suspend fun deleteAllData() {
        database.withTransaction {
            habitDao.deleteAll()
            habitLogDao.deleteAll()
            dayNoteDao.deleteAll()
            habitReminderDao.deleteAll()
            skipDayDao.deleteAll()
            streakFreezeDao.deleteAll()
            dailyRewardDao.deleteAll()
            badgeUnlockedDao.deleteAll()
        }
        prefsRepository.clearAll()
    }
}
