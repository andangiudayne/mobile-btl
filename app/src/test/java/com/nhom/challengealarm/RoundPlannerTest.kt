package com.nhom.challengealarm

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RoundPlannerTest {

    @Test
    fun nextNeverRepeatsWhenThereIsAChoice() {
        val pool = listOf("toán", "gõ chữ", "nhớ màu")
        val random = Random(42)
        var prev: String? = null
        repeat(200) {
            val next = RoundPlanner.next(pool, prev, random)
            assertNotEquals(prev, next)
            prev = next
        }
    }

    @Test
    fun singleChallengeRepeats() {
        assertEquals("toán", RoundPlanner.next(listOf("toán"), "toán"))
    }

    @Test
    fun breakOnlyAfterMentalRoundsAndNotAfterLast() {
        assertEquals(60_000L, RoundPlanner.breakMillis(mental = true, breakMinutes = 1, roundsDone = 1, totalRounds = 3))
        assertEquals(120_000L, RoundPlanner.breakMillis(true, 2, 2, 3))
        assertEquals(0L, RoundPlanner.breakMillis(false, 2, 1, 3)) // lắc, đi bộ: không nghỉ
        assertEquals(0L, RoundPlanner.breakMillis(true, 2, 3, 3)) // màn cuối: tắt luôn
        assertEquals(0L, RoundPlanner.breakMillis(true, 0, 1, 3))
        assertEquals(120_000L, RoundPlanner.breakMillis(true, 9, 1, 3)) // tối đa 2 phút
    }

    @Test
    fun awakeButtonFromRoundTwo() {
        assertFalse(RoundPlanner.canUseAwake(0)) // đang ở màn 1
        assertTrue(RoundPlanner.canUseAwake(1)) // đang ở màn 2
    }

    @Test
    fun overallProgressSpansAllRounds() {
        assertEquals(0f, RoundPlanner.overallProgress(0, 0f, 4), 0.0001f)
        assertEquals(0.625f, RoundPlanner.overallProgress(2, 0.5f, 4), 0.0001f)
        assertEquals(1f, RoundPlanner.overallProgress(4, 0f, 4), 0.0001f)
    }
}
