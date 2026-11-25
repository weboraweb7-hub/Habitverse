package com.example.ui.home

import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.domain.model.DayCompletionStatus
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

private val MONTH_CHIP_FORMATTER = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
private val RAIL_DAY_WIDTH = 48.dp

enum class RailSyncSource {
    NONE,
    RAIL,
    PAGER,
    TAP
}

class DayStatusLruCache(private val maxEntries: Int = 180) {
    private var lastRevision: Int = -1
    private val cache = object : LinkedHashMap<LocalDate, DayCompletionStatus>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<LocalDate, DayCompletionStatus>?): Boolean {
            return size > maxEntries
        }
    }

    fun getOrCompute(
        revision: Int,
        date: LocalDate,
        compute: (LocalDate) -> DayCompletionStatus
    ): DayCompletionStatus {
        if (revision != lastRevision) {
            cache.clear()
            lastRevision = revision
        }
        return cache.getOrPut(date) { compute(date) }
    }
}

@Composable
fun rememberReducedMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        }.getOrDefault(false)
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TimeRailSection(
    railState: LazyListState,
    today: LocalDate,
    displayedDate: LocalDate,
    statusLookup: (LocalDate) -> DayCompletionStatus,
    onLiveCenteredDateChanged: (LocalDate?) -> Unit,
    onCommitRailDate: (LocalDate) -> Unit,
    onTapDay: (LocalDate) -> Unit,
    onJumpToToday: () -> Unit,
    onUserRailScrollStarted: () -> Unit,
    isExternalSyncInProgress: () -> Boolean
) {
    val minDate = remember(today) { TimeRailMath.minRailDate(today) }
    val maxDate = remember(today) { TimeRailMath.maxRailDate(today) }
    val totalDays = remember { TimeRailMath.totalRailDaysCount() }
    val reducedMotion = rememberReducedMotion()
    val haptic = LocalHapticFeedback.current

    var showMonthSheet by remember { mutableStateOf(false) }

    val centeredIndex by remember(railState, minDate) {
        derivedStateOf {
            val layoutInfo = railState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) {
                TimeRailMath.dateToRailIndex(displayedDate, minDate, totalDays)
            } else {
                val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
                visibleItems.minByOrNull { item ->
                    abs((item.offset + item.size / 2) - viewportCenter)
                }?.index ?: TimeRailMath.dateToRailIndex(displayedDate, minDate, totalDays)
            }
        }
    }

    val centeredDate = remember(centeredIndex, minDate) {
        TimeRailMath.railIndexToDate(centeredIndex, minDate, totalDays)
    }

    val activeLabelDate = if (railState.isScrollInProgress) centeredDate else displayedDate

    var lastHapticTimeMs by remember { mutableLongStateOf(0L) }
    var wasUserScrolling by remember { mutableStateOf(false) }
    LaunchedEffect(centeredDate, railState.isScrollInProgress) {
        if (railState.isScrollInProgress) {
            onLiveCenteredDateChanged(centeredDate)
            if (!reducedMotion && wasUserScrolling) {
                val now = System.currentTimeMillis()
                if (now - lastHapticTimeMs >= 30L) {
                    lastHapticTimeMs = now
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }
        }
    }

    LaunchedEffect(railState) {
        snapshotFlow { railState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { scrolling ->
                if (scrolling) {
                    if (!isExternalSyncInProgress()) {
                        wasUserScrolling = true
                        onUserRailScrollStarted()
                    }
                } else {
                    onLiveCenteredDateChanged(null)
                    if (wasUserScrolling) {
                        wasUserScrolling = false
                        onCommitRailDate(centeredDate)
                    }
                }
            }
    }

    val snapFling = rememberSnapFlingBehavior(
        lazyListState = railState,
        snapPosition = SnapPosition.Center
    )
    val flingBehavior: FlingBehavior = remember(snapFling, reducedMotion) {
        object : FlingBehavior {
            override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
                val adjusted = if (reducedMotion) {
                    0f
                } else {
                    TimeRailMath.scaleAndClampFlingVelocity(initialVelocity)
                }
                return with(snapFling) { performFling(adjusted) }
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Month/year chip + floating Today pill row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .combinedClickable(
                        onClick = { showMonthSheet = true },
                        onLongClick = { showMonthSheet = true },
                        onLongClickLabel = "Open month calendar"
                    )
                    .semantics {
                        contentDescription =
                            "${activeLabelDate.format(MONTH_CHIP_FORMATTER)}. Hold or tap to open month calendar."
                    }
                    .heightIn(min = 48.dp)
                    .testTag("time_rail_month_chip")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CalendarMonth,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = activeLabelDate.format(MONTH_CHIP_FORMATTER),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            AnimatedVisibility(
                visible = TimeRailMath.shouldShowTodayPill(activeLabelDate, today),
                enter = fadeIn() + scaleIn(initialScale = 0.88f),
                exit = fadeOut() + scaleOut(targetScale = 0.88f)
            ) {
                Surface(
                    onClick = onJumpToToday,
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("time_rail_today_pill")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Today,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Today",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Centered Momentum-Scrolling Date Strip
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth()
        ) {
            val sidePadding = ((maxWidth - RAIL_DAY_WIDTH) / 2f).coerceAtLeast(0.dp)
            LazyRow(
                state = railState,
                flingBehavior = flingBehavior,
                contentPadding = PaddingValues(horizontal = sidePadding),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_time_rail")
            ) {
                items(
                    count = totalDays,
                    key = { idx -> minDate.plusDays(idx.toLong()).toEpochDay() }
                ) { idx ->
                    val date = remember(idx, minDate) { minDate.plusDays(idx.toLong()) }
                    val isCenteredOrSelected = if (railState.isScrollInProgress) {
                        date == centeredDate
                    } else {
                        date == displayedDate
                    }
                    val status = statusLookup(date)
                    TimeRailDayItem(
                        date = date,
                        today = today,
                        status = status,
                        isSelected = isCenteredOrSelected,
                        onClick = { onTapDay(date) }
                    )
                }
            }
        }
    }

    if (showMonthSheet) {
        MiniMonthCalendarSheet(
            initialMonth = YearMonth.from(activeLabelDate),
            today = today,
            minDate = minDate,
            maxDate = maxDate,
            selectedDate = activeLabelDate,
            statusLookup = statusLookup,
            onSelectDate = { picked ->
                showMonthSheet = false
                onTapDay(picked)
            },
            onDismiss = { showMonthSheet = false }
        )
    }
}

@Composable
private fun TimeRailDayItem(
    date: LocalDate,
    today: LocalDate,
    status: DayCompletionStatus,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
    val outlineColor = MaterialTheme.colorScheme.outline
    val onPrimaryColor = MaterialTheme.colorScheme.onPrimary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val freezeBlue = Color(0xFF0284C7)

    val isToday = date == today
    val isFuture = date.isAfter(today)
    val weekdayLetter = date.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault())
    val statusDescription = when (status) {
        DayCompletionStatus.FULL -> "all habits done"
        DayCompletionStatus.PARTIAL -> "partially complete"
        DayCompletionStatus.SKIPPED -> "rest day"
        DayCompletionStatus.FROZEN -> "streak frozen"
        DayCompletionStatus.FUTURE -> "upcoming day"
        DayCompletionStatus.NONE -> "no habits completed"
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(RAIL_DAY_WIDTH)
            .clip(RoundedCornerShape(16.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = 4.dp)
            .alpha(if (isFuture) 0.48f else 1f)
            .semantics(mergeDescendants = true) {
                contentDescription = buildString {
                    if (isToday) append("Today, ")
                    append(date.format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())))
                    append(", ")
                    append(statusDescription)
                    if (isSelected) append(", selected")
                }
            }
            .testTag("time_rail_day_${date.toEpochDay()}")
    ) {
        Text(
            text = weekdayLetter,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(38.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = if (isSelected) 2.5.dp.toPx() else 1.5.dp.toPx()
                val radius = size.minDimension / 2f - strokeWidth

                when (status) {
                    DayCompletionStatus.FULL -> {
                        drawCircle(color = primaryColor, radius = radius)
                    }
                    DayCompletionStatus.PARTIAL -> {
                        drawCircle(color = surfaceVariantColor, radius = radius)
                        drawArc(
                            color = primaryColor.copy(alpha = 0.78f),
                            startAngle = 0f,
                            sweepAngle = 180f,
                            useCenter = true,
                            topLeft = Offset(strokeWidth, strokeWidth),
                            size = Size(radius * 2f, radius * 2f)
                        )
                    }
                    DayCompletionStatus.SKIPPED -> {
                        drawCircle(
                            color = tertiaryColor.copy(alpha = 0.25f),
                            radius = radius
                        )
                        drawCircle(
                            color = tertiaryColor,
                            radius = radius,
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                    }
                    DayCompletionStatus.FROZEN -> {
                        drawCircle(
                            color = freezeBlue.copy(alpha = 0.2f),
                            radius = radius
                        )
                        drawCircle(
                            color = freezeBlue,
                            radius = radius,
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                    }
                    DayCompletionStatus.NONE, DayCompletionStatus.FUTURE -> {
                        drawCircle(
                            color = outlineColor.copy(alpha = 0.65f),
                            radius = radius,
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                    }
                }

                if (isSelected) {
                    drawCircle(
                        color = primaryColor,
                        radius = size.minDimension / 2f - 1.5.dp.toPx(),
                        style = Stroke(width = 2.5.dp.toPx())
                    )
                }
            }

            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Medium,
                color = if (status == DayCompletionStatus.FULL) onPrimaryColor else onSurfaceColor
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(if (isToday) primaryColor else Color.Transparent)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MiniMonthCalendarSheet(
    initialMonth: YearMonth,
    today: LocalDate,
    minDate: LocalDate,
    maxDate: LocalDate,
    selectedDate: LocalDate,
    statusLookup: (LocalDate) -> DayCompletionStatus,
    onSelectDate: (LocalDate) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var displayedMonth by remember(initialMonth) { mutableStateOf(initialMonth) }
    val cells = remember(displayedMonth) { TimeRailMath.monthCalendarCells(displayedMonth) }

    val minMonth = remember(minDate) { YearMonth.from(minDate) }
    val maxMonth = remember(maxDate) { YearMonth.from(maxDate) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(
                    onClick = { displayedMonth = displayedMonth.minusMonths(1) },
                    enabled = displayedMonth.isAfter(minMonth),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                        contentDescription = "Previous month"
                    )
                }

                Text(
                    text = displayedMonth.atDay(1).format(MONTH_CHIP_FORMATTER),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                IconButton(
                    onClick = { displayedMonth = displayedMonth.plusMonths(1) },
                    enabled = displayedMonth.isBefore(maxMonth),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = "Next month"
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

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
                            val inBounds = !date.isBefore(minDate) && !date.isAfter(maxDate)
                            val status = statusLookup(date)
                            val isSelected = date == selectedDate
                            val bg = when {
                                !inBounds -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                                status == DayCompletionStatus.FULL -> MaterialTheme.colorScheme.primary
                                status == DayCompletionStatus.PARTIAL -> MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                status == DayCompletionStatus.SKIPPED -> MaterialTheme.colorScheme.tertiaryContainer
                                status == DayCompletionStatus.FROZEN -> Color(0xFFBAE6FD)
                                date.isAfter(today) -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                else -> MaterialTheme.colorScheme.surfaceVariant
                            }
                            val fg = when {
                                status == DayCompletionStatus.FULL -> MaterialTheme.colorScheme.onPrimary
                                status == DayCompletionStatus.FROZEN -> Color(0xFF0C4A6E)
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(bg)
                                    .clickable(enabled = inBounds) { onSelectDate(date) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = date.dayOfMonth.toString(),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = if (isSelected || date == today) FontWeight.Bold else FontWeight.Normal,
                                    color = fg
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { onSelectDate(today) }) {
                    Text("Jump to Today")
                }
                TextButton(onClick = onDismiss) {
                    Text("Close")
                }
            }
        }
    }
}
