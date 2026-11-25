package com.example.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import androidx.work.WorkManager
import com.example.data.local.BadgeUnlockedDao
import com.example.data.local.DailyRewardDao
import com.example.data.local.DayNoteDao
import com.example.data.local.HabitDao
import com.example.data.local.HabitLogDao
import com.example.data.local.HabitReminderDao
import com.example.data.local.HabitWorksDatabase
import com.example.data.local.SkipDayDao
import com.example.data.local.StreakFreezeDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Singleton

private val Context.userPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "habitworks_preferences"
)

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): HabitWorksDatabase =
        Room.databaseBuilder(
            context,
            HabitWorksDatabase::class.java,
            "habitworks_db"
        )
            .addMigrations(
                HabitWorksDatabase.MIGRATION_1_2,
                HabitWorksDatabase.MIGRATION_2_3
            )
            .build()

    @Provides
    fun provideHabitDao(db: HabitWorksDatabase): HabitDao = db.habitDao()

    @Provides
    fun provideHabitLogDao(db: HabitWorksDatabase): HabitLogDao = db.habitLogDao()

    @Provides
    fun provideDayNoteDao(db: HabitWorksDatabase): DayNoteDao = db.dayNoteDao()

    @Provides
    fun provideHabitReminderDao(db: HabitWorksDatabase): HabitReminderDao = db.habitReminderDao()

    @Provides
    fun provideSkipDayDao(db: HabitWorksDatabase): SkipDayDao = db.skipDayDao()

    @Provides
    fun provideStreakFreezeDao(db: HabitWorksDatabase): StreakFreezeDao = db.streakFreezeDao()

    @Provides
    fun provideDailyRewardDao(db: HabitWorksDatabase): DailyRewardDao = db.dailyRewardDao()

    @Provides
    fun provideBadgeUnlockedDao(db: HabitWorksDatabase): BadgeUnlockedDao = db.badgeUnlockedDao()

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        context.userPreferencesDataStore

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)
}
