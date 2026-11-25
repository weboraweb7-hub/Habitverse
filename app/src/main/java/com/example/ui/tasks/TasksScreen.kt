package com.example.ui.tasks

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.repository.ReminderSlotInput
import com.example.domain.model.Habit
import com.example.domain.model.HabitCategory
import com.example.domain.model.HabitPalette
import com.example.domain.model.HabitReminder
import com.example.domain.model.HabitType
import com.example.domain.model.RepeatDays
import com.example.domain.model.StarterPack
import com.example.domain.model.SuggestedHabit
import com.example.domain.model.TimeOfDay
import com.example.domain.usecase.HabitCalculator
import com.example.domain.usecase.QuickAddParser
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val REMINDER_DISPLAY_FMT = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())
private val CHALLENGE_DURATIONS = listOf(0, 7, 21, 30, 66)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    state: TasksUiState,
    onSelectCategory: (HabitCategory?) -> Unit,
    onAddClicked: () -> Unit,
    onEditClicked: (Habit) -> Unit,
    onDismissEditor: () -> Unit,
    onQuickAddHabit: (String) -> Unit,
    onClearPlantMessage: () -> Unit,
    onSaveHabit: (
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
    ) -> Unit,
    onDuplicateHabit: (Habit) -> Unit,
    onMoveHabit: (String, Int) -> Unit,
    onApplySmartReminder: (HabitCalculator.SmartReminderSuggestion) -> Unit,
    onDismissSmartReminder: (String) -> Unit,
    onAddSuggested: (SuggestedHabit) -> Unit,
    onAddStarterPack: (StarterPack) -> Unit,
    onClearPackMessage: () -> Unit,
    onRequestArchive: (Habit) -> Unit,
    onSwipeArchive: (Habit) -> Unit,
    onConfirmArchive: () -> Unit,
    onDismissArchive: () -> Unit,
    onUndoArchive: (String) -> Unit,
    onClearLastArchived: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val habitsById = remember(state.allActiveHabits) {
        state.allActiveHabits.associateBy { it.id }
    }

    LaunchedEffect(state.lastArchivedHabit) {
        val archived = state.lastArchivedHabit ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = "Archived \"${archived.name}\"",
            actionLabel = "Undo",
            duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed) {
            onUndoArchive(archived.id)
        } else {
            onClearLastArchived()
        }
    }

    LaunchedEffect(state.packAddedMessage) {
        val msg = state.packAddedMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = msg, duration = SnackbarDuration.Short)
        onClearPackMessage()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddClicked,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.add_habit)) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("add_habit_fab")
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column {
                    Text(
                        text = "Your Habits",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "${state.activeHabits.size} active · Long-press to duplicate or reorder · Swipe to archive",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Quick-Add Natural Language Bar
            item {
                QuickAddBar(
                    plantMessage = state.plantFeedbackMessage,
                    onPlantHabit = onQuickAddHabit,
                    onClearPlantMessage = onClearPlantMessage
                )
            }

            // Smart Reminder Suggestion
            val suggestion = state.smartReminderSuggestion
            if (suggestion != null) {
                item {
                    SmartReminderCard(
                        suggestion = suggestion,
                        onApply = { onApplySmartReminder(suggestion) },
                        onDismiss = { onDismissSmartReminder(suggestion.habit.id) }
                    )
                }
            }

            // Category Filter Chips
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    item {
                        FilterChip(
                            selected = state.selectedCategory == null,
                            onClick = { onSelectCategory(null) },
                            label = { Text("All") },
                            modifier = Modifier.testTag("category_chip_all")
                        )
                    }
                    items(HabitCategory.entries, key = { it.name }) { category ->
                        FilterChip(
                            selected = state.selectedCategory == category,
                            onClick = { onSelectCategory(category) },
                            label = { Text(category.label) },
                            modifier = Modifier.testTag("category_chip_${category.name}")
                        )
                    }
                }
            }

            if (state.activeHabits.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(20.dp)) {
                            Text(
                                text = if (state.selectedCategory == null) {
                                    "No habits yet"
                                } else {
                                    "No ${state.selectedCategory.label} habits yet"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Use Quick-Add above, tap + Add habit, or pick a Starter Pack below.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                itemsIndexed(state.activeHabits, key = { _, h -> h.id }) { index, habit ->
                    val stackParent = habit.stackAfterHabitId?.let { habitsById[it] }
                    ManageHabitCard(
                        habit = habit,
                        stackParentName = stackParent?.name,
                        reminders = state.remindersByHabit[habit.id].orEmpty(),
                        canMoveUp = index > 0 && state.selectedCategory == null,
                        canMoveDown = index < state.activeHabits.lastIndex && state.selectedCategory == null,
                        onEdit = { onEditClicked(habit) },
                        onArchiveClick = { onRequestArchive(habit) },
                        onSwipeArchive = { onSwipeArchive(habit) },
                        onDuplicate = { onDuplicateHabit(habit) },
                        onMoveUp = { onMoveHabit(habit.id, -1) },
                        onMoveDown = { onMoveHabit(habit.id, 1) }
                    )
                }
            }

            // Starter Packs Section
            item {
                StarterPacksSection(
                    packs = state.starterPacks,
                    onAddPack = onAddStarterPack
                )
            }

            if (state.suggestedHabits.isNotEmpty()) {
                item {
                    SuggestedHabitsCard(
                        suggestions = state.suggestedHabits,
                        onAddSuggested = onAddSuggested
                    )
                }
            }
        }
    }

    if (state.isEditorOpen) {
        HabitEditorBottomSheet(
            editingHabit = state.editingHabit,
            allActiveHabits = state.allActiveHabits,
            initialReminders = state.editingReminders,
            onDismiss = onDismissEditor,
            onSave = { name, icon, colorHex, repeatDays, type, targetValue, unitLabel, timeOfDay, category, cueText, tinyVersion, stackAfterHabitId, challengeDays, reminders ->
                onSaveHabit(
                    state.editingHabit?.id,
                    name,
                    icon,
                    colorHex,
                    repeatDays,
                    type,
                    targetValue,
                    unitLabel,
                    timeOfDay,
                    category,
                    cueText,
                    tinyVersion,
                    stackAfterHabitId,
                    challengeDays,
                    reminders
                )
            }
        )
    }

    val archiveTarget = state.pendingArchiveHabit
    if (archiveTarget != null) {
        AlertDialog(
            onDismissRequest = onDismissArchive,
            title = { Text("Archive \"${archiveTarget.name}\"?") },
            text = {
                Text(
                    "This removes the habit from today and future days while keeping your past completion history intact."
                )
            },
            confirmButton = {
                Button(
                    onClick = onConfirmArchive,
                    modifier = Modifier.testTag("confirm_archive_button")
                ) {
                    Text("Archive")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissArchive) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickAddBar(
    plantMessage: String?,
    onPlantHabit: (String) -> Unit,
    onClearPlantMessage: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    val preview = remember(input) { QuickAddParser.parse(input) }
    val haptic = LocalHapticFeedback.current

    fun submit() {
        if (input.isNotBlank()) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onPlantHabit(input)
            input = ""
        }
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { if (it.length <= 120) input = it },
                    placeholder = { Text("Try \"Drink 8 glasses at 9am daily\"") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("quick_add_input")
                )
                Button(
                    onClick = { submit() },
                    enabled = input.isNotBlank(),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .heightIn(min = 52.dp)
                        .testTag("quick_add_submit_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Spa,
                        contentDescription = "Plant habit",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Plant")
                }
            }

            AnimatedVisibility(visible = preview != null) {
                if (preview != null) {
                    FlowRow(
                        modifier = Modifier.padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        PreviewChip("${preview.icon} ${preview.name}")
                        if (preview.type != HabitType.YES_NO) {
                            val unit = if (preview.type == HabitType.TIMER) "min" else preview.unitLabel
                            PreviewChip("${preview.targetValue} $unit")
                        }
                        PreviewChip("${preview.timeOfDay.icon} ${preview.timeOfDay.label}")
                        PreviewChip(RepeatDays.formatSummary(preview.repeatDays))
                        if (preview.reminderHour != null && preview.reminderMinute != null) {
                            val formatted = LocalTime.of(preview.reminderHour, preview.reminderMinute)
                                .format(REMINDER_DISPLAY_FMT)
                            PreviewChip("⏰ $formatted")
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = !plantMessage.isNullOrBlank(),
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                if (!plantMessage.isNullOrBlank()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = plantMessage,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onClearPlantMessage) {
                            Text("OK")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewChip(text: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun SmartReminderCard(
    suggestion: HabitCalculator.SmartReminderSuggestion,
    onApply: () -> Unit,
    onDismiss: () -> Unit
) {
    val suggestedFormatted = LocalTime.of(suggestion.suggestedHour, suggestion.suggestedMinute)
        .format(REMINDER_DISPLAY_FMT)
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("smart_reminder_card")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Smart reminder tip",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "You usually complete ${suggestion.habit.name} around $suggestedFormatted. Shift reminder?",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            TextButton(onClick = onApply, modifier = Modifier.testTag("apply_smart_reminder_button")) {
                Text("Set $suggestedFormatted")
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = "Dismiss tip", modifier = Modifier.size(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ManageHabitCard(
    habit: Habit,
    stackParentName: String?,
    reminders: List<HabitReminder>,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEdit: () -> Unit,
    onArchiveClick: () -> Unit,
    onSwipeArchive: () -> Unit,
    onDuplicate: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    val accent = Color(habit.colorHex)
    var showMenu by remember { mutableStateOf(false) }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onSwipeArchive()
                true
            } else {
                false
            }
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Archive",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = "Archive habit",
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    ) {
        Box {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .combinedClickable(
                        onClick = onEdit,
                        onLongClick = { showMenu = true }
                    )
                    .testTag("manage_habit_card_${habit.id}")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = habit.icon, style = MaterialTheme.typography.titleLarge)
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = habit.name,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (habit.cueText.isNotBlank()) {
                            Text(
                                text = "Cue: ${habit.cueText}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        val reminderSummary = if (reminders.isNotEmpty()) {
                            reminders.joinToString(", ") {
                                LocalTime.of(it.hour, it.minute).format(REMINDER_DISPLAY_FMT)
                            }
                        } else {
                            habit.reminderTime
                        }
                        val scheduleText = buildString {
                            append("${habit.timeOfDay.label} · ${habit.category.label}")
                            if (habit.type != HabitType.YES_NO) {
                                append(" · ${habit.formatTargetLabel()}")
                            }
                            append(" · ${RepeatDays.formatSummary(habit.repeatDays)}")
                            if (habit.challengeDays > 0) {
                                append(" · 🏁 ${habit.challengeDays}d")
                            }
                            if (!reminderSummary.isNullOrBlank()) {
                                append(" · ⏰ $reminderSummary")
                            }
                        }
                        Text(
                            text = scheduleText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!stackParentName.isNullOrBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Link,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Stacked after $stackParentName",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("edit_habit_${habit.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Edit,
                            contentDescription = "Edit ${habit.name}",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(
                        onClick = onArchiveClick,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("archive_habit_${habit.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = "Remove ${habit.name}",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Duplicate habit") },
                    leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
                    onClick = {
                        showMenu = false
                        onDuplicate()
                    }
                )
                if (canMoveUp) {
                    DropdownMenuItem(
                        text = { Text("Move up") },
                        leadingIcon = { Icon(Icons.Rounded.ArrowUpward, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onMoveUp()
                        }
                    )
                }
                if (canMoveDown) {
                    DropdownMenuItem(
                        text = { Text("Move down") },
                        leadingIcon = { Icon(Icons.Rounded.ArrowDownward, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onMoveDown()
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Archive") },
                    leadingIcon = { Icon(Icons.Outlined.DeleteOutline, contentDescription = null) },
                    onClick = {
                        showMenu = false
                        onArchiveClick()
                    }
                )
            }
        }
    }
}

@Composable
private fun StarterPacksSection(
    packs: List<StarterPack>,
    onAddPack: (StarterPack) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Starter Packs",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "Add a complete routine in one tap.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(10.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(packs, key = { it.id }) { pack ->
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                    ),
                    modifier = Modifier
                        .width(210.dp)
                        .testTag("starter_pack_${pack.id}")
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "${pack.icon} ${pack.title}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = pack.subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            minLines = 2
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = { onAddPack(pack) },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("add_pack_button_${pack.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add ${pack.habits.size} habits")
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SuggestedHabitsCard(
    suggestions: List<SuggestedHabit>,
    onAddSuggested: (SuggestedHabit) -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Quick-add ideas",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tap to add a proven daily habit.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                suggestions.forEach { suggestion ->
                    AssistChip(
                        onClick = { onAddSuggested(suggestion) },
                        label = { Text("${suggestion.icon} ${suggestion.name}") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        modifier = Modifier.testTag("suggested_habit_${suggestion.name}")
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun HabitEditorBottomSheet(
    editingHabit: Habit?,
    allActiveHabits: List<Habit>,
    initialReminders: List<HabitReminder>,
    onDismiss: () -> Unit,
    onSave: (
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
    ) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember(editingHabit) { mutableStateOf(editingHabit?.name.orEmpty()) }
    var selectedIcon by remember(editingHabit) {
        mutableStateOf(editingHabit?.icon ?: HabitPalette.icons.first())
    }
    var selectedColor by remember(editingHabit) {
        mutableLongStateOf(editingHabit?.colorHex ?: HabitPalette.colors.first())
    }
    var repeatDays by remember(editingHabit) {
        mutableIntStateOf(editingHabit?.repeatDays ?: RepeatDays.ALL)
    }
    var selectedType by remember(editingHabit) {
        mutableStateOf(editingHabit?.type ?: HabitType.YES_NO)
    }
    var targetText by remember(editingHabit) {
        mutableStateOf(
            when (editingHabit?.type) {
                HabitType.COUNT -> editingHabit.targetValue.toString()
                HabitType.TIMER -> editingHabit.targetValue.toString()
                else -> "8"
            }
        )
    }
    var unitLabel by remember(editingHabit) {
        mutableStateOf(editingHabit?.unitLabel?.ifBlank { "" } ?: "")
    }
    var selectedTimeOfDay by remember(editingHabit) {
        mutableStateOf(editingHabit?.timeOfDay ?: TimeOfDay.MORNING)
    }
    var selectedCategory by remember(editingHabit) {
        mutableStateOf(editingHabit?.category ?: HabitCategory.GENERAL)
    }
    var cueText by remember(editingHabit) { mutableStateOf(editingHabit?.cueText.orEmpty()) }
    var tinyVersion by remember(editingHabit) { mutableStateOf(editingHabit?.tinyVersion.orEmpty()) }
    var stackAfterId by remember(editingHabit) { mutableStateOf(editingHabit?.stackAfterHabitId) }
    var challengeDays by remember(editingHabit) { mutableIntStateOf(editingHabit?.challengeDays ?: 0) }

    var reminderSlots by remember(editingHabit, initialReminders) {
        mutableStateOf(
            if (initialReminders.isNotEmpty()) {
                initialReminders.map { ReminderSlotInput(it.hour, it.minute) }
            } else {
                val parsed = editingHabit?.reminderTime?.split(":")
                val h = parsed?.getOrNull(0)?.toIntOrNull()
                val m = parsed?.getOrNull(1)?.toIntOrNull()
                if (h != null && m != null) listOf(ReminderSlotInput(h, m)) else emptyList()
            }
        )
    }
    var showTimePicker by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            showTimePicker = true
        }
    }

    val stackCandidates = remember(allActiveHabits, editingHabit) {
        allActiveHabits.filter { candidate ->
            if (editingHabit == null) true
            else candidate.id != editingHabit.id && candidate.stackAfterHabitId != editingHabit.id
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Text(
                text = if (editingHabit == null) {
                    stringResource(R.string.add_habit)
                } else {
                    stringResource(R.string.edit_habit)
                },
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(14.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 80) name = it },
                label = { Text("Habit name") },
                placeholder = { Text("e.g., Drink water") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("habit_name_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = cueText,
                onValueChange = { if (it.length <= 120) cueText = it },
                label = { Text("Cue (optional)") },
                placeholder = { Text("After I pour morning coffee...") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("habit_cue_input")
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = tinyVersion,
                onValueChange = { if (it.length <= 100) tinyVersion = it },
                label = { Text("Tiny version (optional)") },
                placeholder = { Text("Make it so small you can't say no (e.g., 2 mins)") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("habit_tiny_input")
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Habit Type
            Text("Habit type", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                HabitType.entries.forEach { type ->
                    FilterChip(
                        selected = selectedType == type,
                        onClick = {
                            selectedType = type
                            if (type == HabitType.TIMER && (targetText.toIntOrNull() ?: 0) < 1) {
                                targetText = "15"
                            } else if (type == HabitType.COUNT && (targetText.toIntOrNull() ?: 0) < 1) {
                                targetText = "8"
                            }
                        },
                        label = { Text(type.label) },
                        modifier = Modifier.testTag("habit_type_${type.name}")
                    )
                }
            }

            if (selectedType != HabitType.YES_NO) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = targetText,
                        onValueChange = { input ->
                            val digits = input.filter { it.isDigit() }.take(3)
                            targetText = digits
                        },
                        label = {
                            Text(if (selectedType == HabitType.TIMER) "Minutes" else "Target count")
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("habit_target_input")
                    )
                    if (selectedType == HabitType.COUNT) {
                        OutlinedTextField(
                            value = unitLabel,
                            onValueChange = { if (it.length <= 24) unitLabel = it },
                            label = { Text("Unit (optional)") },
                            placeholder = { Text("glasses") },
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(1.2f)
                                .testTag("habit_unit_input")
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Timed Challenge
            Text("Timed challenge (optional)", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CHALLENGE_DURATIONS.forEach { days ->
                    FilterChip(
                        selected = challengeDays == days,
                        onClick = { challengeDays = days },
                        label = { Text(if (days == 0) "No limit" else "$days days") },
                        modifier = Modifier.testTag("challenge_days_$days")
                    )
                }
            }

            if (stackCandidates.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Text("Habit stacking (do right after)", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = stackAfterId == null,
                        onClick = { stackAfterId = null },
                        label = { Text("Standalone") }
                    )
                    stackCandidates.take(8).forEach { candidate ->
                        FilterChip(
                            selected = stackAfterId == candidate.id,
                            onClick = { stackAfterId = candidate.id },
                            label = { Text("${candidate.icon} ${candidate.name}") }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Time of Day
            Text("Time of day", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                TimeOfDay.entries.forEach { slot ->
                    FilterChip(
                        selected = selectedTimeOfDay == slot,
                        onClick = { selectedTimeOfDay = slot },
                        label = { Text("${slot.icon} ${slot.label}") },
                        modifier = Modifier.testTag("time_of_day_${slot.name}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Category
            Text("Category", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                HabitCategory.entries.forEach { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        label = { Text(category.label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text("Icon", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HabitPalette.icons.forEach { emoji ->
                    val isSelected = emoji == selectedIcon
                    Surface(
                        onClick = { selectedIcon = emoji },
                        shape = CircleShape,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        },
                        border = if (isSelected) {
                            androidx.compose.foundation.BorderStroke(
                                2.dp,
                                MaterialTheme.colorScheme.primary
                            )
                        } else null,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(text = emoji, style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text("Color", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HabitPalette.colors.forEach { colorHex ->
                    val isSelected = colorHex == selectedColor
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color(colorHex))
                            .then(
                                if (isSelected) {
                                    Modifier.border(
                                        3.dp,
                                        MaterialTheme.colorScheme.onSurface,
                                        CircleShape
                                    )
                                } else Modifier
                            )
                            .clickable { selectedColor = colorHex },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Selected color",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Repeat days", style = MaterialTheme.typography.titleMedium)
                FilterChip(
                    selected = repeatDays == RepeatDays.ALL,
                    onClick = { repeatDays = RepeatDays.ALL },
                    label = { Text("Daily") }
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DayOfWeek.entries.forEach { day ->
                    val active = RepeatDays.isScheduledOn(repeatDays, day)
                    val label = day.getDisplayName(TextStyle.NARROW, Locale.getDefault())
                    Surface(
                        onClick = { repeatDays = RepeatDays.toggleDay(repeatDays, day) },
                        shape = CircleShape,
                        color = if (active) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        modifier = Modifier.size(42.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (active) {
                                    MaterialTheme.colorScheme.onPrimary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Multiple Reminders per Habit
            Text("Reminders", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(6.dp))
            if (reminderSlots.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    reminderSlots.forEach { slot ->
                        val formatted = LocalTime.of(slot.hour, slot.minute).format(REMINDER_DISPLAY_FMT)
                        InputChip(
                            selected = true,
                            onClick = { reminderSlots = reminderSlots - slot },
                            label = { Text(formatted) },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Remove reminder $formatted",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            OutlinedButton(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            showTimePicker = true
                        } else {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    } else {
                        showTimePicker = true
                    }
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("add_habit_reminder_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.AccessTime,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add reminder time")
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = {
                    val target = if (selectedType == HabitType.YES_NO) {
                        1
                    } else {
                        (targetText.toIntOrNull() ?: 1).coerceAtLeast(1)
                    }
                    val finalUnit = when (selectedType) {
                        HabitType.YES_NO -> ""
                        HabitType.COUNT -> unitLabel.trim()
                        HabitType.TIMER -> "min"
                    }
                    onSave(
                        name,
                        selectedIcon,
                        selectedColor,
                        repeatDays,
                        selectedType,
                        target,
                        finalUnit,
                        selectedTimeOfDay,
                        selectedCategory,
                        cueText,
                        tinyVersion,
                        stackAfterId,
                        challengeDays,
                        reminderSlots
                    )
                },
                enabled = name.isNotBlank(),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .testTag("save_habit_button")
            ) {
                Text(if (editingHabit == null) "Save habit" else "Update habit")
            }
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = 8,
            initialMinute = 30,
            is24Hour = false
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Add habit reminder") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                Button(
                    onClick = {
                        val slot = ReminderSlotInput(timePickerState.hour, timePickerState.minute)
                        if (slot !in reminderSlots) {
                            reminderSlots = reminderSlots + slot
                        }
                        showTimePicker = false
                    }
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
