package com.example.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.BeachAccess
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ads.AdBannerView
import com.example.ads.AdConfig
import com.example.domain.model.DayCompletionStatus
import com.example.domain.model.HabitType
import com.example.domain.model.TimeOfDay
import com.example.domain.usecase.HabitCalculator
import com.example.ui.theme.NewsreaderFont
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

private val DATE_SUBTITLE_FORMATTER = DateTimeFormatter.ofPattern("EEE, MMM d")
private val FREEZE_DATE_FORMATTER = DateTimeFormatter.ofPattern("MMM d")

@Composable
fun HomeScreen(
    state: HomeUiState,
    canRequestAds: Boolean,
    externalJumpDate: LocalDate?,
    adFeedbackMessage: String?,
    onClearAdFeedback: () -> Unit,
    onExternalJumpConsumed: () -> Unit,
    onCompleteWelcome: (String) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
    onClearFuturePreview: () -> Unit,
    onNextQuote: () -> Unit,
    onToggleHabit: (String, LocalDate) -> Unit,
    onAdjustCount: (String, LocalDate, Int) -> Unit,
    onToggleTimer: (com.example.domain.model.Habit, LocalDate) -> Unit,
    onResetTimer: (String, LocalDate) -> Unit,
    onToggleSkipDay: (LocalDate) -> Unit,
    onUseFreeze: (LocalDate) -> Unit,
    onWatchAdForStreakFreeze: (LocalDate) -> Unit,
    onOpenProfile: () -> Unit,
    onGoToTasks: () -> Unit,
    viewModel: HomeViewModel
) {
    val maxPages = HomeViewModel.MAX_HISTORY_DAYS.toInt() + 1
    val todayIndex = HomeViewModel.MAX_HISTORY_DAYS.toInt()
    val initialPage = remember(state.selectedDate, state.today) {
        val diff = ChronoUnit.DAYS.between(state.selectedDate, state.today).toInt()
        (todayIndex - diff).coerceIn(0, todayIndex)
    }

    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { maxPages }
    )
    val coroutineScope = rememberCoroutineScope()

    val displayedDate = state.lockedFutureDate ?: state.selectedDate
    val minRailDate = remember(state.today) { TimeRailMath.minRailDate(state.today) }
    val totalRailDays = remember { TimeRailMath.totalRailDaysCount() }
    val railState = rememberLazyListState(
        initialFirstVisibleItemIndex = TimeRailMath.dateToRailIndex(displayedDate, minRailDate, totalRailDays)
    )

    var syncSource by remember { mutableStateOf(RailSyncSource.NONE) }
    var railLiveDate by remember { mutableStateOf<LocalDate?>(null) }

    val statusCache = remember { DayStatusLruCache(maxEntries = 180) }
    var dataRevision by remember { mutableIntStateOf(0) }
    LaunchedEffect(
        state.allHabits,
        state.logsByKey,
        state.skippedDates,
        state.frozenDates,
        state.today
    ) {
        dataRevision += 1
    }

    fun jumpToDate(targetDate: LocalDate, source: RailSyncSource) {
        coroutineScope.launch {
            syncSource = source
            try {
                val targetIndex = TimeRailMath.dateToRailIndex(targetDate, minRailDate, totalRailDays)
                if (targetDate.isAfter(state.today)) {
                    onSelectDate(targetDate)
                    if (source != RailSyncSource.RAIL) {
                        railState.animateScrollToItem(targetIndex)
                    }
                } else {
                    onClearFuturePreview()
                    val diff = ChronoUnit.DAYS.between(targetDate, state.today).toInt()
                    val targetPage = (todayIndex - diff).coerceIn(0, todayIndex)
                    if (source != RailSyncSource.RAIL) {
                        launch { railState.animateScrollToItem(targetIndex) }
                    }
                    pagerState.animateScrollToPage(targetPage)
                    onSelectDate(targetDate)
                }
            } finally {
                syncSource = RailSyncSource.NONE
            }
        }
    }

    // Pager -> State & Rail sync
    LaunchedEffect(pagerState, state.today) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page ->
                if (syncSource == RailSyncSource.RAIL || syncSource == RailSyncSource.TAP) {
                    return@collect
                }
                val daysAgo = (todayIndex - page).toLong().coerceAtLeast(0L)
                val date = state.today.minusDays(daysAgo)
                onSelectDate(date)
                val targetIndex = TimeRailMath.dateToRailIndex(date, minRailDate, totalRailDays)
                if (!railState.isScrollInProgress) {
                    syncSource = RailSyncSource.PAGER
                    try {
                        railState.animateScrollToItem(targetIndex)
                    } finally {
                        syncSource = RailSyncSource.NONE
                    }
                }
            }
    }

    LaunchedEffect(externalJumpDate, state.today) {
        if (externalJumpDate != null && !externalJumpDate.isAfter(state.today)) {
            jumpToDate(externalJumpDate, RailSyncSource.TAP)
            onExternalJumpConsumed()
        }
    }

    val activeHeaderDate = railLiveDate ?: displayedDate
    val isDisplayedSkipped = displayedDate in state.skippedDates
    val isDisplayedFrozen = displayedDate in state.frozenDates
    val dateLabel = when (activeHeaderDate) {
        state.today -> "Today · ${activeHeaderDate.format(DATE_SUBTITLE_FORMATTER)}"
        state.today.minusDays(1) -> "Yesterday · ${activeHeaderDate.format(DATE_SUBTITLE_FORMATTER)}"
        else -> activeHeaderDate.format(DATE_SUBTITLE_FORMATTER)
    }

    val reducedMotion = rememberReducedMotion()
    val rootHaptic = LocalHapticFeedback.current
    LaunchedEffect(state.celebrationCounter) {
        if (state.celebrationCounter > 0 && !reducedMotion) {
            rootHaptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
    ) {
        // 1. Top Bar: Greeting, Date + Rest Day toggle, Profile Avatar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.greeting,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = dateLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("home_date_subtitle")
                )
            }

            if (state.lockedFutureDate == null) {
                FilterChip(
                    selected = isDisplayedSkipped,
                    onClick = { onToggleSkipDay(displayedDate) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.BeachAccess,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = { Text(if (isDisplayedSkipped) "Skipped" else "Rest") },
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .testTag("skip_day_chip")
                )
            }

            Surface(
                onClick = onOpenProfile,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("profile_avatar_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = state.firstName.firstOrNull()?.uppercase() ?: "H",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        XpLevelStrip(
            xpProgress = state.xpProgress,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        // 2. TimeRail (Momentum-scrolling horizontal date strip + month chip + Today pill)
        TimeRailSection(
            railState = railState,
            today = state.today,
            displayedDate = displayedDate,
            statusLookup = { date ->
                statusCache.getOrCompute(dataRevision, date) { targetDate ->
                    HabitCalculator.dayCompletion(
                        habits = state.allHabits,
                        logsByKey = state.logsByKey,
                        date = targetDate,
                        skippedDates = state.skippedDates,
                        frozenDates = state.frozenDates
                    ).status(state.today)
                }
            },
            onLiveCenteredDateChanged = { liveDate ->
                railLiveDate = liveDate
            },
            onCommitRailDate = { committedDate ->
                if (syncSource == RailSyncSource.RAIL || syncSource == RailSyncSource.NONE) {
                    jumpToDate(committedDate, RailSyncSource.RAIL)
                }
            },
            onTapDay = { tappedDate ->
                jumpToDate(tappedDate, RailSyncSource.TAP)
            },
            onJumpToToday = {
                jumpToDate(state.today, RailSyncSource.TAP)
            },
            onUserRailScrollStarted = {
                if (syncSource == RailSyncSource.NONE) {
                    syncSource = RailSyncSource.RAIL
                }
            },
            isExternalSyncInProgress = {
                syncSource == RailSyncSource.PAGER || syncSource == RailSyncSource.TAP
            }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 3. Daily Quote Card
        DailyQuoteCard(
            text = state.quote.text,
            book = state.quote.book,
            author = state.quote.author,
            onClick = onNextQuote
        )

        // Optional status / ad failure notice / reward notice
        val noticeText = adFeedbackMessage ?: state.rewardNotice
        if (!noticeText.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = noticeText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = {
                            if (adFeedbackMessage != null) onClearAdFeedback() else viewModel.clearRewardNotice()
                        }
                    ) {
                        Text("OK")
                    }
                }
            }
        }

        if (state.isComebackEligible && displayedDate == state.today && state.lockedFutureDate == null) {
            Spacer(modifier = Modifier.height(8.dp))
            ComebackBanner()
        }

        if (state.isTodayAllDone && displayedDate == state.today && state.lockedFutureDate == null) {
            Spacer(modifier = Modifier.height(8.dp))
            DailyChestCard(
                alreadyOpenedReward = state.todayReward,
                onOpenClick = viewModel::openDailyChest
            )
        }

        // Skip/Freeze Status or Streak Protection Prompt
        if (isDisplayedSkipped && state.lockedFutureDate == null) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "🏖️ Vacation / rest day — your streak is safe.",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { onToggleSkipDay(displayedDate) }) {
                        Text("Undo")
                    }
                }
            }
        } else if (isDisplayedFrozen && state.lockedFutureDate == null) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "❄️ Streak Freeze active on this day — streak preserved.",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        } else if (state.protectableMissedDate != null && state.lockedFutureDate == null) {
            val missedDate = state.protectableMissedDate
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("streak_freeze_prompt")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AcUnit,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Missed ${missedDate.format(FREEZE_DATE_FORMATTER)}? Protect streak",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (state.streakFreezeTokens > 0) {
                        TextButton(onClick = { onUseFreeze(missedDate) }) {
                            Text("Freeze (${state.streakFreezeTokens})")
                        }
                    } else {
                        TextButton(
                            onClick = { onWatchAdForStreakFreeze(missedDate) },
                            modifier = Modifier.testTag("watch_ad_freeze_button")
                        ) {
                            Text("Watch Ad · Freeze")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 4. Swipeable Habits Area (grouped by Time of Day)
        Box(modifier = Modifier.weight(1f)) {
            if (state.lockedFutureDate != null) {
                val futureGrouped = viewModel.groupedHabitsForDate(state, state.lockedFutureDate)
                FutureDayLockedContent(
                    date = state.lockedFutureDate,
                    grouped = futureGrouped,
                    onBackToToday = {
                        jumpToDate(state.today, RailSyncSource.TAP)
                    }
                )
            } else {
                HorizontalPager(
                    state = pagerState,
                    flingBehavior = PagerDefaults.flingBehavior(
                        state = pagerState,
                        pagerSnapDistance = PagerSnapDistance.atMost(1)
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("home_day_pager")
                ) { page ->
                    val daysAgo = (todayIndex - page).toLong().coerceAtLeast(0L)
                    val pageDate = state.today.minusDays(daysAgo)
                    val grouped = remember(
                        state.allHabits,
                        state.logsByKey,
                        state.activeTimer,
                        pageDate,
                        state.today
                    ) {
                        viewModel.groupedHabitsForDate(state, pageDate)
                    }
                    DayHabitsPage(
                        date = pageDate,
                        hasAnyActiveHabits = state.hasAnyActiveHabits,
                        grouped = grouped,
                        onToggleHabit = { habitId -> onToggleHabit(habitId, pageDate) },
                        onMarkTinyDone = { habitId -> viewModel.markHabitTinyDone(habitId, pageDate) },
                        onAdjustCount = { habitId, delta -> onAdjustCount(habitId, pageDate, delta) },
                        onToggleTimer = { habit -> onToggleTimer(habit, pageDate) },
                        onResetTimer = { habitId -> onResetTimer(habitId, pageDate) },
                        onGoToTasks = onGoToTasks
                    )
                }
            }
        }

        if (displayedDate == state.today &&
            state.lockedFutureDate == null &&
            state.selectedCompletion.completedCount >= 1
        ) {
            Spacer(modifier = Modifier.height(6.dp))
            MoodEnergySelectorRow(
                selectedMood = state.selectedDayMood,
                onSelectMood = { mood -> viewModel.saveDayMood(state.today, mood) }
            )
        }

        // 5. Progress Summary + Celebration
        if (state.hasAnyActiveHabits && state.lockedFutureDate == null) {
            Spacer(modifier = Modifier.height(6.dp))
            HomeProgressSummary(
                completed = state.selectedCompletion.completedCount,
                total = state.selectedCompletion.scheduledCount,
                percent = state.selectedCompletion.percent,
                currentStreak = state.currentStreak,
                flameSkin = state.activeFlameSkin,
                isSkipped = isDisplayedSkipped
            )
        }

        // 6. Adaptive Banner Ad (>=16dp below summary, never near toggles or add button)
        AdBannerView(
            adUnitId = AdConfig.BANNER_HOME_AD_UNIT_ID,
            canRequestAds = canRequestAds,
            modifier = Modifier.padding(bottom = 8.dp)
        )
    }

    FullScreenConfettiOverlay(triggerId = state.celebrationCounter)

    BadgeUnlockToast(
        badge = state.badgeToast,
        onDismiss = viewModel::dismissBadgeToast,
        modifier = Modifier.align(Alignment.TopCenter)
    )
    }

    val chestPopup = state.chestPopup
    if (chestPopup != null) {
        ChestOpenDialog(
            result = chestPopup.result,
            rareQuote = chestPopup.rareQuote,
            onDismiss = viewModel::dismissChestPopup
        )
    }

    if (!state.hasSeenWelcome) {
        WelcomeNameDialog(
            onSave = onCompleteWelcome,
            onSkip = { onCompleteWelcome("") }
        )
    }
}

@Composable
private fun WelcomeNameDialog(
    onSave: (String) -> Unit,
    onSkip: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onSkip,
        title = { Text("Welcome to HabitWorks") },
        text = {
            Column {
                Text(
                    text = "Everything stays privately on your device — no sign-in required. What should we call you?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 40) name = it },
                    label = { Text("First name (optional)") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("welcome_name_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(name) },
                modifier = Modifier.testTag("welcome_continue_button")
            ) {
                Text("Get Started")
            }
        },
        dismissButton = {
            TextButton(onClick = onSkip) {
                Text("Skip")
            }
        }
    )
}

@Composable
private fun DailyQuoteCard(
    text: String,
    book: String,
    author: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        ),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("daily_quote_card")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Rounded.FormatQuote,
                contentDescription = "Daily quote",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = text,
                    fontFamily = NewsreaderFont,
                    fontStyle = FontStyle.Italic,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "$book · $author",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DayHabitsPage(
    date: LocalDate,
    hasAnyActiveHabits: Boolean,
    grouped: Map<TimeOfDay, List<HabitCheckItemUi>>,
    onToggleHabit: (String) -> Unit,
    onMarkTinyDone: (String) -> Unit,
    onAdjustCount: (String, Int) -> Unit,
    onToggleTimer: (com.example.domain.model.Habit) -> Unit,
    onResetTimer: (String) -> Unit,
    onGoToTasks: () -> Unit
) {
    if (!hasAnyActiveHabits) {
        EmptyHabitsState(onGoToTasks = onGoToTasks)
        return
    }

    if (grouped.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "No habits scheduled for this day",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onGoToTasks) {
                    Text("Manage habit schedule")
                }
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        TimeOfDay.entries.forEach { slot ->
            val slotItems = grouped[slot].orEmpty()
            if (slotItems.isNotEmpty()) {
                item(key = "header_${date}_${slot.name}") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 2.dp, start = 4.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${slot.icon} ${slot.label}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val doneInGroup = slotItems.count { it.isDone }
                        Text(
                            text = "$doneInGroup/${slotItems.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                items(slotItems, key = { "${date}_${it.habit.id}" }) { item ->
                    HabitToggleRow(
                        item = item,
                        onToggle = { onToggleHabit(item.habit.id) },
                        onMarkTinyDone = { onMarkTinyDone(item.habit.id) },
                        onNextStackedComplete = { nextId -> onToggleHabit(nextId) },
                        onAdjustCount = { delta -> onAdjustCount(item.habit.id, delta) },
                        onToggleTimer = { onToggleTimer(item.habit) },
                        onResetTimer = { onResetTimer(item.habit.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FutureDayLockedContent(
    date: LocalDate,
    grouped: Map<TimeOfDay, List<HabitCheckItemUi>>,
    onBackToToday: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = "Future day locked",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Future habits unlock on ${date.format(DATE_SUBTITLE_FORMATTER)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onBackToToday) {
                    Text("Today")
                }
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        val flat = TimeOfDay.entries.flatMap { grouped[it].orEmpty() }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.55f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(flat, key = { "future_${date}_${it.habit.id}" }) { item ->
                HabitToggleRow(
                    item = item.copy(isLocked = true),
                    onToggle = {},
                    onMarkTinyDone = {},
                    onNextStackedComplete = {},
                    onAdjustCount = {},
                    onToggleTimer = {},
                    onResetTimer = {}
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HabitToggleRow(
    item: HabitCheckItemUi,
    onToggle: () -> Unit,
    onMarkTinyDone: () -> Unit,
    onNextStackedComplete: (String) -> Unit,
    onAdjustCount: (Int) -> Unit,
    onToggleTimer: () -> Unit,
    onResetTimer: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    var showTinyDialog by remember { mutableStateOf(false) }
    val checkScale by animateFloatAsState(
        targetValue = if (item.isDone) 1f else 0.92f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "habit_check_scale"
    )
    val accent = Color(item.habit.colorHex)

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isDone) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.38f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (item.isDone) 0.dp else 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .combinedClickable(
                enabled = !item.isLocked,
                onClick = {
                    if (item.habit.type == HabitType.YES_NO) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onToggle()
                    }
                },
                onLongClick = {
                    if (!item.isDone) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        showTinyDialog = true
                    }
                }
            )
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            RowSparkleEffect(
                isDone = item.isDone && !item.isLocked,
                accent = accent,
                modifier = Modifier.matchParentSize()
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = item.habit.icon,
                        style = MaterialTheme.typography.titleLarge
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.habit.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = if (item.isDone) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        textDecoration = if (item.isDone) TextDecoration.LineThrough else TextDecoration.None,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.habit.cueText.isNotBlank()) {
                        Text(
                            text = "Cue: ${item.habit.cueText}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    val subtitle = buildString {
                        append(item.habit.category.label)
                        if (item.habit.type != HabitType.YES_NO) {
                            append(" · ")
                            append(item.habit.formatTargetLabel())
                        }
                        if (item.isTiny) {
                            append(" · Tiny win")
                        }
                        if (!item.habit.reminderTime.isNullOrBlank()) {
                            append(" · ")
                            append(item.habit.reminderTime)
                        }
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val challenge = item.challengeProgress
                    if (challenge != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 3.dp)
                        ) {
                            val trackColor = MaterialTheme.colorScheme.surfaceVariant
                            Canvas(modifier = Modifier.size(14.dp)) {
                                val stroke = 2.5.dp.toPx()
                                drawCircle(
                                    color = trackColor,
                                    style = Stroke(width = stroke)
                                )
                                drawArc(
                                    color = accent,
                                    startAngle = -90f,
                                    sweepAngle = 360f * (challenge.dayNumber.toFloat() / challenge.totalDays.toFloat()),
                                    useCenter = false,
                                    style = Stroke(width = stroke, cap = StrokeCap.Round)
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Day ${challenge.dayNumber} of ${challenge.totalDays}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (!item.stackParentName.isNullOrBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Link,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "After ${item.stackParentName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .scale(checkScale)
                        .clip(CircleShape)
                        .clickable(enabled = !item.isLocked) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onToggle()
                        }
                        .testTag("habit_toggle_${item.habit.id}")
                ) {
                    if (item.isLocked) {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = "Locked",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(22.dp)
                        )
                    } else {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(
                                    if (item.isDone) MaterialTheme.colorScheme.primary else Color.Transparent
                                )
                                .border(
                                    width = 2.dp,
                                    color = if (item.isDone) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outline
                                    },
                                    shape = CircleShape
                                )
                        ) {
                            if (item.isDone) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = "Completed",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (!item.isLocked && item.habit.type == HabitType.COUNT) {
                val target = item.habit.targetValue.coerceAtLeast(1)
                val progress = item.progressValue.coerceAtLeast(0)
                val unit = item.habit.unitLabel.ifBlank { "times" }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LinearProgressIndicator(
                        progress = { (progress.toFloat() / target.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = accent,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeCap = StrokeCap.Round
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "$progress / $target $unit",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(
                        onClick = { onAdjustCount(-1) },
                        enabled = progress > 0,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("count_minus_${item.habit.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Remove,
                            contentDescription = "Decrease ${item.habit.name}"
                        )
                    }
                    IconButton(
                        onClick = { onAdjustCount(1) },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("count_plus_${item.habit.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = "Increase ${item.habit.name}",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            } else if (!item.isLocked && item.habit.type == HabitType.TIMER) {
                val targetSecs = item.habit.effectiveTargetProgress.coerceAtLeast(60)
                val elapsedSecs = item.progressValue.coerceAtLeast(0)
                val elapsedFormatted = String.format(Locale.US, "%02d:%02d", elapsedSecs / 60, elapsedSecs % 60)
                val targetFormatted = String.format(Locale.US, "%02d:00", item.habit.targetValue.coerceAtLeast(1))
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LinearProgressIndicator(
                        progress = { (elapsedSecs.toFloat() / targetSecs.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = accent,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeCap = StrokeCap.Round
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "$elapsedFormatted / $targetFormatted",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (item.isTimerRunning) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(
                        onClick = onToggleTimer,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("timer_toggle_${item.habit.id}")
                    ) {
                        Icon(
                            imageVector = if (item.isTimerRunning) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (item.isTimerRunning) "Pause timer" else "Start timer",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (elapsedSecs > 0) {
                        IconButton(
                            onClick = onResetTimer,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Reset timer"
                            )
                        }
                    }
                }
            }
            val nextHabit = item.nextStackedHabit
            if (!item.isLocked && item.isDone && nextHabit != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onNextStackedComplete(nextHabit.id)
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.testTag("next_stacked_chip_${nextHabit.id}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Link,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Next up: ${nextHabit.icon} ${nextHabit.name} · Tap to complete",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
        }
    }

    if (showTinyDialog) {
        val tinyCopy = item.habit.tinyVersion.ifBlank {
            "Do a tiny 2-minute version to keep your streak alive."
        }
        AlertDialog(
            onDismissRequest = { showTinyDialog = false },
            title = { Text("Just do the tiny version?") },
            text = {
                Text(
                    text = "$tinyCopy\n\nCounts for today's streak and awards +5 XP.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showTinyDialog = false
                        onMarkTinyDone()
                    },
                    modifier = Modifier.testTag("confirm_tiny_win_button")
                ) {
                    Text("Mark tiny win")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTinyDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun EmptyHabitsState(onGoToTasks: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_habit_empty_1795097146263),
            contentDescription = stringResource(R.string.empty_habits_title),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(156.dp)
                .clip(RoundedCornerShape(28.dp))
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = stringResource(R.string.empty_habits_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.empty_habits_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onGoToTasks,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .heightIn(min = 48.dp)
                .testTag("empty_go_to_tasks_button")
        ) {
            Text(stringResource(R.string.go_to_tasks))
        }
    }
}

@Composable
private fun HomeProgressSummary(
    completed: Int,
    total: Int,
    percent: Int,
    currentStreak: Int,
    flameSkin: com.example.domain.model.FlameSkin,
    isSkipped: Boolean
) {
    val allDone = total > 0 && completed >= total
    val animatedProgress by animateFloatAsState(
        targetValue = if (total == 0) 0f else completed.toFloat() / total.toFloat(),
        animationSpec = tween(durationMillis = 350),
        label = "day_progress_bar"
    )

    val burstProgress = remember { Animatable(0f) }
    LaunchedEffect(allDone) {
        if (allDone) {
            burstProgress.snapTo(0f)
            burstProgress.animateTo(1f, animationSpec = tween(durationMillis = 700))
        }
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (allDone) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_progress_summary")
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            if (allDone && burstProgress.value < 1f) {
                val p = burstProgress.value
                val primary = MaterialTheme.colorScheme.primary
                val tertiary = MaterialTheme.colorScheme.tertiary
                Canvas(modifier = Modifier.matchParentSize()) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    for (i in 0 until 12) {
                        val angle = (i * 30.0) * Math.PI / 180.0
                        val dist = p * size.width * 0.42f
                        val x = cx + (cos(angle) * dist).toFloat()
                        val y = cy + (sin(angle) * dist * 0.45f).toFloat()
                        drawCircle(
                            color = if (i % 2 == 0) primary.copy(alpha = 1f - p) else tertiary.copy(alpha = 1f - p),
                            radius = 4.dp.toPx(),
                            center = Offset(x, y)
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (isSkipped) "Rest day ($completed/$total)" else "$completed of $total done",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "$percent%",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    EvolvingStreakFlame(
                        streak = currentStreak,
                        skin = flameSkin
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = StrokeCap.Round
                )

                AnimatedVisibility(
                    visible = allDone,
                    enter = fadeIn() + scaleIn(initialScale = 0.9f),
                    exit = fadeOut()
                ) {
                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = "All habits completed",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "All habits complete for today! Great momentum.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }
    }
}
