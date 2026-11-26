package com.nhom.challengealarm

import kotlin.random.Random

/**
 * Một bài toán. text là biểu thức ("37 + 48") hoặc phương trình có dấu "?" ("? × 7 = 56").
 * answer là số cần nhập.
 */
data class MathProblem(val text: String, val answer: Int) {
    /** Câu hỏi hiện trên màn hình: biểu thức thì thêm "= ?", phương trình thì giữ nguyên. */
    val question: String get() = if ("=" in text) text else "$text = ?"
}

/**
 * Sinh đề toán ngẫu nhiên. Mỗi mức khó có 4 dạng bài khác nhau để không bị quen tay:
 * dễ: cộng, trừ, tìm số hạng còn thiếu, bảng cửu chương;
 * vừa: cộng trừ 3 chữ số, nhân, chia hết, tìm thừa số còn thiếu;
 * khó: nhân rồi cộng, nhân rồi trừ, có ngoặc, phương trình hai bước.
 */
object MathChallenge {

    /** difficulty 1 = dễ, 2 = vừa, 3 = khó. Đề random mới mỗi lần nên không nhớ đáp án được. */
    fun generate(difficulty: Int, random: Random = Random.Default): MathProblem = when (difficulty) {
        1 -> easy(random)
        2 -> medium(random)
        else -> hard(random)
    }

    private fun easy(r: Random): MathProblem = when (r.nextInt(4)) {
        0 -> {
            val a = r.nextInt(10, 100)
            val b = r.nextInt(10, 100)
            MathProblem("$a + $b", a + b)
        }
        1 -> {
            val a = r.nextInt(10, 100)
            val b = r.nextInt(10, 100)
            MathProblem("${maxOf(a, b)} − ${minOf(a, b)}", maxOf(a, b) - minOf(a, b))
        }
        2 -> {
            val a = r.nextInt(10, 60)
            val b = r.nextInt(10, 60)
            MathProblem("? + $b = ${a + b}", a)
        }
        else -> {
            val a = r.nextInt(2, 10)
            val b = r.nextInt(2, 10)
            MathProblem("$a × $b", a * b)
        }
    }

    private fun medium(r: Random): MathProblem = when (r.nextInt(4)) {
        0 -> {
            val a = r.nextInt(100, 1000)
            val b = r.nextInt(100, 1000)
            if (r.nextBoolean()) MathProblem("$a + $b", a + b)
            else MathProblem("${maxOf(a, b)} − ${minOf(a, b)}", maxOf(a, b) - minOf(a, b))
        }
        1 -> {
            val a = r.nextInt(6, 20)
            val b = r.nextInt(6, 20)
            MathProblem("$a × $b", a * b)
        }
        2 -> {
            val b = r.nextInt(3, 13)
            val q = r.nextInt(4, 21)
            MathProblem("${b * q} ÷ $b", q)
        }
        else -> {
            val a = r.nextInt(3, 13)
            val b = r.nextInt(3, 13)
            MathProblem("? × $b = ${a * b}", a)
        }
    }

    private fun hard(r: Random): MathProblem = when (r.nextInt(4)) {
        0 -> {
            val a = r.nextInt(11, 30)
            val b = r.nextInt(6, 15)
            val c = r.nextInt(10, 200)
            MathProblem("$a × $b + $c", a * b + c)
        }
        1 -> {
            val a = r.nextInt(11, 30)
            val b = r.nextInt(6, 15)
            val c = r.nextInt(10, 200)
            MathProblem("$a × $b − $c", a * b - c)
        }
        2 -> {
            val a = r.nextInt(5, 31)
            val b = r.nextInt(5, 31)
            val c = r.nextInt(3, 10)
            MathProblem("( $a + $b ) × $c", (a + b) * c)
        }
        else -> {
            val a = r.nextInt(6, 16)
            val b = r.nextInt(3, 10)
            val c = r.nextInt(10, 61)
            MathProblem("? × $b + $c = ${a * b + c}", a)
        }
    }
}
