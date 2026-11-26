package com.nhom.challengealarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NewChallengesTest {
    @Test
    fun difficultyMakesEveryNewChallengeHarder() {
        assertTrue(ChallengeLevels.rhythmHits(1) < ChallengeLevels.rhythmHits(3))
        assertTrue(ChallengeLevels.rhythmPeriodMs(1) > ChallengeLevels.rhythmPeriodMs(3))
        assertTrue(ChallengeLevels.rhythmZone(1) > ChallengeLevels.rhythmZone(3))
        assertTrue(ChallengeLevels.patternLength(1) < ChallengeLevels.patternLength(3))
        assertTrue(ChallengeLevels.wordCount(1) < ChallengeLevels.wordCount(3))
        assertTrue(ChallengeLevels.catchTarget(1) < ChallengeLevels.catchTarget(3))
        assertTrue(ChallengeLevels.catchVisibleMs(1) > ChallengeLevels.catchVisibleMs(3))
    }

    @Test
    fun patternNeverNeedsMoreDotsThanTheGridHas() {
        assertTrue(ChallengeLevels.patternLength(3) <= 9)
    }

    @Test
    fun outOfRangeDifficultyIsClamped() {
        assertEquals(ChallengeLevels.catchTarget(1), ChallengeLevels.catchTarget(0))
        assertEquals(ChallengeLevels.catchTarget(3), ChallengeLevels.catchTarget(9))
    }

    @Test
    fun newChallengesAreRegisteredWithUniqueNegativeIds() {
        val types = BuiltIn.all.map { it.type }
        listOf(ChallengeType.RHYTHM, ChallengeType.PATTERN, ChallengeType.WORDS, ChallengeType.CATCH)
            .forEach { assertTrue(it in types) }
        assertEquals(BuiltIn.all.size, BuiltIn.all.map { it.id }.toSet().size)
    }
}
