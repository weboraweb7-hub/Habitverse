package com.example.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.prefs.UserPreferencesRepository
import com.example.data.repository.HabitRepository
import com.example.data.repository.ReminderSlotInput
import com.example.domain.model.Habit
import com.example.domain.model.HabitCategory
import com.example.domain.model.HabitReminder
import com.example.domain.model.HabitType
import com.example.domain.model.StarterPack
import com.example.domain.model.StarterPacks
import com.example.domain.model.SuggestedHabit
import com.example.domain.model.SuggestedHabits
import com.example.domain.model.TimeOfDay
import com.example.domain.usecase.CurrentDateProvider
import com.example.domain.usecase.HabitCalculator
import com.example.domain.usecase.QuickAddParser
import com.example.work.ReminderScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TasksUiState(
    val activeHabits: List<Habit> = emptyList(),
    val allActiveHabits: List<Habit> = emptyList(),
    val remindersByHabit: Map<String, List<HabitReminder>> = emptyMap(),
    val selectedCategory: HabitCategory? = null,
    val suggestedHabits: List<SuggestedHabit> = SuggestedHabits.items,
    val starterPacks: List<StarterPack> = StarterPacks.packs,
    val editingHabit: Habit? = null,
    val editingReminders: List<HabitReminder> = emptyList(),
    val isEditorOpen: Boolean = false,
    val pendingArchiveHabit: Habit? = null,
    val lastArchivedHabit: Habit? = null,
    val packAddedMessage: String? = null,
    val smartReminderSuggestion: HabitCalculator.SmartReminderSuggestion? = null,
    val plantFeedbackMessage: String? = null
)

@HiltViewModel
class TasksViewModel @Inject constructor(
    private val habitRepository: HabitRepository,
    private val reminderScheduler: ReminderScheduler,
    private val currentDateProvider: CurrentDateProvider,
    private val prefsRepository: UserPreferencesRepository
) : ViewModel() {

    private val _editorState = MutableStateFlow<Pair<Boolean, Habit?>>(false to null)
    private val _selectedCategory = MutableStateFlow<HabitCategory?>(null)
    private val _pendingArchive = MutableStateFlow<Habit?>(null)
    private val _lastArchived = MutableStateFlow<Habit?>(null)
    private val _packMessage = MutableStateFlow<String?>(null)
    private val _plantMessage = MutableStateFlow<String?>(null)
    private val _dismissedSmartHabitIds = MutableStateFlow<Set<String>>(emptySet())

    private val overlayFlow = combine(
        _pendingArchive,
        _lastArchived,
        _packMessage,
        _plantMessage,
        _dismissedSmartHabitIds
    ) { pending, last, pack, plant, dismissed ->
        TasksOverlay(pending, last, pack, plant, dismissed)
    }

    val uiState: StateFlow<TasksUiState> = combine(
        habitRepository.observeActiveHabits(),
        habitRepository.observeAllLogs(),
        habitRepository.observeAllReminders(),
        combine(_selectedCategory, _editorState) { cat, editor -> cat to editor },
        overlayFlow
    ) { activeHabits, allLogs, allReminders, (selectedCategory, editorPair), overlay ->
        val (editorOpen, editingHabit) = editorPair
        val existingNames = activeHabits.map { it.name.lowercase() }.toSet()
        val remainingSuggestions = SuggestedHabits.items.filter { it.name.lowercase() !in existingNames }
        val remindersByHabit = allReminders.groupBy { it.habitId }
        val filteredHabits = if (selectedCategory == null) {
            activeHabits
        } else {
            activeHabits.filter { it.category == selectedCategory }
        }
        val smartCandidate = HabitCalculator.findSmartReminderSuggestion(
            habits = activeHabits.filter { it.id !in overlay.dismissedSmartHabitIds },
            logs = allLogs,
            remindersByHabit = remindersByHabit
        )

        TasksUiState(
            activeHabits = filteredHabits,
            allActiveHabits = activeHabits,
            remindersByHabit = remindersByHabit,
            selectedCategory = selectedCategory,
            suggestedHabits = remainingSuggestions,
            starterPacks = StarterPacks.packs,
            editingHabit = editingHabit,
            editingReminders = editingHabit?.let { remindersByHabit[it.id].orEmpty() }.orEmpty(),
            isEditorOpen = editorOpen,
            pendingArchiveHabit = overlay.pendingArchive,
            lastArchivedHabit = overlay.lastArchived,
            packAddedMessage = overlay.packMessage,
            smartReminderSuggestion = smartCandidate,
            plantFeedbackMessage = overlay.plantMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = TasksUiState()
    )

    fun selectCategory(category: HabitCategory?) {
        _selectedCategory.value = category
    }

    fun onAddHabitClicked() {
        _editorState.value = true to null
    }

    fun onEditHabitClicked(habit: Habit) {
        _editorState.value = true to habit
    }

    fun dismissEditor() {
        _editorState.value = false to null
    }

    fun quickAddHabit(rawText: String) {
        val parsed = QuickAddParser.parse(rawText) ?: return
        viewModelScope.launch {
            val today = currentDateProvider.today.value
            val reminders = if (parsed.reminderHour != null && parsed.reminderMinute != null) {
                listOf(ReminderSlotInput(parsed.reminderHour, parsed.reminderMinute))
            } else {
                emptyList()
            }
            val (created, savedReminders) = habitRepository.addHabit(
                name = parsed.name,
                icon = parsed.icon,
                colorHex = parsed.colorHex,
                repeatDays = parsed.repeatDays,
                type = parsed.type,
                targetValue = parsed.targetValue,
                unitLabel = parsed.unitLabel,
                timeOfDay = parsed.timeOfDay,
                category = parsed.category,
                reminders = reminders,
                createdDate = today
            )
            reminderScheduler.syncHabitReminders(created.id, emptyList(), savedReminders)
            val gotBonus = prefsRepository.grantDailyPlantBonusIfEligible(today)
            _plantMessage.value = if (gotBonus) {
                "🌱 Planted \"${created.name}\"! +5 XP"
            } else {
                "🌱 Planted \"${created.name}\"!"
            }
        }
    }

    fun clearPlantMessage() {
        _plantMessage.value = null
    }

    fun saveHabit(
        existingId: String?,
        name: String,
        icon: String,
        colorHex: Long,
        repeatDays: Int,
        type: HabitType,
        targetValue: Int,
        unitLabel: String,
        timeOfDay: TimeOfDay,
        category: HabitCategory,
        cueText: String,
        tinyVersion: String,
        stackAfterHabitId: String?,
        challengeDays: Int,
        reminders: List<ReminderSlotInput>
    ) {
        if (name.isBlank()) return
        viewModelScope.launch {
            if (existingId == null) {
                val (created, savedReminders) = habitRepository.addHabit(
                    name = name,
                    icon = icon,
                    colorHex = colorHex,
                    repeatDays = repeatDays,
                    type = type,
                    targetValue = targetValue,
                    unitLabel = unitLabel,
                    timeOfDay = timeOfDay,
                    category = category,
                    cueText = cueText,
                    tinyVersion = tinyVersion,
                    stackAfterHabitId = stackAfterHabitId,
                    challengeDays = challengeDays,
                    reminders = reminders,
                    createdDate = currentDateProvider.today.value
                )
                reminderScheduler.syncHabitReminders(created.id, emptyList(), savedReminders)
            } else {
                val oldReminders = habitRepository.getRemindersForHabit(existingId)
                val savedReminders = habitRepository.updateHabit(
                    habitId = existingId,
                    name = name,
                    icon = icon,
                    colorHex = colorHex,
                    repeatDays = repeatDays,
                    type = type,
                    targetValue = targetValue,
                    unitLabel = unitLabel,
                    timeOfDay = timeOfDay,
                    category = category,
                    cueText = cueText,
                    tinyVersion = tinyVersion,
                    stackAfterHabitId = stackAfterHabitId,
                    challengeDays = challengeDays,
                    reminders = reminders
                )
                reminderScheduler.syncHabitReminders(existingId, oldReminders, savedReminders)
            }
            _editorState.value = false to null
        }
    }

    fun duplicateHabit(habit: Habit) {
        viewModelScope.launch {
            val (copy, reminders) = habitRepository.duplicateHabit(habit, currentDateProvider.today.value)
            reminderScheduler.syncHabitReminders(copy.id, emptyList(), reminders)
            _packMessage.value = "Duplicated \"${habit.name}\""
        }
    }

    fun moveHabit(habitId: String, delta: Int) {
        val list = uiState.value.allActiveHabits.toMutableList()
        val idx = list.indexOfFirst { it.id == habitId }
        if (idx < 0) return
        val newIdx = (idx + delta).coerceIn(0, list.lastIndex)
        if (newIdx == idx) return
        val item = list.removeAt(idx)
        list.add(newIdx, item)
        viewModelScope.launch {
            habitRepository.reorderHabits(list.map { it.id })
        }
    }

    fun reorderHabits(orderedIds: List<String>) {
        viewModelScope.launch {
            habitRepository.reorderHabits(orderedIds)
        }
    }

    fun applySmartReminderSuggestion(suggestion: HabitCalculator.SmartReminderSuggestion) {
        viewModelScope.launch {
            val oldReminders = habitRepository.getRemindersForHabit(suggestion.habit.id)
            val saved = habitRepository.updateHabitReminderTimeSingle(
                habitId = suggestion.habit.id,
                hour = suggestion.suggestedHour,
                minute = suggestion.suggestedMinute
            )
            reminderScheduler.syncHabitReminders(suggestion.habit.id, oldReminders, saved)
            _dismissedSmartHabitIds.value = _dismissedSmartHabitIds.value + suggestion.habit.id
            _packMessage.value = "Updated reminder for \"${suggestion.habit.name}\""
        }
    }

    fun dismissSmartReminderSuggestion(habitId: String) {
        _dismissedSmartHabitIds.value = _dismissedSmartHabitIds.value + habitId
    }

    fun addSuggestedHabit(suggested: SuggestedHabit) {
        viewModelScope.launch {
            habitRepository.addHabit(
                name = suggested.name,
                icon = suggested.icon,
                colorHex = suggested.colorHex,
                repeatDays = suggested.repeatDays,
                type = suggested.type,
                targetValue = suggested.targetValue,
                unitLabel = suggested.unitLabel,
                timeOfDay = suggested.timeOfDay,
                category = suggested.category,
                cueText = suggested.cueText,
                tinyVersion = suggested.tinyVersion,
                createdDate = currentDateProvider.today.value
            )
        }
    }

    fun addStarterPack(pack: StarterPack) {
        viewModelScope.launch {
            habitRepository.addStarterPack(pack.habits, currentDateProvider.today.value)
            _packMessage.value = "Added ${pack.title} starter pack"
        }
    }

    fun clearPackMessage() {
        _packMessage.value = null
    }

    fun requestArchive(habit: Habit) {
        _pendingArchive.value = habit
    }

    fun dismissArchiveDialog() {
        _pendingArchive.value = null
    }

    fun confirmArchive() {
        val target = _pendingArchive.value ?: return
        _pendingArchive.value = null
        archiveHabitInternal(target)
    }

    fun archiveHabitDirect(habit: Habit) {
        archiveHabitInternal(habit)
    }

    private fun archiveHabitInternal(target: Habit) {
        viewModelScope.launch {
            habitRepository.archiveHabit(target.id, currentDateProvider.today.value)
            val reminders = habitRepository.getRemindersForHabit(target.id)
            reminders.forEach { reminderScheduler.cancelHabitReminder(it.id) }
            _lastArchived.value = target
        }
    }

    fun undoArchive(habitId: String) {
        viewModelScope.launch {
            habitRepository.restoreHabit(habitId)
            val reminders = habitRepository.getRemindersForHabit(habitId)
            reminders.filter { it.enabled }.forEach { reminderScheduler.scheduleHabitReminder(it) }
            _lastArchived.value = null
        }
    }

    fun clearLastArchived() {
        _lastArchived.value = null
    }
}

private data class TasksOverlay(
    val pendingArchive: Habit?,
    val lastArchived: Habit?,
    val packMessage: String?,
    val plantMessage: String?,
    val dismissedSmartHabitIds: Set<String>
)
