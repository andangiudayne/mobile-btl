package com.nhom.challengealarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChallengesTest {

    @Test
    fun builtInIdsAreUniqueAndNeverCollideWithUserChallenges() {
        val ids = BuiltIn.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        // id dương dành cho thử thách người dùng tự thêm (Room tự tăng từ 1)
        assertTrue(ids.all { it <= 0 })
    }

    @Test
    fun mathIsIdZeroAndStepsIdMatchesStepsType() {
        assertEquals(ChallengeType.MATH, BuiltIn.byId(0)?.type)
        assertEquals(ChallengeType.STEPS, BuiltIn.byId(BuiltIn.STEPS_ID)?.type)
    }

    @Test
    fun everyBuiltInTypeIsUsedExactlyOnce() {
        val types = BuiltIn.all.map { it.type }
        assertEquals(types.size, types.toSet().size)
        // QR và PHOTO là thử thách người dùng tự thêm nên không nằm trong danh sách có sẵn
        assertTrue(ChallengeType.QR !in types && ChallengeType.PHOTO !in types)
    }

    @Test
    fun harderLevelsAskForMore() {
        val functions = listOf(
            ChallengeLevels::shakeCount,
            ChallengeLevels::stepCount,
            ChallengeLevels::memoryLength,
            ChallengeLevels::orderCount,
        )
        functions.forEach { f ->
            assertTrue(f(1) < f(2) && f(2) < f(3))
        }
    }

    @Test
    fun orderGridFillsWholeRows() {
        for (difficulty in 1..3) {
            assertEquals(0, ChallengeLevels.orderCount(difficulty) % ChallengeLevels.orderColumns(difficulty))
        }
    }

    @Test
    fun outOfRangeDifficultyIsClamped() {
        assertEquals(ChallengeLevels.shakeCount(1), ChallengeLevels.shakeCount(0))
        assertEquals(ChallengeLevels.shakeCount(3), ChallengeLevels.shakeCount(9))
    }

    @Test
    fun alarmChallengeIdListParsesNegativeAndZeroIds() {
        val alarm = AlarmEntity(hour = 7, minute = 0, challengeIds = "0,-1,-4,12")
        assertEquals(listOf(0, -1, -4, 12), alarm.challengeIdList())
    }

    @Test
    fun alarmChallengeIdListIgnoresGarbage() {
        val alarm = AlarmEntity(hour = 7, minute = 0, challengeIds = "3,,x, 5")
        assertEquals(listOf(3, 5), alarm.challengeIdList())
    }
}
