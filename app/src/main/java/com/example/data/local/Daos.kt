package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {
    @Query("SELECT * FROM habits ORDER BY sortOrder ASC, createdAt ASC")
    fun observeAllHabits(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits ORDER BY sortOrder ASC, createdAt ASC")
    suspend fun getAllHabitsOnce(): List<HabitEntity>

    @Query("SELECT * FROM habits WHERE archivedAt IS NULL ORDER BY sortOrder ASC, createdAt ASC")
    fun observeActiveHabits(): Flow<List<HabitEntity>>

    @Query("SELECT * FROM habits WHERE archivedAt IS NULL ORDER BY sortOrder ASC, createdAt ASC")
    suspend fun getActiveHabitsOnce(): List<HabitEntity>

    @Query("SELECT COUNT(*) FROM habits WHERE archivedAt IS NULL")
    suspend fun countActiveHabits(): Int

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM habits")
    suspend fun maxSortOrder(): Int

    @Query("SELECT * FROM habits WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): HabitEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(habit: HabitEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(habits: List<HabitEntity>)

    @Query("UPDATE habits SET archivedAt = :archivedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setArchivedAt(id: String, archivedAt: String?, updatedAt: Long)

    @Query("UPDATE habits SET sortOrder = :sortOrder, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setSortOrder(id: String, sortOrder: Int, updatedAt: Long)

    @Query("UPDATE habits SET reminderTime = :reminderTime, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setReminderTime(id: String, reminderTime: String?, updatedAt: Long)

    @Query("DELETE FROM habits")
    suspend fun deleteAll()
}

@Dao
interface HabitLogDao {
    @Query("SELECT * FROM habit_logs")
    fun observeAllLogs(): Flow<List<HabitLogEntity>>

    @Query("SELECT * FROM habit_logs")
    suspend fun getAllLogsOnce(): List<HabitLogEntity>

    @Query("SELECT * FROM habit_logs WHERE date = :date")
    suspend fun getLogsForDateOnce(date: String): List<HabitLogEntity>

    @Query("SELECT * FROM habit_logs WHERE habitId = :habitId AND date = :date LIMIT 1")
    suspend fun getLog(habitId: String, date: String): HabitLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(log: HabitLogEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(logs: List<HabitLogEntity>)

    @Query("DELETE FROM habit_logs")
    suspend fun deleteAll()
}

@Dao
interface DayNoteDao {
    @Query("SELECT * FROM day_notes ORDER BY date DESC")
    fun observeAllNotes(): Flow<List<DayNoteEntity>>

    @Query("SELECT * FROM day_notes ORDER BY date DESC")
    suspend fun getAllNotesOnce(): List<DayNoteEntity>

    @Query("SELECT * FROM day_notes WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: String): DayNoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(note: DayNoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(notes: List<DayNoteEntity>)

    @Query("DELETE FROM day_notes")
    suspend fun deleteAll()
}

@Dao
interface HabitReminderDao {
    @Query("SELECT * FROM habit_reminders ORDER BY hour ASC, minute ASC")
    fun observeAllReminders(): Flow<List<HabitReminderEntity>>

    @Query("SELECT * FROM habit_reminders ORDER BY hour ASC, minute ASC")
    suspend fun getAllRemindersOnce(): List<HabitReminderEntity>

    @Query("SELECT * FROM habit_reminders WHERE habitId = :habitId ORDER BY hour ASC, minute ASC")
    suspend fun getForHabitOnce(habitId: String): List<HabitReminderEntity>

    @Query("SELECT * FROM habit_reminders WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): HabitReminderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(reminders: List<HabitReminderEntity>)

    @Query("DELETE FROM habit_reminders WHERE habitId = :habitId")
    suspend fun deleteForHabit(habitId: String)

    @Query("DELETE FROM habit_reminders")
    suspend fun deleteAll()
}

@Dao
interface SkipDayDao {
    @Query("SELECT * FROM skip_days ORDER BY date DESC")
    fun observeAllSkipDays(): Flow<List<SkipDayEntity>>

    @Query("SELECT * FROM skip_days ORDER BY date DESC")
    suspend fun getAllSkipDaysOnce(): List<SkipDayEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(skipDay: SkipDayEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(skipDays: List<SkipDayEntity>)

    @Query("DELETE FROM skip_days WHERE date = :date")
    suspend fun deleteByDate(date: String)

    @Query("DELETE FROM skip_days")
    suspend fun deleteAll()
}

@Dao
interface StreakFreezeDao {
    @Query("SELECT * FROM streak_freezes ORDER BY date DESC")
    fun observeAllFreezes(): Flow<List<StreakFreezeEntity>>

    @Query("SELECT * FROM streak_freezes ORDER BY date DESC")
    suspend fun getAllFreezesOnce(): List<StreakFreezeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(freeze: StreakFreezeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(freezes: List<StreakFreezeEntity>)

    @Query("DELETE FROM streak_freezes")
    suspend fun deleteAll()
}

@Dao
interface DailyRewardDao {
    @Query("SELECT * FROM daily_rewards ORDER BY date DESC")
    fun observeAllRewards(): Flow<List<DailyRewardEntity>>

    @Query("SELECT * FROM daily_rewards ORDER BY date DESC")
    suspend fun getAllRewardsOnce(): List<DailyRewardEntity>

    @Query("SELECT * FROM daily_rewards WHERE date = :date LIMIT 1")
    suspend fun getByDate(date: String): DailyRewardEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(reward: DailyRewardEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rewards: List<DailyRewardEntity>)

    @Query("DELETE FROM daily_rewards")
    suspend fun deleteAll()
}

@Dao
interface BadgeUnlockedDao {
    @Query("SELECT * FROM badges_unlocked ORDER BY unlockedAt DESC")
    fun observeAllBadges(): Flow<List<BadgeUnlockedEntity>>

    @Query("SELECT * FROM badges_unlocked ORDER BY unlockedAt DESC")
    suspend fun getAllBadgesOnce(): List<BadgeUnlockedEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAllIgnore(badges: List<BadgeUnlockedEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(badges: List<BadgeUnlockedEntity>)

    @Query("DELETE FROM badges_unlocked")
    suspend fun deleteAll()
}
