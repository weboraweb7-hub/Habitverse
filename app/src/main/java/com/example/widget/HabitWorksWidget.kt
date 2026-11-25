package com.example.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.CheckBox
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.MainActivity
import com.example.data.local.HabitDao
import com.example.data.local.HabitLogDao
import com.example.data.local.SkipDayDao
import com.example.data.local.toDomain
import com.example.data.repository.HabitRepository
import com.example.domain.model.Habit
import com.example.domain.usecase.HabitCalculator
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.LocalDate

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetDependenciesEntryPoint {
    fun habitDao(): HabitDao
    fun habitLogDao(): HabitLogDao
    fun skipDayDao(): SkipDayDao
    fun streakFreezeDao(): com.example.data.local.StreakFreezeDao
    fun habitRepository(): HabitRepository
}

object HabitWorksWidgetUpdater {
    suspend fun updateAll(context: Context) {
        runCatching {
            HabitWorksWidget().updateAll(context)
        }
        runCatching {
            StreakTodayWidget().updateAll(context)
        }
    }
}

data class WidgetHabitRow(
    val habit: Habit,
    val done: Boolean,
    val progressText: String
)

class HabitWorksWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetDependenciesEntryPoint::class.java
        )
        val today = LocalDate.now()
        val todayStr = today.toString()

        val allHabits = entryPoint.habitDao().getActiveHabitsOnce().map { it.toDomain() }
        val scheduledToday = HabitCalculator.scheduledHabitsForDate(allHabits, today)
        val logsToday = entryPoint.habitLogDao().getLogsForDateOnce(todayStr).associateBy { it.habitId }
        val isSkippedToday = entryPoint.skipDayDao().getAllSkipDaysOnce().any { it.date == todayStr }

        val rows = scheduledToday.map { habit ->
            val log = logsToday[habit.id]
            val done = log?.done ?: false
            val progressText = when (habit.type) {
                com.example.domain.model.HabitType.YES_NO -> ""
                com.example.domain.model.HabitType.COUNT -> {
                    val count = log?.progressValue ?: 0
                    val unit = habit.unitLabel.ifBlank { "x" }
                    "$count/${habit.targetValue} $unit"
                }
                com.example.domain.model.HabitType.TIMER -> {
                    val mins = (log?.progressValue ?: 0) / 60
                    "$mins/${habit.targetValue}m"
                }
            }
            WidgetHabitRow(habit = habit, done = done, progressText = progressText)
        }
        val doneCount = rows.count { it.done }

        provideContent {
            GlanceTheme {
                WidgetContent(
                    rows = rows,
                    doneCount = doneCount,
                    totalCount = rows.size,
                    isSkippedToday = isSkippedToday
                )
            }
        }
    }

    @Composable
    private fun WidgetContent(
        rows: List<WidgetHabitRow>,
        doneCount: Int,
        totalCount: Int,
        isSkippedToday: Boolean
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(20.dp)
                .padding(14.dp)
        ) {
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .clickable(actionStartActivity<MainActivity>()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "HabitWorks",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = GlanceModifier.defaultWeight()
                )
                Box(
                    modifier = GlanceModifier
                        .background(GlanceTheme.colors.primaryContainer)
                        .cornerRadius(12.dp)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (isSkippedToday) "Rest Day" else "$doneCount/$totalCount done",
                        style = TextStyle(
                            color = GlanceTheme.colors.onPrimaryContainer,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }

            Spacer(modifier = GlanceModifier.height(10.dp))

            if (rows.isEmpty()) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .clickable(actionStartActivity<MainActivity>()),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No habits scheduled today. Tap to add one.",
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 13.sp
                        )
                    )
                }
            } else {
                LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                    items(
                        items = rows,
                        itemId = { item -> item.habit.id.hashCode().toLong() }
                    ) { row ->
                        Row(
                            modifier = GlanceModifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(GlanceTheme.colors.surface)
                                .cornerRadius(12.dp)
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .clickable(
                                    actionRunCallback<ToggleHabitWidgetAction>(
                                        actionParametersOf(KEY_HABIT_ID to row.habit.id)
                                    )
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CheckBox(
                                checked = row.done,
                                onCheckedChange = actionRunCallback<ToggleHabitWidgetAction>(
                                    actionParametersOf(KEY_HABIT_ID to row.habit.id)
                                )
                            )
                            Spacer(modifier = GlanceModifier.width(8.dp))
                            Text(
                                text = "${row.habit.icon} ${row.habit.name}",
                                style = TextStyle(
                                    color = GlanceTheme.colors.onSurface,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                maxLines = 1,
                                modifier = GlanceModifier.defaultWeight()
                            )
                            if (row.progressText.isNotEmpty()) {
                                Spacer(modifier = GlanceModifier.width(6.dp))
                                Text(
                                    text = row.progressText,
                                    style = TextStyle(
                                        color = GlanceTheme.colors.primary,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    companion object {
        val KEY_HABIT_ID = ActionParameters.Key<String>("habit_id")
    }
}

class ToggleHabitWidgetAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters
    ) {
        val habitId = parameters[HabitWorksWidget.KEY_HABIT_ID] ?: return
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetDependenciesEntryPoint::class.java
        )
        entryPoint.habitRepository().toggleHabitLog(habitId, LocalDate.now())
    }
}

class HabitWorksWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HabitWorksWidget()
}
