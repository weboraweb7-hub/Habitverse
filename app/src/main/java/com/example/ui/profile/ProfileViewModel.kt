package com.example.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.backup.BackupManager
import com.example.data.backup.HabitWorksBackup
import com.example.data.prefs.UserPreferencesRepository
import com.example.data.repository.HabitRepository
import com.example.domain.usecase.CurrentDateProvider
import com.example.domain.usecase.HabitCalculator
import com.example.ui.theme.AppThemeOption
import com.example.widget.HabitWorksWidgetUpdater
import com.example.work.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class ProfileUiState(
    val firstName: String = "",
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 8,
    val reminderMinute: Int = 30,
    val streakFreezeTokens: Int = 0,
    val protectableMissedDate: LocalDate? = null,
    val isTodaySkipped: Boolean = false,
    val activeTheme: AppThemeOption = AppThemeOption.FOREST_TEAL,
    val isThemesUnlocked: Boolean = false,
    val themeUnlockExpiresAtMs: Long = 0L,
    val statusMessage: String? = null,
    val pendingImportBackup: HabitWorksBackup? = null
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    @param:ApplicationContext private val appContext: Context,
    private val prefsRepository: UserPreferencesRepository,
    private val habitRepository: HabitRepository,
    private val reminderScheduler: ReminderScheduler,
    private val backupManager: BackupManager,
    private val currentDateProvider: CurrentDateProvider
) : ViewModel() {

    private val _statusMessage = MutableStateFlow<String?>(null)
    private val _pendingImportBackup = MutableStateFlow<HabitWorksBackup?>(null)

    val uiState: StateFlow<ProfileUiState> = combine(
        prefsRepository.userPrefs,
        combine(
            habitRepository.observeAllHabits(),
            habitRepository.observeAllLogs(),
            habitRepository.observeSkipDays(),
            habitRepository.observeStreakFreezes()
        ) { habits, logs, skips, freezes ->
            val skipDates = skips.mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }.toSet()
            val freezeDates = freezes.mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }.toSet()
            Triple(habits to logs, skipDates, freezeDates)
        },
        currentDateProvider.today,
        _statusMessage,
        _pendingImportBackup
    ) { prefs, (habitsAndLogs, skipDates, freezeDates), today, status, pendingBackup ->
        val protectable = HabitCalculator.findProtectableMissedDate(
            habits = habitsAndLogs.first,
            logs = habitsAndLogs.second,
            today = today,
            skippedDates = skipDates,
            frozenDates = freezeDates
        )
        val now = System.currentTimeMillis()
        ProfileUiState(
            firstName = prefs.firstName,
            reminderEnabled = prefs.reminderEnabled,
            reminderHour = prefs.reminderHour,
            reminderMinute = prefs.reminderMinute,
            streakFreezeTokens = prefs.streakFreezeTokens,
            protectableMissedDate = protectable,
            isTodaySkipped = today in skipDates,
            activeTheme = prefs.effectiveTheme(now),
            isThemesUnlocked = prefs.isUnlockedThemeActive(now),
            themeUnlockExpiresAtMs = prefs.themeUnlockExpiresAtMs,
            statusMessage = status,
            pendingImportBackup = pendingBackup
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = ProfileUiState()
    )

    fun updateFirstName(name: String) {
        viewModelScope.launch {
            prefsRepository.setFirstName(name)
        }
    }

    fun setReminderEnabled(enabled: Boolean, hour: Int, minute: Int) {
        viewModelScope.launch {
            prefsRepository.setReminder(enabled, hour, minute)
            if (enabled) {
                reminderScheduler.scheduleDaily(hour, minute)
            } else {
                reminderScheduler.cancelGlobal()
            }
        }
    }

    fun updateReminderTime(hour: Int, minute: Int, currentlyEnabled: Boolean) {
        viewModelScope.launch {
            prefsRepository.setReminder(currentlyEnabled, hour, minute)
            if (currentlyEnabled) {
                reminderScheduler.scheduleDaily(hour, minute)
            }
        }
    }

    fun selectTheme(theme: AppThemeOption) {
        viewModelScope.launch {
            prefsRepository.selectTheme(theme)
        }
    }

    fun onThemeUnlockRewardEarned(targetTheme: AppThemeOption) {
        viewModelScope.launch {
            prefsRepository.unlockThemesForSevenDays(targetTheme)
            _statusMessage.value = "${targetTheme.title} unlocked for 7 days!"
        }
    }

    fun onStreakFreezeRewardEarned() {
        viewModelScope.launch {
            prefsRepository.addStreakFreezeToken(1)
            _statusMessage.value = "Earned +1 Streak Freeze!"
        }
    }

    fun applyFreezeToMissedDate(date: LocalDate) {
        viewModelScope.launch {
            if (prefsRepository.consumeStreakFreezeToken()) {
                habitRepository.applyStreakFreeze(date)
                _statusMessage.value = "Streak Freeze applied to $date"
            }
        }
    }

    fun toggleTodaySkip() {
        viewModelScope.launch {
            habitRepository.toggleSkipDay(currentDateProvider.today.value)
        }
    }

    fun enableVacationDays(daysCount: Int) {
        viewModelScope.launch {
            habitRepository.setVacationRange(currentDateProvider.today.value, daysCount)
            _statusMessage.value = "Vacation mode enabled for $daysCount days."
        }
    }

    fun showStatus(message: String) {
        _statusMessage.value = message
    }

    fun clearStatus() {
        _statusMessage.value = null
    }

    suspend fun buildExportBackupJson(): String = backupManager.exportToJson()

    fun validateImportJson(rawJson: String) {
        backupManager.validateAndParse(rawJson)
            .onSuccess { backup ->
                _pendingImportBackup.value = backup
            }
            .onFailure { err ->
                _statusMessage.value = err.localizedMessage ?: "Invalid backup file."
            }
    }

    fun dismissImportConfirmation() {
        _pendingImportBackup.value = null
    }

    fun confirmImportBackup() {
        val backup = _pendingImportBackup.value ?: return
        _pendingImportBackup.value = null
        viewModelScope.launch {
            backupManager.restoreFromBackup(backup)
            val reminders = habitRepository.getAllRemindersOnce()
            reminderScheduler.rescheduleAll(
                globalEnabled = backup.userPrefs.reminderEnabled,
                globalHour = backup.userPrefs.reminderHour,
                globalMinute = backup.userPrefs.reminderMinute,
                habitReminders = reminders
            )
            HabitWorksWidgetUpdater.updateAll(appContext)
            _statusMessage.value = "Backup restored (${backup.habits.size} habits)."
        }
    }

    fun deleteAllMyData() {
        viewModelScope.launch {
            backupManager.deleteAllData()
            reminderScheduler.cancelAll()
            HabitWorksWidgetUpdater.updateAll(appContext)
            _statusMessage.value = "All your local data has been deleted."
        }
    }
}
