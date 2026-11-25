package com.example.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.domain.model.FlameSkin
import com.example.ui.theme.AppThemeOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

data class UserPrefs(
    val hasSeenWelcome: Boolean = false,
    val firstName: String = "",
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 8,
    val reminderMinute: Int = 30,
    val lastInterstitialTimestampMs: Long = 0L,
    val streakFreezeTokens: Int = 0,
    val selectedThemeId: String = AppThemeOption.FOREST_TEAL.id,
    val themeUnlockExpiresAtMs: Long = 0L,
    val installSeed: Long = 0L,
    val activeFlameSkinId: String = FlameSkin.CLASSIC.id,
    val unlockedFlameSkinIds: Set<String> = setOf(FlameSkin.CLASSIC.id),
    val plantBonusXpTotal: Int = 0,
    val lastPlantBonusDate: String = "",
    val lastAutoShieldStreakMilestone: Int = 0,
    val lastStreakNudgeDate: String = ""
) {
    fun effectiveTheme(nowMs: Long = System.currentTimeMillis()): AppThemeOption {
        val chosen = AppThemeOption.fromId(selectedThemeId)
        return if (chosen.isFreeForever || themeUnlockExpiresAtMs > nowMs) {
            chosen
        } else {
            AppThemeOption.FOREST_TEAL
        }
    }

    fun isUnlockedThemeActive(nowMs: Long = System.currentTimeMillis()): Boolean =
        themeUnlockExpiresAtMs > nowMs

    val activeFlameSkin: FlameSkin
        get() = FlameSkin.fromId(activeFlameSkinId)
}

@Singleton
class UserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    companion object {
        const val THEME_UNLOCK_DURATION_MS = 7L * 24L * 60L * 60L * 1000L
        const val MAX_STREAK_FREEZES = 3
    }

    private object Keys {
        val HAS_SEEN_WELCOME = booleanPreferencesKey("has_seen_welcome")
        val FIRST_NAME = stringPreferencesKey("first_name")
        val REMINDER_ENABLED = booleanPreferencesKey("reminder_enabled")
        val REMINDER_HOUR = intPreferencesKey("reminder_hour")
        val REMINDER_MINUTE = intPreferencesKey("reminder_minute")
        val LAST_INTERSTITIAL_MS = longPreferencesKey("last_interstitial_ms")
        val STREAK_FREEZE_TOKENS = intPreferencesKey("streak_freeze_tokens")
        val SELECTED_THEME_ID = stringPreferencesKey("selected_theme_id")
        val THEME_UNLOCK_EXPIRES_MS = longPreferencesKey("theme_unlock_expires_ms")
        val INSTALL_SEED = longPreferencesKey("install_seed")
        val ACTIVE_FLAME_SKIN_ID = stringPreferencesKey("active_flame_skin_id")
        val UNLOCKED_FLAME_SKINS = stringPreferencesKey("unlocked_flame_skins")
        val PLANT_BONUS_XP_TOTAL = intPreferencesKey("plant_bonus_xp_total")
        val LAST_PLANT_BONUS_DATE = stringPreferencesKey("last_plant_bonus_date")
        val LAST_AUTO_SHIELD_MILESTONE = intPreferencesKey("last_auto_shield_milestone")
        val LAST_STREAK_NUDGE_DATE = stringPreferencesKey("last_streak_nudge_date")
    }

    val userPrefs: Flow<UserPrefs> = dataStore.data.map { prefs ->
        val rawSkins = prefs[Keys.UNLOCKED_FLAME_SKINS]
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?.toSet()
            ?: setOf(FlameSkin.CLASSIC.id)
        UserPrefs(
            hasSeenWelcome = prefs[Keys.HAS_SEEN_WELCOME] ?: false,
            firstName = prefs[Keys.FIRST_NAME].orEmpty(),
            reminderEnabled = prefs[Keys.REMINDER_ENABLED] ?: false,
            reminderHour = prefs[Keys.REMINDER_HOUR] ?: 8,
            reminderMinute = prefs[Keys.REMINDER_MINUTE] ?: 30,
            lastInterstitialTimestampMs = prefs[Keys.LAST_INTERSTITIAL_MS] ?: 0L,
            streakFreezeTokens = prefs[Keys.STREAK_FREEZE_TOKENS] ?: 0,
            selectedThemeId = prefs[Keys.SELECTED_THEME_ID] ?: AppThemeOption.FOREST_TEAL.id,
            themeUnlockExpiresAtMs = prefs[Keys.THEME_UNLOCK_EXPIRES_MS] ?: 0L,
            installSeed = prefs[Keys.INSTALL_SEED] ?: 0L,
            activeFlameSkinId = prefs[Keys.ACTIVE_FLAME_SKIN_ID] ?: FlameSkin.CLASSIC.id,
            unlockedFlameSkinIds = rawSkins + FlameSkin.CLASSIC.id,
            plantBonusXpTotal = prefs[Keys.PLANT_BONUS_XP_TOTAL] ?: 0,
            lastPlantBonusDate = prefs[Keys.LAST_PLANT_BONUS_DATE].orEmpty(),
            lastAutoShieldStreakMilestone = prefs[Keys.LAST_AUTO_SHIELD_MILESTONE] ?: 0,
            lastStreakNudgeDate = prefs[Keys.LAST_STREAK_NUDGE_DATE].orEmpty()
        )
    }

    suspend fun getOrCreateInstallSeed(): Long {
        var resolved = 0L
        dataStore.edit { prefs ->
            val current = prefs[Keys.INSTALL_SEED] ?: 0L
            if (current != 0L) {
                resolved = current
            } else {
                val created = Random.nextLong(100_000L, Long.MAX_VALUE)
                prefs[Keys.INSTALL_SEED] = created
                resolved = created
            }
        }
        return resolved
    }

    suspend fun completeWelcome(firstName: String) {
        dataStore.edit { prefs ->
            prefs[Keys.HAS_SEEN_WELCOME] = true
            prefs[Keys.FIRST_NAME] = firstName.trim().take(40)
        }
    }

    suspend fun setFirstName(firstName: String) {
        dataStore.edit { prefs ->
            prefs[Keys.FIRST_NAME] = firstName.trim().take(40)
        }
    }

    suspend fun setReminder(enabled: Boolean, hour: Int, minute: Int) {
        dataStore.edit { prefs ->
            prefs[Keys.REMINDER_ENABLED] = enabled
            prefs[Keys.REMINDER_HOUR] = hour
            prefs[Keys.REMINDER_MINUTE] = minute
        }
    }

    suspend fun setLastInterstitialTimestamp(timestampMs: Long) {
        dataStore.edit { prefs ->
            prefs[Keys.LAST_INTERSTITIAL_MS] = timestampMs
        }
    }

    suspend fun addStreakFreezeToken(delta: Int = 1, capAtMax: Boolean = true) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.STREAK_FREEZE_TOKENS] ?: 0
            val sum = (current + delta).coerceAtLeast(0)
            prefs[Keys.STREAK_FREEZE_TOKENS] = if (capAtMax) sum.coerceAtMost(MAX_STREAK_FREEZES) else sum
        }
    }

    suspend fun consumeStreakFreezeToken(): Boolean {
        var consumed = false
        dataStore.edit { prefs ->
            val current = prefs[Keys.STREAK_FREEZE_TOKENS] ?: 0
            if (current > 0) {
                prefs[Keys.STREAK_FREEZE_TOKENS] = current - 1
                consumed = true
            }
        }
        return consumed
    }

    suspend fun grantAutoStreakShieldIfEligible(currentStreak: Int): Boolean {
        val milestone = (currentStreak / 7) * 7
        if (milestone < 7) return false
        var granted = false
        dataStore.edit { prefs ->
            val lastMilestone = prefs[Keys.LAST_AUTO_SHIELD_MILESTONE] ?: 0
            if (milestone > lastMilestone) {
                val currentTokens = prefs[Keys.STREAK_FREEZE_TOKENS] ?: 0
                if (currentTokens < MAX_STREAK_FREEZES) {
                    prefs[Keys.STREAK_FREEZE_TOKENS] = (currentTokens + 1).coerceAtMost(MAX_STREAK_FREEZES)
                    granted = true
                }
                prefs[Keys.LAST_AUTO_SHIELD_MILESTONE] = milestone
            }
        }
        return granted
    }

    suspend fun grantDailyPlantBonusIfEligible(today: LocalDate): Boolean {
        val todayStr = today.toString()
        var granted = false
        dataStore.edit { prefs ->
            if (prefs[Keys.LAST_PLANT_BONUS_DATE] != todayStr) {
                prefs[Keys.LAST_PLANT_BONUS_DATE] = todayStr
                prefs[Keys.PLANT_BONUS_XP_TOTAL] = (prefs[Keys.PLANT_BONUS_XP_TOTAL] ?: 0) + 5
                granted = true
            }
        }
        return granted
    }

    suspend fun unlockFlameSkin(skin: FlameSkin) {
        dataStore.edit { prefs ->
            val current = prefs[Keys.UNLOCKED_FLAME_SKINS]
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?.toMutableSet()
                ?: mutableSetOf(FlameSkin.CLASSIC.id)
            current.add(FlameSkin.CLASSIC.id)
            current.add(skin.id)
            prefs[Keys.UNLOCKED_FLAME_SKINS] = current.joinToString(",")
            prefs[Keys.ACTIVE_FLAME_SKIN_ID] = skin.id
        }
    }

    suspend fun setLastStreakNudgeDate(date: String) {
        dataStore.edit { prefs ->
            prefs[Keys.LAST_STREAK_NUDGE_DATE] = date
        }
    }

    suspend fun unlockThemesForSevenDays(theme: AppThemeOption, nowMs: Long = System.currentTimeMillis()) {
        dataStore.edit { prefs ->
            prefs[Keys.SELECTED_THEME_ID] = theme.id
            prefs[Keys.THEME_UNLOCK_EXPIRES_MS] = nowMs + THEME_UNLOCK_DURATION_MS
        }
    }

    suspend fun selectTheme(theme: AppThemeOption) {
        dataStore.edit { prefs ->
            prefs[Keys.SELECTED_THEME_ID] = theme.id
        }
    }

    suspend fun restoreFromBackup(
        firstName: String,
        reminderEnabled: Boolean,
        reminderHour: Int,
        reminderMinute: Int,
        streakFreezeTokens: Int,
        selectedThemeId: String,
        themeUnlockExpiresAtMs: Long,
        installSeed: Long = 0L,
        activeFlameSkinId: String = FlameSkin.CLASSIC.id,
        unlockedFlameSkinIds: Set<String> = setOf(FlameSkin.CLASSIC.id),
        plantBonusXpTotal: Int = 0,
        lastPlantBonusDate: String = "",
        lastAutoShieldStreakMilestone: Int = 0
    ) {
        dataStore.edit { prefs ->
            prefs[Keys.HAS_SEEN_WELCOME] = true
            prefs[Keys.FIRST_NAME] = firstName.trim().take(40)
            prefs[Keys.REMINDER_ENABLED] = reminderEnabled
            prefs[Keys.REMINDER_HOUR] = reminderHour
            prefs[Keys.REMINDER_MINUTE] = reminderMinute
            prefs[Keys.STREAK_FREEZE_TOKENS] = streakFreezeTokens.coerceAtLeast(0)
            prefs[Keys.SELECTED_THEME_ID] = selectedThemeId
            prefs[Keys.THEME_UNLOCK_EXPIRES_MS] = themeUnlockExpiresAtMs
            if (installSeed != 0L) prefs[Keys.INSTALL_SEED] = installSeed
            prefs[Keys.ACTIVE_FLAME_SKIN_ID] = FlameSkin.fromId(activeFlameSkinId).id
            prefs[Keys.UNLOCKED_FLAME_SKINS] = (unlockedFlameSkinIds + FlameSkin.CLASSIC.id).joinToString(",")
            prefs[Keys.PLANT_BONUS_XP_TOTAL] = plantBonusXpTotal.coerceAtLeast(0)
            prefs[Keys.LAST_PLANT_BONUS_DATE] = lastPlantBonusDate
            prefs[Keys.LAST_AUTO_SHIELD_MILESTONE] = lastAutoShieldStreakMilestone.coerceAtLeast(0)
        }
    }

    suspend fun clearAll() {
        dataStore.edit { it.clear() }
    }
}
