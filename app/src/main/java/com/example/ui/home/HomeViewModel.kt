package com.example.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.QuotesAssetLoader
import com.example.data.prefs.UserPreferencesRepository
import com.example.data.repository.HabitRepository
import com.example.domain.model.DailyReward
import com.example.domain.model.DayCompletion
import com.example.domain.model.DayNote
import com.example.domain.model.FlameSkin
import com.example.domain.model.Habit
import com.example.domain.model.HabitLog
import com.example.domain.model.HabitType
import com.example.domain.model.Quote
import com.example.domain.model.RewardType
import com.example.domain.model.TimeOfDay
import com.example.domain.model.UnlockedBadge
import com.example.domain.usecase.BadgeDefinition
import com.example.domain.usecase.BadgeEvaluationInput
import com.example.domain.usecase.BadgeEvaluator
import com.example.domain.usecase.ChestRewardGenerator
import com.example.domain.usecase.ChestRollResult
import com.example.domain.usecase.CurrentDateProvider
import com.example.domain.usecase.HabitCalculator
import com.example.domain.usecase.XpCalculator
import com.example.domain.usecase.XpProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

data class HabitCheckItemUi(
    val habit: Habit,
    val isDone: Boolean,
    val isTiny: Boolean = false,
    val progressValue: Int,
    val isTimerRunning: Boolean,
    val isLocked: Boolean,
    val stackParentName: String? = null,
    val nextStackedHabit: Habit? = null,
    val challengeProgress: HabitCalculator.ChallengeProgress? = null
)

data class ActiveTimerKey(
    val habitId: String,
    val date: LocalDate
)

data class ChestPopupUi(
    val result: ChestRollResult,
    val rareQuote: Quote? = null
)

data class HomeUiState(
    val greeting: String = "Good morning",
    val firstName: String = "",
    val hasSeenWelcome: Boolean = true,
    val today: LocalDate = LocalDate.now(),
    val selectedDate: LocalDate = LocalDate.now(),
    val hasAnyActiveHabits: Boolean = false,
    val quote: Quote = Quote(
        text = "Habits are the compound interest of self-improvement.",
        book = "Atomic Habits",
        author = "James Clear"
    ),
    val currentStreak: Int = 0,
    val selectedCompletion: DayCompletion = DayCompletion(LocalDate.now(), 0, 0),
    val allHabits: List<Habit> = emptyList(),
    val logsByKey: Map<Pair<String, String>, HabitLog> = emptyMap(),
    val skippedDates: Set<LocalDate> = emptySet(),
    val frozenDates: Set<LocalDate> = emptySet(),
    val streakFreezeTokens: Int = 0,
    val protectableMissedDate: LocalDate? = null,
    val activeTimer: ActiveTimerKey? = null,
    val lockedFutureDate: LocalDate? = null,
    val xpProgress: XpProgress = XpCalculator.buildXpProgress(0),
    val activeFlameSkin: FlameSkin = FlameSkin.CLASSIC,
    val unlockedFlameSkinIds: Set<String> = setOf(FlameSkin.CLASSIC.id),
    val isTodayAllDone: Boolean = false,
    val todayReward: DailyReward? = null,
    val isComebackEligible: Boolean = false,
    val chestPopup: ChestPopupUi? = null,
    val badgeToast: BadgeDefinition? = null,
    val celebrationCounter: Int = 0,
    val rewardNotice: String? = null,
    val selectedDayMood: Int? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val habitRepository: HabitRepository,
    private val quotesLoader: QuotesAssetLoader,
    private val currentDateProvider: CurrentDateProvider,
    private val prefsRepository: UserPreferencesRepository
) : ViewModel() {

    companion object {
        const val MAX_HISTORY_DAYS = 3650L
        private val STREAK_MILESTONES = setOf(3, 7, 14, 30, 100, 365)
    }

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    private val _lockedFutureDate = MutableStateFlow<LocalDate?>(null)
    private val _quoteTapOffset = MutableStateFlow(0)
    private val _activeTimer = MutableStateFlow<ActiveTimerKey?>(null)
    private val _chestPopup = MutableStateFlow<ChestPopupUi?>(null)
    private val _badgeToast = MutableStateFlow<BadgeDefinition?>(null)
    private val _celebrationCounter = MutableStateFlow(0)
    private val _rewardNotice = MutableStateFlow<String?>(null)

    private val _allTodayDoneEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val allTodayDoneEvent: SharedFlow<Unit> = _allTodayDoneEvent.asSharedFlow()

    private var timerJob: Job? = null
    private var previousTodayAllDone: Boolean? = null
    private var previousStreak: Int? = null
    private var lastEvaluatedBadgeSignature: String? = null

    init {
        viewModelScope.launch {
            prefsRepository.getOrCreateInstallSeed()
        }
    }

    private val rewardUiFlow = combine(
        _chestPopup,
        _badgeToast,
        _celebrationCounter,
        _rewardNotice
    ) { chest, badge, celebration, notice ->
        RewardUiOverlay(chest, badge, celebration, notice)
    }

    private val repoDataFlow = combine(
        habitRepository.observeAllHabits(),
        habitRepository.observeAllLogs(),
        habitRepository.observeSkipDays(),
        habitRepository.observeStreakFreezes(),
        combine(
            habitRepository.observeAllDailyRewards(),
            habitRepository.observeAllUnlockedBadges(),
            habitRepository.observeAllNotes()
        ) { rewards, badges, notes -> Triple(rewards, badges, notes) }
    ) { habits, logs, skips, freezes, (rewards, badges, notes) ->
        RepoSnapshot(
            habits = habits,
            logs = logs,
            skippedDates = skips.mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }.toSet(),
            frozenDates = freezes.mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }.toSet(),
            dailyRewards = rewards,
            unlockedBadges = badges,
            notes = notes
        )
    }

    val uiState: StateFlow<HomeUiState> = combine(
        repoDataFlow,
        currentDateProvider.today,
        combine(_selectedDate, _lockedFutureDate, _quoteTapOffset) { sel, future, offset ->
            Triple(sel, future, offset)
        },
        prefsRepository.userPrefs,
        combine(_activeTimer, rewardUiFlow) { timer, rewardOverlay -> timer to rewardOverlay }
    ) { data, today, (rawSelected, futureDate, quoteOffset), prefs, (activeTimer, rewardOverlay) ->
        val selectedDate = if (rawSelected.isAfter(today)) today else rawSelected
        val displayDate = futureDate ?: selectedDate
        val logsByKey = HabitCalculator.indexLogs(data.logs)

        val selectedCompletion = HabitCalculator.dayCompletion(
            habits = data.habits,
            logsByKey = logsByKey,
            date = displayDate,
            skippedDates = data.skippedDates,
            frozenDates = data.frozenDates
        )

        val todayCompletion = HabitCalculator.dayCompletion(
            habits = data.habits,
            logsByKey = logsByKey,
            date = today,
            skippedDates = data.skippedDates,
            frozenDates = data.frozenDates
        )
        val isTodayAllDone = todayCompletion.scheduledCount > 0 &&
            todayCompletion.completedCount >= todayCompletion.scheduledCount
        val prevDone = previousTodayAllDone
        if (prevDone != null && !prevDone && isTodayAllDone) {
            _allTodayDoneEvent.tryEmit(Unit)
            _celebrationCounter.value = _celebrationCounter.value + 1
        }
        previousTodayAllDone = isTodayAllDone

        val currentStreak = HabitCalculator.calculateCurrentStreak(
            habits = data.habits,
            logs = data.logs,
            today = today,
            skippedDates = data.skippedDates,
            frozenDates = data.frozenDates
        )
        val prevStreak = previousStreak
        if (prevStreak != null && currentStreak > prevStreak && currentStreak in STREAK_MILESTONES) {
            _celebrationCounter.value = _celebrationCounter.value + 1
        }
        previousStreak = currentStreak

        val milestone = (currentStreak / 7) * 7
        if (milestone >= 7 && milestone > prefs.lastAutoShieldStreakMilestone) {
            viewModelScope.launch {
                if (prefsRepository.grantAutoStreakShieldIfEligible(currentStreak)) {
                    _rewardNotice.value = "🛡️ Auto Streak Freeze earned for hitting a ${milestone}d streak!"
                }
            }
        }

        val bestStreak = HabitCalculator.calculateBestStreak(
            habits = data.habits,
            logs = data.logs,
            today = today,
            skippedDates = data.skippedDates,
            frozenDates = data.frozenDates
        )

        val totalXp = XpCalculator.calculateTotalXp(
            habits = data.habits,
            logs = data.logs,
            dailyRewards = data.dailyRewards,
            plantBonusXpTotal = prefs.plantBonusXpTotal,
            skippedDates = data.skippedDates,
            frozenDates = data.frozenDates,
            today = today
        )
        val xpProgress = XpCalculator.buildXpProgress(totalXp)

        val unlockedSet = data.unlockedBadges.map { it.badgeId }.toSet()
        val sig = "${data.logs.count { it.done }}_${currentStreak}_${bestStreak}_${xpProgress.level}_${data.dailyRewards.size}_${data.habits.size}"
        if (sig != lastEvaluatedBadgeSignature && data.logs.any { it.done }) {
            lastEvaluatedBadgeSignature = sig
            val earnedIds = BadgeEvaluator.evaluateUnlockedBadgeIds(
                BadgeEvaluationInput(
                    habits = data.habits,
                    logs = data.logs,
                    currentStreak = currentStreak,
                    bestStreak = bestStreak,
                    level = xpProgress.level,
                    dailyRewards = data.dailyRewards,
                    skippedDates = data.skippedDates,
                    frozenDates = data.frozenDates,
                    today = today
                )
            )
            val missing = earnedIds.filter { it !in unlockedSet }
            if (missing.isNotEmpty()) {
                viewModelScope.launch {
                    val added = habitRepository.unlockBadgesIfNew(missing, today)
                    if (added.isNotEmpty()) {
                        _badgeToast.value = BadgeEvaluator.allBadges.firstOrNull { it.id == added.first() }
                    }
                }
            }
        }

        val protectable = HabitCalculator.findProtectableMissedDate(
            habits = data.habits,
            logs = data.logs,
            today = today,
            skippedDates = data.skippedDates,
            frozenDates = data.frozenDates
        )

        val comebackEligible = !isTodayAllDone &&
            XpCalculator.isComebackEligibleToday(
                habits = data.habits,
                logs = data.logs,
                today = today,
                skippedDates = data.skippedDates,
                frozenDates = data.frozenDates
            )

        val hour = LocalTime.now().hour
        val greetingBase = when {
            hour < 12 -> "Good morning"
            hour < 17 -> "Good afternoon"
            else -> "Good evening"
        }
        val greeting = if (prefs.firstName.isNotBlank()) {
            "$greetingBase, ${prefs.firstName}"
        } else {
            greetingBase
        }

        HomeUiState(
            greeting = greeting,
            firstName = prefs.firstName,
            hasSeenWelcome = prefs.hasSeenWelcome,
            today = today,
            selectedDate = selectedDate,
            hasAnyActiveHabits = data.habits.any { it.archivedAt == null },
            quote = quotesLoader.getQuoteForDate(displayDate, quoteOffset),
            currentStreak = currentStreak,
            selectedCompletion = selectedCompletion,
            allHabits = data.habits,
            logsByKey = logsByKey,
            skippedDates = data.skippedDates,
            frozenDates = data.frozenDates,
            streakFreezeTokens = prefs.streakFreezeTokens,
            protectableMissedDate = protectable,
            activeTimer = activeTimer,
            lockedFutureDate = futureDate,
            xpProgress = xpProgress,
            activeFlameSkin = prefs.activeFlameSkin,
            unlockedFlameSkinIds = prefs.unlockedFlameSkinIds,
            isTodayAllDone = isTodayAllDone,
            todayReward = data.dailyRewards.firstOrNull { it.date == today.toString() },
            isComebackEligible = comebackEligible,
            chestPopup = rewardOverlay.chestPopup,
            badgeToast = rewardOverlay.badgeToast,
            celebrationCounter = rewardOverlay.celebrationCounter,
            rewardNotice = rewardOverlay.rewardNotice,
            selectedDayMood = data.notes.firstOrNull { it.date == displayDate.toString() }?.mood
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = HomeUiState()
    )

    fun completeWelcome(name: String) {
        viewModelScope.launch {
            prefsRepository.completeWelcome(name)
        }
    }

    fun selectDate(date: LocalDate) {
        val today = currentDateProvider.today.value
        if (date.isAfter(today)) {
            _lockedFutureDate.value = date
        } else {
            _lockedFutureDate.value = null
            _selectedDate.value = date
        }
    }

    fun clearFuturePreview() {
        _lockedFutureDate.value = null
    }

    fun nextQuote() {
        _quoteTapOffset.value += 1
    }

    fun toggleHabit(habitId: String, date: LocalDate) {
        val today = currentDateProvider.today.value
        if (date.isAfter(today)) return
        if (_activeTimer.value == ActiveTimerKey(habitId, date)) {
            stopTimer()
        }
        viewModelScope.launch {
            habitRepository.toggleHabitLog(habitId, date)
        }
    }

    fun markHabitTinyDone(habitId: String, date: LocalDate) {
        val today = currentDateProvider.today.value
        if (date.isAfter(today)) return
        if (_activeTimer.value == ActiveTimerKey(habitId, date)) {
            stopTimer()
        }
        viewModelScope.launch {
            habitRepository.markHabitTinyDone(habitId, date)
        }
    }

    fun adjustCount(habitId: String, date: LocalDate, delta: Int) {
        val today = currentDateProvider.today.value
        if (date.isAfter(today)) return
        val currentProgress = uiState.value.logsByKey[habitId to date.toString()]?.progressValue ?: 0
        viewModelScope.launch {
            habitRepository.updateHabitProgress(habitId, date, (currentProgress + delta).coerceAtLeast(0))
        }
    }

    fun toggleTimer(habit: Habit, date: LocalDate) {
        val today = currentDateProvider.today.value
        if (date.isAfter(today) || habit.type != HabitType.TIMER) return
        val key = ActiveTimerKey(habit.id, date)
        if (_activeTimer.value == key) {
            stopTimer()
            return
        }
        stopTimer()
        _activeTimer.value = key
        timerJob = viewModelScope.launch {
            var currentSecs = uiState.value.logsByKey[habit.id to date.toString()]?.progressValue ?: 0
            val targetSecs = habit.effectiveTargetProgress
            while (isActive && _activeTimer.value == key) {
                delay(1000L)
                currentSecs += 1
                habitRepository.updateHabitProgress(habit.id, date, currentSecs)
                if (currentSecs >= targetSecs) {
                    _activeTimer.value = null
                    break
                }
            }
        }
    }

    fun resetTimer(habitId: String, date: LocalDate) {
        if (_activeTimer.value == ActiveTimerKey(habitId, date)) {
            stopTimer()
        }
        viewModelScope.launch {
            habitRepository.updateHabitProgress(habitId, date, 0)
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
        _activeTimer.value = null
    }

    fun toggleSkipDay(date: LocalDate) {
        viewModelScope.launch {
            habitRepository.toggleSkipDay(date)
        }
    }

    fun useFreezeOnDate(date: LocalDate) {
        viewModelScope.launch {
            if (prefsRepository.consumeStreakFreezeToken()) {
                habitRepository.applyStreakFreeze(date)
            }
        }
    }

    fun grantFreezeTokenAndProtectIfNeeded(protectDate: LocalDate?) {
        viewModelScope.launch {
            if (protectDate != null) {
                habitRepository.applyStreakFreeze(protectDate)
            } else {
                prefsRepository.addStreakFreezeToken(1)
            }
        }
    }

    fun openDailyChest() {
        viewModelScope.launch {
            val state = uiState.value
            val today = state.today
            if (!state.isTodayAllDone || state.todayReward != null) return@launch
            if (habitRepository.getDailyRewardByDate(today) != null) return@launch

            val seed = prefsRepository.getOrCreateInstallSeed()
            val guaranteeRare = BadgeEvaluator.hasFinishedAnyChallenge(
                habits = state.allHabits,
                logsByKey = state.logsByKey,
                today = today
            )
            val roll = ChestRewardGenerator.generateReward(
                date = today,
                installSeed = seed,
                unlockedSkinIds = state.unlockedFlameSkinIds,
                currentFreezeTokens = state.streakFreezeTokens,
                guaranteeRareOrBetter = guaranteeRare
            )

            habitRepository.saveDailyReward(
                date = today,
                rewardType = roll.rewardType,
                rewardValue = roll.rewardValue,
                openedAtMs = System.currentTimeMillis()
            )

            when (roll.rewardType) {
                RewardType.STREAK_FREEZE -> prefsRepository.addStreakFreezeToken(1)
                RewardType.FLAME_SKIN -> roll.unlockedSkin?.let { prefsRepository.unlockFlameSkin(it) }
                else -> Unit
            }

            val rareQuote = if (roll.rewardType == RewardType.RARE_QUOTE) {
                quotesLoader.getQuoteForDate(today, roll.rewardValue)
            } else {
                null
            }
            _chestPopup.value = ChestPopupUi(result = roll, rareQuote = rareQuote)
        }
    }

    fun dismissChestPopup() {
        _chestPopup.value = null
    }

    fun dismissBadgeToast() {
        _badgeToast.value = null
    }

    fun clearRewardNotice() {
        _rewardNotice.value = null
    }

    fun saveDayMood(date: LocalDate, mood: Int) {
        viewModelScope.launch {
            habitRepository.saveDayMood(date, mood)
        }
    }

    fun groupedHabitsForDate(state: HomeUiState, date: LocalDate): Map<TimeOfDay, List<HabitCheckItemUi>> {
        val dateStr = date.toString()
        val locked = date.isAfter(state.today)
        val scheduled = HabitCalculator.scheduledHabitsForDate(state.allHabits, date)
        val byId = scheduled.associateBy { it.id }
        val items = scheduled.map { habit ->
            val log = if (locked) null else state.logsByKey[habit.id to dateStr]
            val parentName = habit.stackAfterHabitId?.let { parentId ->
                byId[parentId]?.name ?: state.allHabits.firstOrNull { it.id == parentId }?.name
            }
            val nextHabit = scheduled.firstOrNull { child ->
                child.stackAfterHabitId == habit.id &&
                    (locked || state.logsByKey[child.id to dateStr]?.done != true)
            }
            HabitCheckItemUi(
                habit = habit,
                isDone = log?.done ?: false,
                isTiny = log?.tiny ?: false,
                progressValue = log?.progressValue ?: 0,
                isTimerRunning = state.activeTimer == ActiveTimerKey(habit.id, date),
                isLocked = locked,
                stackParentName = parentName,
                nextStackedHabit = nextHabit,
                challengeProgress = HabitCalculator.challengeProgress(habit, date)
            )
        }
        return items.groupBy { it.habit.timeOfDay }
    }

    override fun onCleared() {
        stopTimer()
        super.onCleared()
    }
}

private data class RepoSnapshot(
    val habits: List<Habit>,
    val logs: List<HabitLog>,
    val skippedDates: Set<LocalDate>,
    val frozenDates: Set<LocalDate>,
    val dailyRewards: List<DailyReward>,
    val unlockedBadges: List<UnlockedBadge>,
    val notes: List<DayNote>
)

private data class RewardUiOverlay(
    val chestPopup: ChestPopupUi?,
    val badgeToast: BadgeDefinition?,
    val celebrationCounter: Int,
    val rewardNotice: String?
)
