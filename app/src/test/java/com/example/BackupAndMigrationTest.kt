package com.example

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.backup.BackupManager
import com.example.data.local.BadgeUnlockedEntity
import com.example.data.local.DailyRewardEntity
import com.example.data.local.DayNoteEntity
import com.example.data.local.HabitEntity
import com.example.data.local.HabitLogEntity
import com.example.data.local.HabitWorksDatabase
import com.example.data.local.SkipDayEntity
import com.example.data.local.StreakFreezeEntity
import com.example.data.prefs.UserPreferencesRepository
import com.example.data.repository.HabitRepository
import com.example.domain.model.HabitCategory
import com.example.domain.model.HabitType
import com.example.domain.model.RewardType
import com.example.domain.model.TimeOfDay
import com.example.domain.model.RepeatDays
import com.example.ui.theme.AppThemeOption
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class BackupAndMigrationTest {

    private lateinit var context: Context
    private lateinit var database: HabitWorksDatabase
    private lateinit var prefsRepository: UserPreferencesRepository
    private lateinit var backupManager: BackupManager
    private lateinit var repository: HabitRepository
    private lateinit var tempDataStoreFile: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, HabitWorksDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        tempDataStoreFile = File(context.filesDir, "test_prefs_${System.nanoTime()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { tempDataStoreFile }
        )
        prefsRepository = UserPreferencesRepository(dataStore)
        backupManager = BackupManager(
            database = database,
            habitDao = database.habitDao(),
            habitLogDao = database.habitLogDao(),
            dayNoteDao = database.dayNoteDao(),
            habitReminderDao = database.habitReminderDao(),
            skipDayDao = database.skipDayDao(),
            streakFreezeDao = database.streakFreezeDao(),
            dailyRewardDao = database.dailyRewardDao(),
            badgeUnlockedDao = database.badgeUnlockedDao(),
            prefsRepository = prefsRepository,
            json = Json { ignoreUnknownKeys = true; isLenient = true }
        )
        repository = HabitRepository(
            appContext = context,
            habitDao = database.habitDao(),
            habitLogDao = database.habitLogDao(),
            dayNoteDao = database.dayNoteDao(),
            habitReminderDao = database.habitReminderDao(),
            skipDayDao = database.skipDayDao(),
            streakFreezeDao = database.streakFreezeDao(),
            dailyRewardDao = database.dailyRewardDao(),
            badgeUnlockedDao = database.badgeUnlockedDao()
        )
    }

    @After
    fun tearDown() {
        database.close()
        tempDataStoreFile.delete()
    }

    @Test
    fun `exportToJson and restoreFromBackup v3 round trip preserves all data and wipe clears new tables`() = runBlocking {
        database.habitDao().upsert(
            HabitEntity(
                id = "h1",
                name = "Drink Water",
                icon = "💧",
                colorHex = 0xFF0EA5E9,
                repeatDays = RepeatDays.ALL,
                createdAt = "2026-11-01",
                type = HabitType.COUNT.name,
                targetValue = 8,
                unitLabel = "glasses",
                timeOfDay = TimeOfDay.MORNING.name,
                category = HabitCategory.HEALTH.name,
                cueText = "After coffee",
                tinyVersion = "1 sip",
                stackAfterHabitId = null,
                challengeDays = 21,
                challengeStart = "2026-11-01"
            )
        )
        database.habitLogDao().upsert(
            HabitLogEntity(
                id = "2026-11-18_h1",
                habitId = "h1",
                date = "2026-11-18",
                done = true,
                progressValue = 8,
                completedAtMs = 987654321L,
                tiny = false
            )
        )
        database.dayNoteDao().upsert(DayNoteEntity(date = "2026-11-18", note = "Great day", mood = 5))
        database.skipDayDao().upsert(SkipDayEntity(date = "2026-11-17", reason = "Vacation"))
        database.streakFreezeDao().upsert(StreakFreezeEntity(date = "2026-11-16", appliedAt = 1234L))
        database.dailyRewardDao().upsert(
            DailyRewardEntity(date = "2026-11-18", rewardType = RewardType.BONUS_XP.name, rewardValue = 30, openedAtMs = 555L)
        )
        database.badgeUnlockedDao().upsertAll(listOf(BadgeUnlockedEntity("first_checkin", "2026-11-18")))

        prefsRepository.completeWelcome("Alex")
        prefsRepository.addStreakFreezeToken(2)
        prefsRepository.unlockThemesForSevenDays(AppThemeOption.OCEAN_COBALT, nowMs = 1_000_000L)

        val exported = backupManager.exportToJson()
        val parsedResult = backupManager.validateAndParse(exported)
        assertTrue(parsedResult.isSuccess)
        assertEquals(3, parsedResult.getOrThrow().schemaVersion)

        // Wipe and confirm ALL tables including daily_rewards and badges_unlocked are empty
        backupManager.deleteAllData()
        assertTrue(database.habitDao().getAllHabitsOnce().isEmpty())
        assertTrue(database.dailyRewardDao().getAllRewardsOnce().isEmpty())
        assertTrue(database.badgeUnlockedDao().getAllBadgesOnce().isEmpty())

        backupManager.restoreFromBackup(parsedResult.getOrThrow())

        val restoredHabits = database.habitDao().getAllHabitsOnce()
        assertEquals(1, restoredHabits.size)
        assertEquals("After coffee", restoredHabits[0].cueText)
        assertEquals("1 sip", restoredHabits[0].tinyVersion)
        assertEquals(21, restoredHabits[0].challengeDays)

        val restoredLogs = database.habitLogDao().getAllLogsOnce()
        assertEquals(1, restoredLogs.size)
        assertEquals(987654321L, restoredLogs[0].completedAtMs)

        assertEquals(5, database.dayNoteDao().getByDate("2026-11-18")?.mood)
        assertEquals(1, database.dailyRewardDao().getAllRewardsOnce().size)
        assertEquals(1, database.badgeUnlockedDao().getAllBadgesOnce().size)

        val prefs = prefsRepository.userPrefs.first()
        assertEquals("Alex", prefs.firstName)
        assertEquals(2, prefs.streakFreezeTokens)
        assertEquals(AppThemeOption.OCEAN_COBALT.id, prefs.selectedThemeId)
        assertTrue(prefs.hasSeenWelcome)
    }

    @Test
    fun `validateAndParse and restoreFromBackup accept legacy v1 and v2 JSON backups`() = runBlocking {
        val v1Json = """
            {
              "app": "HabitWorks",
              "schemaVersion": 1,
              "habits": [
                {
                  "id": "legacy_1",
                  "name": "Morning Walk",
                  "icon": "🚶",
                  "colorHex": 1000,
                  "repeatDays": 127,
                  "createdAt": "2026-10-01"
                }
              ],
              "logs": [
                {
                  "id": "2026-10-02_legacy_1",
                  "habitId": "legacy_1",
                  "date": "2026-10-02",
                  "done": true
                }
              ]
            }
        """.trimIndent()

        val v2Json = """
            {
              "app": "HabitWorks",
              "schemaVersion": 2,
              "habits": [
                {
                  "id": "legacy_2",
                  "name": "Hydrate",
                  "icon": "💧",
                  "colorHex": 2000,
                  "repeatDays": 127,
                  "createdAt": "2026-11-01",
                  "type": "COUNT",
                  "targetValue": 8,
                  "unitLabel": "glasses",
                  "timeOfDay": "MORNING",
                  "category": "HEALTH"
                }
              ],
              "logs": [],
              "skipDays": [{"date": "2026-11-02", "reason": "Rest"}],
              "streakFreezes": [{"date": "2026-11-03", "appliedAt": 99}]
            }
        """.trimIndent()

        val v1Parsed = backupManager.validateAndParse(v1Json)
        assertTrue(v1Parsed.isSuccess)
        backupManager.restoreFromBackup(v1Parsed.getOrThrow())
        assertEquals("Morning Walk", database.habitDao().getAllHabitsOnce().single().name)
        assertEquals("", database.habitDao().getAllHabitsOnce().single().cueText)

        val v2Parsed = backupManager.validateAndParse(v2Json)
        assertTrue(v2Parsed.isSuccess)
        backupManager.restoreFromBackup(v2Parsed.getOrThrow())
        assertEquals("Hydrate", database.habitDao().getAllHabitsOnce().single().name)
        assertEquals(1, database.skipDayDao().getAllSkipDaysOnce().size)
        assertEquals(1, database.streakFreezeDao().getAllFreezesOnce().size)
    }

    @Test
    fun `validateAndParse rejects empty, malformed, or wrong-app backups`() {
        assertTrue(backupManager.validateAndParse("").isFailure)
        assertTrue(backupManager.validateAndParse("{not-json").isFailure)
        assertTrue(
            backupManager.validateAndParse("""{"app":"OtherApp","schemaVersion":3}""").isFailure
        )
        assertTrue(
            backupManager.validateAndParse(
                """{"app":"HabitWorks","schemaVersion":3,"habits":[{"id":"","name":"Test","icon":"💧","colorHex":1,"repeatDays":127,"createdAt":"2026-11-01"}]}"""
            ).isFailure
        )
        assertTrue(
            backupManager.validateAndParse(
                """{"app":"HabitWorks","schemaVersion":3,"habits":[{"id":"1","name":"Test","icon":"💧","colorHex":1,"repeatDays":127,"createdAt":"not-a-date"}]}"""
            ).isFailure
        )
    }

    @Test
    fun `room MIGRATION_1_2 and MIGRATION_2_3 preserve existing data across versions`() {
        val dbName = "migration_test_${System.nanoTime()}.db"
        val dbFile = context.getDatabasePath(dbName)
        dbFile.parentFile?.mkdirs()

        val v1Helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS habits (
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
                                pendingSync INTEGER NOT NULL
                            )
                            """.trimIndent()
                        )
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS habit_logs (
                                id TEXT NOT NULL PRIMARY KEY,
                                habitId TEXT NOT NULL,
                                date TEXT NOT NULL,
                                done INTEGER NOT NULL,
                                updatedAt INTEGER NOT NULL,
                                pendingSync INTEGER NOT NULL
                            )
                            """.trimIndent()
                        )
                        db.execSQL(
                            """
                            CREATE TABLE IF NOT EXISTS day_notes (
                                date TEXT NOT NULL PRIMARY KEY,
                                note TEXT NOT NULL,
                                updatedAt INTEGER NOT NULL,
                                pendingSync INTEGER NOT NULL
                            )
                            """.trimIndent()
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        val writableV1 = v1Helper.writableDatabase
        writableV1.execSQL(
            """
            INSERT INTO habits (id, name, icon, colorHex, repeatDays, reminderTime, createdAt, archivedAt, sortOrder, updatedAt, pendingSync)
            VALUES ('h_v1', 'Meditate', '🧘', 123456, 127, '08:00', '2026-11-01', NULL, 0, 1000, 1)
            """.trimIndent()
        )
        writableV1.execSQL(
            """
            INSERT INTO habit_logs (id, habitId, date, done, updatedAt, pendingSync)
            VALUES ('2026-11-18_h_v1', 'h_v1', '2026-11-18', 1, 2000, 1)
            """.trimIndent()
        )
        writableV1.execSQL(
            """
            INSERT INTO day_notes (date, note, updatedAt, pendingSync)
            VALUES ('2026-11-18', 'V1 note preserved', 3000, 1)
            """.trimIndent()
        )
        v1Helper.close()

        val migratedDb = Room.databaseBuilder(context, HabitWorksDatabase::class.java, dbName)
            .addMigrations(HabitWorksDatabase.MIGRATION_1_2, HabitWorksDatabase.MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()

        runBlocking {
            val habits = migratedDb.habitDao().getAllHabitsOnce()
            assertEquals(1, habits.size)
            assertEquals("Meditate", habits[0].name)
            assertEquals("", habits[0].cueText)
            assertEquals(0, habits[0].challengeDays)

            val logs = migratedDb.habitLogDao().getAllLogsOnce()
            assertEquals(1, logs.size)
            assertTrue(logs[0].done)
            assertNull(logs[0].completedAtMs)
            assertFalse(logs[0].tiny)

            val notes = migratedDb.dayNoteDao().getAllNotesOnce()
            assertEquals(1, notes.size)
            assertEquals("V1 note preserved", notes[0].note)
            assertNull(notes[0].mood)

            migratedDb.dailyRewardDao().upsert(DailyRewardEntity("2026-11-19", RewardType.BONUS_XP.name, 20, 100L))
            migratedDb.badgeUnlockedDao().insertAllIgnore(listOf(BadgeUnlockedEntity("first_checkin", "2026-11-19")))
            assertEquals(1, migratedDb.dailyRewardDao().getAllRewardsOnce().size)
            assertEquals(1, migratedDb.badgeUnlockedDao().getAllBadgesOnce().size)
        }

        migratedDb.close()
        dbFile.delete()
    }

    @Test
    fun `updateHabitProgress sets completedAtMs only on completion transition and clears on un-done`() = runBlocking {
        val today = LocalDate.of(2026, 11, 24)
        val (timerHabit, _) = repository.addHabit(
            name = "Focus",
            icon = "🎯",
            colorHex = 0xFF0D9488,
            repeatDays = RepeatDays.ALL,
            type = HabitType.TIMER,
            targetValue = 1, // 60 seconds
            createdDate = today
        )

        // Timer tick before target -> completedAtMs stays null
        repository.updateHabitProgress(timerHabit.id, today, 30)
        var log = database.habitLogDao().getLog(timerHabit.id, today.toString())!!
        assertFalse(log.done)
        assertNull(log.completedAtMs)

        // Reach target -> completedAtMs set
        repository.updateHabitProgress(timerHabit.id, today, 60)
        log = database.habitLogDao().getLog(timerHabit.id, today.toString())!!
        assertTrue(log.done)
        val firstCompletedAt = log.completedAtMs
        assertNotNull(firstCompletedAt)

        // Further tick stays done -> completedAtMs unchanged
        repository.updateHabitProgress(timerHabit.id, today, 75)
        log = database.habitLogDao().getLog(timerHabit.id, today.toString())!!
        assertEquals(firstCompletedAt, log.completedAtMs)

        // Drop below target -> cleared
        repository.updateHabitProgress(timerHabit.id, today, 10)
        log = database.habitLogDao().getLog(timerHabit.id, today.toString())!!
        assertFalse(log.done)
        assertNull(log.completedAtMs)
    }
}
