package com.example

import com.example.domain.model.FlameSkin
import com.example.domain.model.RewardType
import com.example.domain.usecase.ChestRewardGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ChestRewardTest {

    @Test
    fun `chest roll is deterministic for same date and installSeed`() {
        val date = LocalDate.of(2026, 11, 24)
        val first = ChestRewardGenerator.generateReward(date, installSeed = 42_000L, unlockedSkinIds = emptySet(), currentFreezeTokens = 0)
        val second = ChestRewardGenerator.generateReward(date, installSeed = 42_000L, unlockedSkinIds = emptySet(), currentFreezeTokens = 0)
        assertEquals(first, second)
    }

    @Test
    fun `chest distribution covers expected categories across many seeds and caps streak freezes at 3`() {
        val baseDate = LocalDate.of(2026, 1, 1)
        val counts = mutableMapOf<RewardType, Int>()
        for (i in 0 until 1000) {
            val roll = ChestRewardGenerator.generateReward(
                date = baseDate.plusDays(i.toLong()),
                installSeed = 987654L,
                unlockedSkinIds = setOf(FlameSkin.CLASSIC.id),
                currentFreezeTokens = 0
            )
            counts[roll.rewardType] = (counts[roll.rewardType] ?: 0) + 1
        }
        assertTrue((counts[RewardType.BONUS_XP] ?: 0) > 450)
        assertTrue((counts[RewardType.STREAK_FREEZE] ?: 0) > 140)
        assertTrue((counts[RewardType.RARE_QUOTE] ?: 0) > 60)
        assertTrue((counts[RewardType.FLAME_SKIN] ?: 0) > 15)

        // When freeze tokens are already at cap (3), STREAK_FREEZE rolls convert to BONUS_XP
        for (i in 0 until 200) {
            val roll = ChestRewardGenerator.generateReward(
                date = baseDate.plusDays(i.toLong()),
                installSeed = 987654L,
                unlockedSkinIds = setOf(FlameSkin.CLASSIC.id),
                currentFreezeTokens = 3
            )
            assertNotEquals(RewardType.STREAK_FREEZE, roll.rewardType)
        }
    }

    @Test
    fun `guaranteed rare or better skips common XP range`() {
        val baseDate = LocalDate.of(2026, 5, 1)
        for (i in 0 until 50) {
            val roll = ChestRewardGenerator.generateReward(
                date = baseDate.plusDays(i.toLong()),
                installSeed = 11111L,
                unlockedSkinIds = setOf(FlameSkin.CLASSIC.id),
                currentFreezeTokens = 0,
                guaranteeRareOrBetter = true
            )
            assertNotEquals(RewardType.BONUS_XP, roll.rewardType)
        }
    }
}
