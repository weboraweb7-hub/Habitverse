package com.example.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        HabitEntity::class,
        HabitLogEntity::class,
        DayNoteEntity::class,
        HabitReminderEntity::class,
        SkipDayEntity::class,
        StreakFreezeEntity::class,
        DailyRewardEntity::class,
        BadgeUnlockedEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class HabitWorksDatabase : RoomDatabase() {
    abstract fun habitDao(): HabitDao
    abstract fun habitLogDao(): HabitLogDao
    abstract fun dayNoteDao(): DayNoteDao
    abstract fun habitReminderDao(): HabitReminderDao
    abstract fun skipDayDao(): SkipDayDao
    abstract fun streakFreezeDao(): StreakFreezeDao
    abstract fun dailyRewardDao(): DailyRewardDao
    abstract fun badgeUnlockedDao(): BadgeUnlockedDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS habits_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        icon TEXT NOT NULL,
                        colorHex INTEGER NOT NULL,
                        repeatDays INTEGER NOT NULL,
                        reminderTime TEXT,
                        createdAt TEXT NOT NULL,
                        archivedAt TEXT,
                        sortOrder INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        type TEXT NOT NULL DEFAULT 'YES_NO',
                        targetValue INTEGER NOT NULL DEFAULT 1,
                        unitLabel TEXT NOT NULL DEFAULT '',
                        timeOfDay TEXT NOT NULL DEFAULT 'ANYTIME',
                        category TEXT NOT NULL DEFAULT 'GENERAL'
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO habits_new (
                        id, name, icon, colorHex, repeatDays, reminderTime,
                        createdAt, archivedAt, sortOrder, updatedAt
                    )
                    SELECT id, name, icon, colorHex, repeatDays, reminderTime,
                           createdAt, archivedAt, sortOrder, updatedAt
                    FROM habits
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE habits")
                db.execSQL("ALTER TABLE habits_new RENAME TO habits")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS habit_logs_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        habitId TEXT NOT NULL,
                        date TEXT NOT NULL,
                        done INTEGER NOT NULL,
                        progressValue INTEGER NOT NULL DEFAULT 0,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO habit_logs_new (id, habitId, date, done, progressValue, updatedAt)
                    SELECT id, habitId, date, done, CASE WHEN done = 1 THEN 1 ELSE 0 END, updatedAt
                    FROM habit_logs
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE habit_logs")
                db.execSQL("ALTER TABLE habit_logs_new RENAME TO habit_logs")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_habit_logs_habitId_date ON habit_logs (habitId, date)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS day_notes_new (
                        date TEXT NOT NULL PRIMARY KEY,
                        note TEXT NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    INSERT INTO day_notes_new (date, note, updatedAt)
                    SELECT date, note, updatedAt FROM day_notes
                    """.trimIndent()
                )
                db.execSQL("DROP TABLE day_notes")
                db.execSQL("ALTER TABLE day_notes_new RENAME TO day_notes")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS habit_reminders (
                        id TEXT NOT NULL PRIMARY KEY,
                        habitId TEXT NOT NULL,
                        hour INTEGER NOT NULL,
                        minute INTEGER NOT NULL,
                        enabled INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_habit_reminders_habitId ON habit_reminders (habitId)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS skip_days (
                        date TEXT NOT NULL PRIMARY KEY,
                        reason TEXT NOT NULL
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS streak_freezes (
                        date TEXT NOT NULL PRIMARY KEY,
                        appliedAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE habits ADD COLUMN cueText TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE habits ADD COLUMN tinyVersion TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE habits ADD COLUMN stackAfterHabitId TEXT")
                db.execSQL("ALTER TABLE habits ADD COLUMN challengeDays INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE habits ADD COLUMN challengeStart TEXT")

                db.execSQL("ALTER TABLE habit_logs ADD COLUMN completedAtMs INTEGER")
                db.execSQL("ALTER TABLE habit_logs ADD COLUMN tiny INTEGER NOT NULL DEFAULT 0")

                db.execSQL("ALTER TABLE day_notes ADD COLUMN mood INTEGER")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS daily_rewards (
                        date TEXT NOT NULL PRIMARY KEY,
                        rewardType TEXT NOT NULL,
                        rewardValue INTEGER NOT NULL,
                        openedAtMs INTEGER
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS badges_unlocked (
                        badgeId TEXT NOT NULL PRIMARY KEY,
                        unlockedAt TEXT NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
