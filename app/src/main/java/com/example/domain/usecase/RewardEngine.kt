package com.example.domain.usecase

import com.example.data.prefs.UserPreferencesRepository
import com.example.domain.model.DailyReward
import com.example.domain.model.FlameSkin
import com.example.domain.model.Habit
import com.example.domain.model.HabitLog
import com.example.domain.model.RewardType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

data class XpProgress(
    val totalXp: Int,
    val level: Int,
    val currentLevelStartXp: Int,
    val nextLevelXp: Int
) {
    val xpIntoLevel: Int
        get() = (totalXp - currentLevelStartXp).coerceAtLeast(0)

    val xpNeededForNextLevel: Int
        get() = (nextLevelXp - currentLevelStartXp).coerceAtLeast(1)

    val progressFraction: Float
        get() = (xpIntoLevel.toFloat() / xpNeededForNextLevel.toFloat()).coerceIn(0f, 1f)
}

object XpCalculator {
    const val XP_PER_HABIT = 10
    const val XP_PER_TINY_WIN = 5
    const val XP_PERFECT_DAY_BASE = 20
    const val XP_COMEBACK_BONUS = 25

    fun xpForLevel(level: Int): Int {
        if (level <= 1) return 0
        return 50 * level * (level - 1)
    }

    fun levelFromXp(totalXp: Int): Int {
        val safeXp = totalXp.coerceAtLeast(0)
        return floor((1.0 + sqrt(1.0 + safeXp / 12.5)) / 2.0).toInt().coerceAtLeast(1)
    }

    fun buildXpProgress(totalXp: Int): XpProgress {
        val safeXp = totalXp.coerceAtLeast(0)
        val level = levelFromXp(safeXp)
        return XpProgress(
            totalXp = safeXp,
            level = level,
            currentLevelStartXp = xpForLevel(level),
            nextLevelXp = xpForLevel(level + 1)
        )
    }

    fun calculateTotalXp(
        habits: List<Habit>,
        logs: List<HabitLog>,
        dailyRewards: List<DailyReward>,
        plantBonusXpTotal: Int,
        skippedDates: Set<LocalDate>,
        frozenDates: Set<LocalDate>,
        today: LocalDate
    ): Int {
        val checkInXp = logs.sumOf { log ->
            when {
                !log.done -> 0
                log.tiny -> XP_PER_TINY_WIN
                else -> XP_PER_HABIT
            }
        }

        val chestXp = dailyRewards.sumOf { reward ->
            if (reward.rewardType == RewardType.BONUS_XP || reward.rewardType == RewardType.LEGENDARY_XP) {
                reward.rewardValue.coerceAtLeast(0)
            } else {
                0
            }
        }

        val perfectDayXp = calculatePerfectDayBonusXp(
            habits = habits,
            logs = logs,
            skippedDates = skippedDates,
            frozenDates = frozenDates,
            today = today
        )

        return (checkInXp + perfectDayXp + chestXp + plantBonusXpTotal.coerceAtLeast(0)).coerceAtLeast(0)
    }

    fun calculatePerfectDayBonusXp(
        habits: List<Habit>,
        logs: List<HabitLog>,
        skippedDates: Set<LocalDate>,
        frozenDates: Set<LocalDate>,
        today: LocalDate
    ): Int {
        if (habits.isEmpty()) return 0
        val earliest = habits.minOf { it.createdDate }
        if (today.isBefore(earliest)) return 0

        val logsByKey = HabitCalculator.indexLogs(logs)
        var totalBonus = 0
        var runningStreak = 0
        var prevHadMissedScheduled = false

        var cursor = earliest
        while (!cursor.isAfter(today)) {
            val comp = HabitCalculator.dayCompletion(habits, logsByKey, cursor, skippedDates, frozenDates)
            if (comp.scheduledCount > 0) {
                if (comp.completedCount >= comp.scheduledCount) {
                    runningStreak++
                    val multiplier = min(1.0 + runningStreak / 30.0, 2.0)
                    totalBonus += (XP_PERFECT_DAY_BASE * multiplier).roundToInt()
                    if (prevHadMissedScheduled) {
                        totalBonus += XP_COMEBACK_BONUS
                    }
                    prevHadMissedScheduled = false
                } else if (!comp.isSkipped && !comp.isFrozen) {
                    runningStreak = 0
                    prevHadMissedScheduled = cursor.isBefore(today)
                }
            }
            cursor = cursor.plusDays(1)
        }
        return totalBonus
    }

    fun isComebackEligibleToday(
        habits: List<Habit>,
        logs: List<HabitLog>,
        today: LocalDate,
        skippedDates: Set<LocalDate>,
        frozenDates: Set<LocalDate>
    ): Boolean {
        if (habits.isEmpty()) return false
        val yesterday = today.minusDays(1)
        val logsByKey = HabitCalculator.indexLogs(logs)
        val yComp = HabitCalculator.dayCompletion(habits, logsByKey, yesterday, skippedDates, frozenDates)
        return yComp.scheduledCount > 0 &&
            yComp.completedCount < yComp.scheduledCount &&
            !yComp.isSkipped &&
            !yComp.isFrozen
    }
}

data class ChestRollResult(
    val rewardType: RewardType,
    val rewardValue: Int,
    val title: String,
    val subtitle: String,
    val unlockedSkin: FlameSkin? = null
)

object ChestRewardGenerator {

    fun generateReward(
        date: LocalDate,
        installSeed: Long,
        unlockedSkinIds: Set<String>,
        currentFreezeTokens: Int,
        guaranteeRareOrBetter: Boolean = false
    ): ChestRollResult {
        val seed = date.toEpochDay() * 1_000_003L xor (installSeed * 31_337L)
        val rng = Random(seed)
        val roll = if (guaranteeRareOrBetter) {
            60 + rng.nextInt(40)
        } else {
            rng.nextInt(100)
        }

        return when {
            roll < 60 -> {
                val xp = 15 + rng.nextInt(26) // 15..40
                ChestRollResult(
                    rewardType = RewardType.BONUS_XP,
                    rewardValue = xp,
                    title = "+$xp Bonus XP",
                    subtitle = "Steady daily reps add up fast."
                )
            }
            roll < 82 -> {
                if (currentFreezeTokens < UserPreferencesRepository.MAX_STREAK_FREEZES) {
                    ChestRollResult(
                        rewardType = RewardType.STREAK_FREEZE,
                        rewardValue = 1,
                        title = "+1 Streak Freeze",
                        subtitle = "Saved to your shield vault for a rainy day."
                    )
                } else {
                    val xp = 35
                    ChestRollResult(
                        rewardType = RewardType.BONUS_XP,
                        rewardValue = xp,
                        title = "+$xp Bonus XP",
                        subtitle = "Freeze vault is full (max 3) — converted to XP!"
                    )
                }
            }
            roll < 94 -> {
                val quoteSeed = rng.nextInt(10_000)
                ChestRollResult(
                    rewardType = RewardType.RARE_QUOTE,
                    rewardValue = quoteSeed,
                    title = "Rare Wisdom Card",
                    subtitle = "A timeless habit insight unlocked for today."
                )
            }
            roll < 99 -> {
                val availableSkins = FlameSkin.entries.filter { it.id !in unlockedSkinIds }
                if (availableSkins.isNotEmpty()) {
                    val skin = availableSkins[rng.nextInt(availableSkins.size)]
                    ChestRollResult(
                        rewardType = RewardType.FLAME_SKIN,
                        rewardValue = skin.ordinal,
                        title = "${skin.title} Unlocked!",
                        subtitle = "Your streak flame now glows with a new skin.",
                        unlockedSkin = skin
                    )
                } else {
                    ChestRollResult(
                        rewardType = RewardType.BONUS_XP,
                        rewardValue = 60,
                        title = "+60 Bonus XP",
                        subtitle = "All flame skins collected — bonus XP granted!"
                    )
                }
            }
            else -> {
                ChestRollResult(
                    rewardType = RewardType.LEGENDARY_XP,
                    rewardValue = 150,
                    title = "Legendary +150 XP!",
                    subtitle = "1% rare drop — incredible consistency!"
                )
            }
        }
    }
}

data class HabitStrength(
    val habit: Habit,
    val scorePercent: Int,
    val label: String
)

object HabitStrengthCalculator {

    fun strengthLabel(percent: Int): String = when {
        percent >= 80 -> "Rock solid"
        percent >= 55 -> "Growing"
        percent >= 30 -> "Building"
        else -> "Fragile"
    }

    fun calculateStrength(
        habit: Habit,
        logsByKey: Map<Pair<String, String>, HabitLog>,
        today: LocalDate,
        skippedDates: Set<LocalDate> = emptySet(),
        frozenDates: Set<LocalDate> = emptySet()
    ): HabitStrength {
        val windowStart = maxOf(habit.createdDate, today.minusDays(89))
        val maxActive = habit.archivedDate?.minusDays(1) ?: today
        val endDate = minOf(today, maxActive)

        var strength = 0.0
        if (!endDate.isBefore(windowStart)) {
            var cursor = windowStart
            while (!cursor.isAfter(endDate)) {
                if (cursor !in skippedDates &&
                    cursor !in frozenDates &&
                    HabitCalculator.isHabitActiveOn(habit, cursor)
                ) {
                    val done = if (logsByKey[habit.id to cursor.toString()]?.done == true) 1.0 else 0.0
                    strength = strength * 0.95 + done * 0.05
                }
                cursor = cursor.plusDays(1)
            }
        }

        val percent = (strength * 100.0).roundToInt().coerceIn(0, 100)
        return HabitStrength(
            habit = habit,
            scorePercent = percent,
            label = strengthLabel(percent)
        )
    }
}

data class BadgeDefinition(
    val id: String,
    val title: String,
    val description: String,
    val emoji: String
)

data class BadgeEvaluationInput(
    val habits: List<Habit>,
    val logs: List<HabitLog>,
    val currentStreak: Int,
    val bestStreak: Int,
    val level: Int,
    val dailyRewards: List<DailyReward>,
    val skippedDates: Set<LocalDate>,
    val frozenDates: Set<LocalDate>,
    val today: LocalDate
)

object BadgeEvaluator {
    val allBadges: List<BadgeDefinition> = listOf(
        BadgeDefinition("first_checkin", "First Step", "Complete your first habit check-in", "🌱"),
        BadgeDefinition("streak_3", "Spark", "Reach a 3-day streak", "✨"),
        BadgeDefinition("streak_7", "Week Warrior", "Reach a 7-day streak", "🔥"),
        BadgeDefinition("streak_14", "Fortnight", "Reach a 14-day streak", "⚡"),
        BadgeDefinition("streak_30", "Monthly Master", "Reach a 30-day streak", "🏅"),
        BadgeDefinition("streak_100", "Centurion", "Reach a 100-day streak", "👑"),
        BadgeDefinition("streak_365", "Year Legend", "Reach a 365-day streak", "🌟"),
        BadgeDefinition("level_5", "Rising Star", "Reach Level 5", "⭐"),
        BadgeDefinition("level_10", "Momentum Pro", "Reach Level 10", "🚀"),
        BadgeDefinition("level_25", "Habit Sage", "Reach Level 25", "💎"),
        BadgeDefinition("completions_50", "Half Century", "Log 50 habit completions", "🎯"),
        BadgeDefinition("completions_200", "Double Ton", "Log 200 habit completions", "💪"),
        BadgeDefinition("completions_500", "Five Hundred", "Log 500 habit completions", "🏔️"),
        BadgeDefinition("completions_1000", "Millennium", "Log 1,000 habit completions", "🏆"),
        BadgeDefinition("early_bird", "Early Bird", "10+ completions before 8:00 AM", "🌅"),
        BadgeDefinition("night_owl", "Night Owl", "10+ completions at 9:00 PM or later", "🦉"),
        BadgeDefinition("comeback", "Never Miss Twice", "Bounce back after a missed day", "🪃"),
        BadgeDefinition("challenge_finisher", "Challenge Finisher", "Finish a timed habit challenge", "🏁"),
        BadgeDefinition("perfect_week", "Perfect Week", "7 consecutive 100% days", "📅"),
        BadgeDefinition("perfect_month", "Perfect Month", "30 consecutive 100% days", "🗓️"),
        BadgeDefinition("habit_stacker", "Habit Stacker", "Chain two habits together", "🔗"),
        BadgeDefinition("tiny_starter", "Tiny Starter", "Keep momentum with a tiny win", "🪴"),
        BadgeDefinition("daily_collector", "Chest Hunter", "Open 7 daily reward chests", "🎁"),
        BadgeDefinition("well_rounded", "Well Rounded", "Active habits in 3+ categories", "🧭")
    )

    fun evaluateUnlockedBadgeIds(
        input: BadgeEvaluationInput,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<String> {
        val unlocked = mutableListOf<String>()
        val doneLogs = input.logs.filter { it.done }
        val totalDone = doneLogs.size
        val topStreak = maxOf(input.currentStreak, input.bestStreak)

        if (totalDone >= 1) unlocked.add("first_checkin")
        if (topStreak >= 3) unlocked.add("streak_3")
        if (topStreak >= 7) unlocked.add("streak_7")
        if (topStreak >= 14) unlocked.add("streak_14")
        if (topStreak >= 30) unlocked.add("streak_30")
        if (topStreak >= 100) unlocked.add("streak_100")
        if (topStreak >= 365) unlocked.add("streak_365")

        if (input.level >= 5) unlocked.add("level_5")
        if (input.level >= 10) unlocked.add("level_10")
        if (input.level >= 25) unlocked.add("level_25")

        if (totalDone >= 50) unlocked.add("completions_50")
        if (totalDone >= 200) unlocked.add("completions_200")
        if (totalDone >= 500) unlocked.add("completions_500")
        if (totalDone >= 1000) unlocked.add("completions_1000")

        val earlyBirdCount = doneLogs.count { log ->
            val ms = log.completedAtMs ?: return@count false
            Instant.ofEpochMilli(ms).atZone(zoneId).hour < 8
        }
        if (earlyBirdCount >= 10) unlocked.add("early_bird")

        val nightOwlCount = doneLogs.count { log ->
            val ms = log.completedAtMs ?: return@count false
            Instant.ofEpochMilli(ms).atZone(zoneId).hour >= 21
        }
        if (nightOwlCount >= 10) unlocked.add("night_owl")

        if (hasComebackHistory(input.habits, input.logs, input.skippedDates, input.frozenDates, input.today)) {
            unlocked.add("comeback")
        }

        val logsByKey = HabitCalculator.indexLogs(input.logs)
        if (hasFinishedAnyChallenge(input.habits, logsByKey, input.today)) {
            unlocked.add("challenge_finisher")
        }

        if (topStreak >= 7) unlocked.add("perfect_week")
        if (topStreak >= 30) unlocked.add("perfect_month")

        if (input.habits.any { !it.stackAfterHabitId.isNullOrBlank() && it.archivedAt == null }) {
            unlocked.add("habit_stacker")
        }

        if (doneLogs.any { it.tiny }) {
            unlocked.add("tiny_starter")
        }

        if (input.dailyRewards.count { it.openedAtMs != null } >= 7) {
            unlocked.add("daily_collector")
        }

        val activeCategories = input.habits.filter { it.archivedAt == null }.map { it.category }.distinct()
        if (activeCategories.size >= 3) {
            unlocked.add("well_rounded")
        }

        return unlocked
    }

    fun hasFinishedAnyChallenge(
        habits: List<Habit>,
        logsByKey: Map<Pair<String, String>, HabitLog>,
        today: LocalDate
    ): Boolean {
        return habits.any { habit ->
            val start = habit.challengeStartDate ?: return@any false
            if (habit.challengeDays <= 0) return@any false
            val end = start.plusDays((habit.challengeDays - 1).toLong())
            if (today.isBefore(end)) return@any false
            var scheduled = 0
            var completed = 0
            var cursor = start
            while (!cursor.isAfter(end)) {
                if (HabitCalculator.isHabitActiveOn(habit, cursor)) {
                    scheduled++
                    if (logsByKey[habit.id to cursor.toString()]?.done == true) {
                        completed++
                    }
                }
                cursor = cursor.plusDays(1)
            }
            scheduled > 0 && completed >= scheduled
        }
    }

    private fun hasComebackHistory(
        habits: List<Habit>,
        logs: List<HabitLog>,
        skippedDates: Set<LocalDate>,
        frozenDates: Set<LocalDate>,
        today: LocalDate
    ): Boolean {
        if (habits.isEmpty()) return false
        val earliest = habits.minOf { it.createdDate }
        if (today.isBefore(earliest)) return false
        val logsByKey = HabitCalculator.indexLogs(logs)
        var sawMissedDay = false
        var cursor = earliest
        while (!cursor.isAfter(today)) {
            val comp = HabitCalculator.dayCompletion(habits, logsByKey, cursor, skippedDates, frozenDates)
            if (comp.scheduledCount > 0) {
                if (comp.completedCount >= comp.scheduledCount) {
                    if (sawMissedDay) return true
                } else if (!comp.isSkipped && !comp.isFrozen && cursor.isBefore(today)) {
                    sawMissedDay = true
                }
            }
            cursor = cursor.plusDays(1)
        }
        return false
    }
}
