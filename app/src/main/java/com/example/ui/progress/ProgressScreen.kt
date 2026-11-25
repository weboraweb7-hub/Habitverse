package com.example.ui.progress

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ads.AdBannerView
import com.example.ads.AdConfig
import com.example.domain.model.DailySheetRow
import com.example.domain.model.DayCompletion
import com.example.domain.model.HabitStats
import com.example.domain.model.LocalInsights
import com.example.domain.model.TimeRange
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val SHEET_DATE_FORMAT = DateTimeFormatter.ofPattern("MMM d")
private val MONTH_YEAR_FORMAT = DateTimeFormatter.ofPattern("MMMM yyyy")

@Composable
fun ProgressScreen(
    state: ProgressUiState,
    canRequestAds: Boolean,
    onSelectRange: (TimeRange) -> Unit,
    onSaveDayNote: (LocalDate, String) -> Unit,
    onOpenDayOnHome: (LocalDate) -> Unit,
    onExportCsv: () -> Intent
) {
    val context = LocalContext.current
    var editingNoteRow by remember { mutableStateOf<DailySheetRow?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "Progress",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                TimeRange.entries.forEach { range ->
                    FilterChip(
                        selected = state.selectedRange == range,
                        onClick = { onSelectRange(range) },
                        label = { Text(range.label) },
                        modifier = Modifier.testTag("range_chip_${range.name}")
                    )
                }
            }
        }

        // KPI Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                KpiCard(
                    title = "Current",
                    value = "🔥 ${state.currentStreak}d",
                    modifier = Modifier.weight(1f)
                )
                KpiCard(
                    title = "Best",
                    value = "🏆 ${state.bestStreak}d",
                    modifier = Modifier.weight(1f)
                )
                KpiCard(
                    title = "Rate",
                    value = "${state.completionPercent}%",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Weekly Recap ("Sunday Story")
        val recap = state.weeklyRecap
        if (recap != null) {
            item {
                WeeklyRecapSection(recap = recap)
            }
        }

        // On-Device Local Insights Card
        val insights = state.localInsights
        if (insights != null) {
            item {
                LocalInsightsCard(insights = insights)
            }
        }

        // 90-Day Consistency Sky
        if (state.skyStars.isNotEmpty()) {
            item {
                ConsistencySkyCard(
                    stars = state.skyStars,
                    onOpenDayOnHome = onOpenDayOnHome
                )
            }
        }

        // Weekly Bar Chart
        item {
            WeeklyBarChartCard(bars = state.weeklyBars)
        }

        // Monthly Calendar Heat-map
        item {
            MonthlyHeatmapCard(
                yearMonth = state.currentMonth,
                today = state.today,
                completions = state.monthCompletions
            )
        }

        // Habit Strength
        if (state.habitStrengths.isNotEmpty()) {
            item {
                Text(
                    text = "Habit Strength",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            items(state.habitStrengths, key = { "strength_${it.habit.id}" }) { strength ->
                HabitStrengthRowCard(strength = strength)
            }
        }

        // Badge Vault (24 Badges)
        item {
            BadgeVaultSection(unlockedBadgesById = state.unlockedBadgesById)
        }

        // Per-Habit Stats
        if (state.perHabitStats.isNotEmpty()) {
            item {
                Text(
                    text = "By Habit",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
            items(state.perHabitStats, key = { "stat_${it.habit.id}" }) { stat ->
                PerHabitStatCard(stat = stat)
            }
        }

        // Daily Sheet Header + Export CSV
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Daily Sheet",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Tap a day to view on Home, or add a note",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = {
                        val intent = onExportCsv()
                        context.startActivity(Intent.createChooser(intent, "Export Habits CSV"))
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.testTag("export_csv_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.export_csv))
                }
            }
        }

        items(state.dailySheetRows, key = { "sheet_${it.date}" }) { row ->
            DailySheetRowItem(
                row = row,
                onRowClick = { onOpenDayOnHome(row.date) },
                onEditNote = { editingNoteRow = row }
            )
        }

        item {
            AdBannerView(
                adUnitId = AdConfig.BANNER_PROGRESS_AD_UNIT_ID,
                canRequestAds = canRequestAds
            )
        }
    }

    val noteTarget = editingNoteRow
    if (noteTarget != null) {
        DayNoteDialog(
            row = noteTarget,
            onDismiss = { editingNoteRow = null },
            onSave = { updatedNote ->
                onSaveDayNote(noteTarget.date, updatedNote)
                editingNoteRow = null
            }
        )
    }
}

@Composable
private fun LocalInsightsCard(insights: LocalInsights) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("local_insights_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Local Insights",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "On-device",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Best weekday",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${insights.bestWeekdayLabel} (${insights.bestWeekdayPercent}%)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "Weekly summary",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        val deltaSign = if (insights.weeklyDeltaPercent >= 0) "+" else ""
                        Text(
                            text = "${insights.weeklyPercent}% ($deltaSign${insights.weeklyDeltaPercent}%)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            val missedText = if (insights.mostMissedHabitName != null && insights.mostMissedPercent != null) {
                "Most missed lately: ${insights.mostMissedHabitName} (${insights.mostMissedPercent}% completion) · ${insights.weeklyCompleted}/${insights.weeklyScheduled} check-ins this week."
            } else {
                "All active habits are staying consistent · ${insights.weeklyCompleted}/${insights.weeklyScheduled} check-ins this week."
            }
            Text(
                text = missedText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!insights.moodInsightText.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "✨ ${insights.moodInsightText}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                            .testTag("mood_insight_text")
                    )
                }
            }
        }
    }
}

@Composable
private fun KpiCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun WeeklyBarChartCard(bars: List<DayCompletion>) {
    val barColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val skipColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f)

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Last 7 Days",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
            ) {
                val count = bars.size.coerceAtLeast(1)
                val slotWidth = size.width / count
                val barWidth = (slotWidth * 0.44f).coerceAtMost(28.dp.toPx())

                bars.forEachIndexed { idx, item ->
                    val left = idx * slotWidth + (slotWidth - barWidth) / 2f
                    drawRoundRect(
                        color = trackColor,
                        topLeft = Offset(left, 0f),
                        size = Size(barWidth, size.height),
                        cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
                    )
                    val fillHeight = (size.height * item.ratio).coerceAtLeast(
                        if (item.completedCount > 0 || item.isSkipped) 8.dp.toPx() else 0f
                    )
                    if (fillHeight > 0f) {
                        drawRoundRect(
                            color = if (item.isSkipped && item.completedCount == 0) skipColor else barColor,
                            topLeft = Offset(left, size.height - fillHeight),
                            size = Size(barWidth, fillHeight),
                            cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx())
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                bars.forEach { item ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = item.date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = when {
                                item.isSkipped -> "Rest"
                                item.isFrozen -> "Ice"
                                else -> "${item.percent}%"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MonthlyHeatmapCard(
    yearMonth: YearMonth,
    today: LocalDate,
    completions: Map<LocalDate, DayCompletion>
) {
    val firstDay = yearMonth.atDay(1)
    val leadingBlanks = firstDay.dayOfWeek.value - 1
    val totalDays = yearMonth.lengthOfMonth()
    val cells: List<LocalDate?> = buildList {
        repeat(leadingBlanks) { add(null) }
        for (day in 1..totalDays) add(yearMonth.atDay(day))
        while (size % 7 != 0) add(null)
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = yearMonth.format(MONTH_YEAR_FORMAT),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                DayOfWeek.entries.forEach { dow ->
                    Text(
                        text = dow.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            cells.chunked(7).forEach { week ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    week.forEach { date ->
                        if (date == null) {
                            Spacer(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                            )
                        } else {
                            val comp = completions[date]
                            val ratio = if (date.isAfter(today)) 0f else (comp?.ratio ?: 0f)
                            val cellColor = when {
                                date.isAfter(today) -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                comp?.isSkipped == true -> MaterialTheme.colorScheme.tertiaryContainer
                                comp?.isFrozen == true -> Color(0xFFBAE6FD)
                                ratio <= 0f -> MaterialTheme.colorScheme.surfaceVariant
                                ratio < 0.5f -> MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
                                ratio < 1f -> MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)
                                else -> MaterialTheme.colorScheme.primary
                            }
                            val textColor = when {
                                comp?.isFrozen == true && !date.isAfter(today) && comp.isSkipped != true -> Color(0xFF0C4A6E)
                                ratio >= 0.65f && !date.isAfter(today) && comp?.isSkipped != true -> MaterialTheme.colorScheme.onPrimary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(cellColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = date.dayOfMonth.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = textColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PerHabitStatCard(stat: HabitStats) {
    val accent = Color(stat.habit.colorHex)
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(stat.habit.icon)
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stat.habit.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "🔥 ${stat.currentStreak}d · ${stat.completionPercent}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { stat.completionPercent / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = accent,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun DailySheetRowItem(
    row: DailySheetRow,
    onRowClick: () -> Unit,
    onEditNote: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onRowClick)
            .testTag("daily_sheet_row_${row.date}")
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = row.date.format(SHEET_DATE_FORMAT),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.width(64.dp)
                )
                Text(
                    text = "${row.doneCount}/${row.totalCount}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(52.dp)
                )
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = when {
                        row.isSkipped -> MaterialTheme.colorScheme.tertiaryContainer
                        row.isFrozen -> Color(0xFFBAE6FD)
                        else -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    }
                ) {
                    Text(
                        text = when {
                            row.isSkipped -> "Rest"
                            row.isFrozen -> "Freeze"
                            else -> "${row.percent}%"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = when {
                            row.isSkipped -> MaterialTheme.colorScheme.onTertiaryContainer
                            row.isFrozen -> Color(0xFF0C4A6E)
                            else -> MaterialTheme.colorScheme.onPrimaryContainer
                        },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                val moodGlyph = when (row.mood) {
                    1 -> "😫 "
                    2 -> "😕 "
                    3 -> "😐 "
                    4 -> "🙂 "
                    5 -> "🤩 "
                    else -> ""
                }
                Text(
                    text = if (row.note.isBlank()) {
                        if (moodGlyph.isNotEmpty()) "${moodGlyph.trim()} · Add note..." else "Add note..."
                    } else {
                        "$moodGlyph${row.note}"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (row.note.isBlank() && moodGlyph.isEmpty()) {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onEditNote,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.EditNote,
                        contentDescription = "Edit note for ${row.date}",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
        }
    }
}

@Composable
private fun DayNoteDialog(
    row: DailySheetRow,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var noteText by remember(row) { mutableStateOf(row.note) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Note for ${row.date.format(SHEET_DATE_FORMAT)}") },
        text = {
            OutlinedTextField(
                value = noteText,
                onValueChange = { if (it.length <= 280) noteText = it },
                placeholder = { Text("How did your habits go today?") },
                maxLines = 3,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { onSave(noteText) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
