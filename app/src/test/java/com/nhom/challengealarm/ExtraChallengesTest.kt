package com.nhom.challengealarm

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtraChallengesTest {

    @Test
    fun stroopWordAndInkAlwaysDiffer() {
        val random = Random(5)
        repeat(500) {
            val item = StroopGenerator.next(mixedRules = true, random = random)
            assertNotEquals(item.word, item.ink)
        }
    }

    @Test
    fun stroopAnswerFollowsTheQuestion() {
        assertEquals(2, StroopItem(word = 0, ink = 2, askInk = true).answer)
        assertEquals(0, StroopItem(word = 0, ink = 2, askInk = false).answer)
    }

    @Test
    fun easyStroopAlwaysAsksForInk() {
        val random = Random(9)
        repeat(200) { assertTrue(StroopGenerator.next(mixedRules = false, random = random).askInk) }
    }

    @Test
    fun swipeDirectionUsesDominantAxis() {
        assertEquals(SwipeDir.RIGHT, SwipeDir.of(120f, 30f, 60f))
        assertEquals(SwipeDir.LEFT, SwipeDir.of(-120f, 30f, 60f))
        assertEquals(SwipeDir.DOWN, SwipeDir.of(20f, 150f, 60f))
        assertEquals(SwipeDir.UP, SwipeDir.of(20f, -150f, 60f))
        assertNull(SwipeDir.of(20f, 20f, 60f)) // vuốt quá ngắn
    }

    @Test
    fun redArrowExpectsOppositeSwipe() {
        assertEquals(SwipeDir.LEFT, ArrowItem(SwipeDir.RIGHT, reverse = true).expected)
        assertEquals(SwipeDir.UP, ArrowItem(SwipeDir.UP, reverse = false).expected)
        SwipeDir.values().forEach { assertEquals(it, it.opposite.opposite) }
    }

    @Test
    fun easyLevelHasNoRedArrows() {
        val random = Random(2)
        repeat(300) { assertFalse(nextArrow(ChallengeLevels.arrowReverseChance(1), random).reverse) }
    }

    @Test
    fun newChallengesGetHarder() {
        assertTrue(ChallengeLevels.stroopTarget(1) < ChallengeLevels.stroopTarget(3))
        assertTrue(ChallengeLevels.pairCount(1) < ChallengeLevels.pairCount(3))
        assertTrue(ChallengeLevels.lightLux(1) < ChallengeLevels.lightLux(3))
        assertTrue(ChallengeLevels.lightHoldSeconds(1) < ChallengeLevels.lightHoldSeconds(3))
        assertTrue(ChallengeLevels.arrowTarget(1) < ChallengeLevels.arrowTarget(3))
        assertTrue(ChallengeLevels.arrowReverseChance(1) < ChallengeLevels.arrowReverseChance(3))
    }

    @Test
    fun pairsFitInRowsOfFour() {
        for (d in 1..3) assertEquals(0, (ChallengeLevels.pairCount(d) * 2) % 4)
    }

    @Test
    fun newChallengesAreRegisteredAndClassified() {
        val types = BuiltIn.all.map { it.type }
        listOf(ChallengeType.STROOP, ChallengeType.PAIRS, ChallengeType.LIGHT, ChallengeType.ARROWS)
            .forEach { assertTrue(it in types) }
        assertFalse(ChallengeType.LIGHT.isMental) // bật đèn bắt rời giường: không có giờ nghỉ
        assertTrue(ChallengeType.STROOP.isMental)
        assertEquals(BuiltIn.all.size, BuiltIn.all.map { it.id }.toSet().size)
    }
}
