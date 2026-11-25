package com.example.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
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
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.example.MainActivity
import com.example.data.local.toDomain
import com.example.domain.usecase.HabitCalculator
import dagger.hilt.android.EntryPointAccessors
import java.time.LocalDate

class StreakTodayWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetDependenciesEntryPoint::class.java
        )
        val today = LocalDate.now()
        val allHabits = entryPoint.habitDao().getAllHabitsOnce().map { it.toDomain() }
        val allLogs = entryPoint.habitLogDao().getAllLogsOnce().map { it.toDomain() }
        val skippedDates = entryPoint.skipDayDao().getAllSkipDaysOnce()
            .mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }
            .toSet()
        val frozenDates = entryPoint.streakFreezeDao().getAllFreezesOnce()
            .mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }
            .toSet()

        val logsByKey = HabitCalculator.indexLogs(allLogs)
        val todayComp = HabitCalculator.dayCompletion(allHabits, logsByKey, today, skippedDates, frozenDates)
        val currentStreak = HabitCalculator.calculateCurrentStreak(
            habits = allHabits,
            logs = allLogs,
            today = today,
            skippedDates = skippedDates,
            frozenDates = frozenDates
        )

        provideContent {
            GlanceTheme {
                StreakWidgetContent(
                    currentStreak = currentStreak,
                    completedCount = todayComp.completedCount,
                    scheduledCount = todayComp.scheduledCount,
                    isSkipped = todayComp.isSkipped,
                    isFrozen = todayComp.isFrozen
                )
            }
        }
    }

    @Composable
    private fun StreakWidgetContent(
        currentStreak: Int,
        completedCount: Int,
        scheduledCount: Int,
        isSkipped: Boolean,
        isFrozen: Boolean
    ) {
        val percent = if (scheduledCount == 0) 0 else ((completedCount * 100) / scheduledCount).coerceIn(0, 100)
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.widgetBackground)
                .cornerRadius(22.dp)
                .padding(14.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = GlanceModifier
                        .background(GlanceTheme.colors.primaryContainer)
                        .cornerRadius(12.dp)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "🔥 ${currentStreak}d",
                        style = TextStyle(
                            color = GlanceTheme.colors.onPrimaryContainer,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
                Spacer(modifier = GlanceModifier.defaultWeight())
                Text(
                    text = "$percent%",
                    style = TextStyle(
                        color = GlanceTheme.colors.primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            Spacer(modifier = GlanceModifier.height(10.dp))

            val headline = when {
                isSkipped -> "Rest Day 🏖️"
                isFrozen -> "Protected ❄️"
                scheduledCount == 0 -> "No tasks"
                completedCount >= scheduledCount -> "All done! 🎉"
                else -> "$completedCount / $scheduledCount"
            }
            Text(
                text = headline,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )

            Spacer(modifier = GlanceModifier.height(4.dp))

            val subline = when {
                isSkipped -> "Streak stays safe"
                scheduledCount == 0 -> "Tap to add a habit"
                completedCount >= scheduledCount -> "Today is complete"
                else -> "${scheduledCount - completedCount} left today"
            }
            Text(
                text = subline,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 12.sp
                ),
                maxLines = 1
            )
        }
    }
}

class StreakTodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StreakTodayWidget()
}
