package com.nhom.challengealarm

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MathChallengeTest {

    /** Tính biểu thức có + − × ÷ và ngoặc (các phần cách nhau bằng dấu cách), nhân chia trước, cộng trừ sau. */
    private class Parser(private val expr: String) {
        private val tokens = expr.trim().split(" ").filter { it.isNotEmpty() }
        private var pos = 0

        fun parse(): Int = sum().also { check(pos == tokens.size) { "Thừa ký tự: $expr" } }

        private fun sum(): Int {
            var v = product()
            while (pos < tokens.size && (tokens[pos] == "+" || tokens[pos] == "−")) {
                val op = tokens[pos++]
                val n = product()
                v = if (op == "+") v + n else v - n
            }
            return v
        }

        private fun product(): Int {
            var v = factor()
            while (pos < tokens.size && (tokens[pos] == "×" || tokens[pos] == "÷")) {
                val op = tokens[pos++]
                val n = factor()
                v = if (op == "×") {
                    v * n
                } else {
                    check(v % n == 0) { "Phép chia không hết: $expr" }
                    v / n
                }
            }
            return v
        }

        private fun factor(): Int {
            val t = tokens[pos++]
            if (t == "(") {
                val v = sum()
                check(tokens[pos++] == ")") { "Thiếu ngoặc đóng: $expr" }
                return v
            }
            return t.toInt()
        }
    }

    private fun evaluate(expr: String): Int = Parser(expr).parse()

    /** Biểu thức: so đáp án với giá trị. Phương trình "? ...= r": thay ? bằng đáp án rồi so hai vế. */
    private fun assertCorrect(p: MathProblem) {
        if ("=" in p.text) {
            val (left, right) = p.text.split("=")
            assertEquals("Sai đáp án cho: ${p.text}", evaluate(right), evaluate(left.replace("?", p.answer.toString())))
        } else {
            assertEquals("Sai đáp án cho: ${p.text}", evaluate(p.text), p.answer)
        }
    }

    @Test
    fun answerMatchesExpressionForAllDifficulties() {
        val random = Random(42)
        for (difficulty in 1..3) {
            repeat(500) { assertCorrect(MathChallenge.generate(difficulty, random)) }
        }
    }

    @Test
    fun easyProblemsNeverHaveNegativeAnswers() {
        val random = Random(7)
        repeat(500) {
            assertTrue(MathChallenge.generate(1, random).answer >= 0)
        }
    }

    @Test
    fun problemsVaryBetweenCalls() {
        val random = Random(1)
        val distinct = (1..50).map { MathChallenge.generate(2, random).text }.toSet()
        assertTrue("Đề bị lặp quá nhiều", distinct.size > 20)
    }

    @Test
    fun everyLevelUsesSeveralProblemShapes() {
        val random = Random(3)
        for (difficulty in 1..3) {
            val shapes = (1..200).map { shape(MathChallenge.generate(difficulty, random).text) }.toSet()
            assertTrue("Mức $difficulty chỉ có ${shapes.size} dạng", shapes.size >= 4)
        }
    }

    @Test
    fun questionShowsEqualsOnlyOnce() {
        assertEquals("12 + 5 = ?", MathProblem("12 + 5", 17).question)
        assertEquals("? × 3 = 12", MathProblem("? × 3 = 12", 4).question)
    }

    /** Dạng bài: bỏ số, chỉ giữ dấu (ví dụ "? × N = N"). */
    private fun shape(text: String): String = text.replace(Regex("\\d+"), "N")
}
