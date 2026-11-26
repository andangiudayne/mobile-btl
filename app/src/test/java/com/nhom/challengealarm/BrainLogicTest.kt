package com.nhom.challengealarm

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrainLogicTest {

    @Test
    fun patternMiddleDots() {
        assertEquals(1, PatternRules.middle(0, 2))
        assertEquals(4, PatternRules.middle(0, 8))
        assertEquals(4, PatternRules.middle(2, 6))
        assertEquals(7, PatternRules.middle(6, 8))
        assertNull(PatternRules.middle(0, 1)) // hai chấm cạnh nhau
        assertNull(PatternRules.middle(0, 5)) // nước đi kiểu quân mã, không qua chấm nào
    }

    @Test
    fun generatedPatternsCanAlwaysBeDrawnWithStraightSwipes() {
        val random = Random(11)
        repeat(500) {
            val length = 4 + it % 4
            val p = PatternRules.generate(length, random)
            assertEquals(length, p.size)
            assertTrue("hình $p phải vẽ được", PatternRules.isValid(p))
        }
    }

    @Test
    fun jumpingOverUnvisitedDotIsInvalid() {
        assertFalse(PatternRules.isValid(listOf(0, 2, 5)))
        assertTrue(PatternRules.isValid(listOf(1, 0, 2))) // chấm giữa (1) đã đi qua trước đó
    }

    @Test
    fun oddColorHasExactlyOneDifferentTileAndGetsHarder() {
        val random = Random(3)
        var previous: OddRound? = null
        repeat(300) {
            val r = OddColor.next(3, random, previous)
            assertTrue(r.odd in 0 until r.size * r.size)
            assertEquals(OddColor.delta(3), kotlin.math.abs(r.value - r.oddValue), 0.0001f)
            if (previous != null) assertNotEquals(previous!!.odd, r.odd)
            previous = r
        }
        assertTrue(OddColor.size(1) < OddColor.size(3))
        assertTrue(OddColor.delta(1) > OddColor.delta(3))
    }

    @Test
    fun digitSpanHasNoRepeatedNeighbours() {
        val random = Random(5)
        repeat(300) {
            val seq = DigitSpan.generate(7, random)
            assertEquals(7, seq.size)
            assertTrue(seq.all { d -> d in 0..9 })
            assertTrue(seq.zipWithNext().none { (a, b) -> a == b })
        }
        assertTrue(DigitSpan.showMs(7) > DigitSpan.showMs(4))
    }

    @Test
    fun slidePuzzleShuffleIsSolvableAndNotSolved() {
        val random = Random(8)
        repeat(200) {
            val t = SlidePuzzle.shuffled(SlidePuzzle.moves(2), random)
            assertFalse(SlidePuzzle.isSolved(t))
            assertEquals((0..8).toList(), t.sorted())
            assertTrue(solvable(t))
        }
    }

    @Test
    fun slideOnlyMovesTilesNextToTheBlank() {
        val solved = SlidePuzzle.SOLVED // ô trống ở góc dưới phải (vị trí 8)
        assertEquals(solved, SlidePuzzle.move(solved, 0)) // ô 1 ở xa, không trượt được
        val moved = SlidePuzzle.move(solved, 7)
        assertEquals(0, moved[7])
        assertEquals(8, moved[8])
        assertEquals(SlidePuzzle.SOLVED, SlidePuzzle.move(moved, 8))
        assertEquals(7, SlidePuzzle.correctCount(moved))
    }

    /** Lưới 3x3 giải được khi số cặp nghịch thế là số chẵn. */
    private fun solvable(t: List<Int>): Boolean {
        val xs = t.filter { it != 0 }
        var inversions = 0
        for (i in xs.indices) for (j in i + 1 until xs.size) if (xs[i] > xs[j]) inversions++
        return inversions % 2 == 0
    }

    @Test
    fun tiltBallFollowsTiltAndStaysInCircle() {
        val flat = TiltMath.ball(0f, 0f)
        assertEquals(0f, flat.first, 0.001f)
        assertEquals(0f, flat.second, 0.001f)
        assertTrue(TiltMath.ball(-3f, 0f).first > 0f) // nghiêng mép phải xuống: bi lăn sang phải
        assertTrue(TiltMath.ball(0f, -3f).second < 0f) // nghiêng mép trên xuống: bi lăn lên
        val (x, y) = TiltMath.ball(-20f, 20f)
        assertEquals(1f, kotlin.math.sqrt(x * x + y * y), 0.001f)
        assertTrue(TiltMath.inside(0.5f, 0.5f, 0.55f, 0.5f, 0.1f))
        assertFalse(TiltMath.inside(0f, 0f, 0.5f, 0.5f, 0.2f))
    }

    @Test
    fun tiltTargetsNeedARealTilt() {
        val random = Random(4)
        repeat(200) {
            val (x, y) = TiltMath.nextTarget(random)
            val r = kotlin.math.sqrt(x * x + y * y)
            assertTrue(r in 0.34f..0.66f)
        }
    }

    @Test
    fun compareNeverTiesAndMarksTheBiggerSide() {
        val random = Random(9)
        for (d in 1..3) repeat(200) {
            val item = CompareQuiz.next(d, random)
            assertNotEquals(item.leftValue, item.rightValue)
            assertEquals(item.leftValue > item.rightValue, item.leftBigger)
        }
    }

    @Test
    fun clockOptionsAreDistinctAndContainTheAnswer() {
        val random = Random(12)
        for (d in 1..3) repeat(200) {
            val item = ClockQuiz.next(d, random)
            assertEquals(4, item.options.size)
            assertEquals(4, item.options.toSet().size)
            assertTrue(item.answer in item.options)
            assertTrue(item.hour in 1..12)
            assertEquals(0, item.minute % ClockQuiz.step(d))
        }
    }

    // ------------------------------------------------------------------ Gõ lại câu

    @Test
    fun typingAcceptsWithOrWithoutDiacritics() {
        val phrase = "Dậy thôi nào"
        assertTrue(TypingMatch.isDone(phrase, "Dậy thôi nào"))
        assertTrue(TypingMatch.isDone(phrase, "day thoi nao"))
        assertTrue(TypingMatch.isDone(phrase, "DAY THOI NAO"))
        assertTrue(TypingMatch.isDone("Đứng dậy vươn vai", "dung day vuon vai"))
        assertFalse(TypingMatch.isDone(phrase, "day thoi"))
        assertFalse(TypingMatch.isDone(phrase, "day thoi nau"))
    }

    @Test
    fun typingIgnoresExtraSpacesFromTheKeyboard() {
        val phrase = "Mở mắt ra nào"
        assertTrue(TypingMatch.isDone(phrase, "mở mắt ra nào ")) // bàn phím tự thêm dấu cách sau từ gợi ý
        assertTrue(TypingMatch.isDone(phrase, " mở  mắt ra   nào"))
        assertEquals(phrase.length, TypingMatch.correctPrefix(phrase, "mo mat ra nao "))
    }

    @Test
    fun typingHandlesDecomposedTextFromSomeKeyboards() {
        // Một số bộ gõ đưa ra chữ cái + dấu rời (NFD) thay vì chữ dựng sẵn.
        val decomposed = java.text.Normalizer.normalize("Trời sáng rồi", java.text.Normalizer.Form.NFD)
        assertTrue(TypingMatch.isDone("Trời sáng rồi", decomposed))
    }

    @Test
    fun typingColorsEachCharacter() {
        val phrase = "Chào buổi sáng"
        val states = TypingMatch.charStates(phrase, "chao bx")
        assertEquals(phrase.length, states.size)
        assertEquals(true, states[0])
        assertEquals(true, states[5])
        assertEquals(false, states[6])
        assertNull(states[7])
        assertEquals(6, TypingMatch.correctPrefix(phrase, "chao bx"))
        // Mỗi ký tự của câu gốc ứng đúng một ký tự sau khi bỏ dấu, nên tô màu không bị lệch.
        listOf(
            "Mình dậy ngay bây giờ vì hôm nay còn nhiều việc quan trọng",
            "Người dậy sớm có thêm thời gian cho những điều mình yêu thích",
            "Ánh nắng buổi sáng giúp mình tỉnh",
        ).forEach { assertEquals(it.length, TypingMatch.fold(it).length) }
    }
}
