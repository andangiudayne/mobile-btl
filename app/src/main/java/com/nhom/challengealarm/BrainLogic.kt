package com.nhom.challengealarm

import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.random.Random

// =================================================================================================
// Logic thuần (không phụ thuộc Android) của các thử thách mới và quy tắc sinh hình cho "Vẽ lại hình".
// Tách riêng để test được trên JVM; giao diện nằm ở BrainChallenges.kt.
// Mức khó d = 1 (dễ), 2 (vừa), 3 (khó); giá trị ngoài khoảng được kẹp lại.
// =================================================================================================

private fun <T> level(values: List<T>, d: Int): T = values[(d - 1).coerceIn(0, values.lastIndex)]

/**
 * "Vẽ lại hình": sinh đường đi qua các chấm 3x3 mà vuốt thẳng giữa hai chấm liên tiếp không đi xuyên qua
 * một chấm chưa dùng (giống quy tắc mở khóa hình của Android). Trước đây hình kiểu 0 → 2 (bỏ qua chấm 1)
 * gần như không vẽ lại được vì ngón tay chạm vào chấm giữa.
 */
object PatternRules {
    /** Chấm nằm chính giữa đoạn a-b (nếu có), ví dụ giữa 0 và 2 là 1, giữa 0 và 8 là 4. */
    fun middle(a: Int, b: Int): Int? {
        val r = (a / 3) + (b / 3)
        val c = (a % 3) + (b % 3)
        if (r % 2 != 0 || c % 2 != 0) return null
        val m = (r / 2) * 3 + c / 2
        return if (m == a || m == b) null else m
    }

    /** Bước a → b hợp lệ khi không có chấm giữa, hoặc chấm giữa đã được đi qua trước đó. */
    fun validStep(visited: List<Int>, from: Int, to: Int): Boolean {
        val m = middle(from, to) ?: return true
        return m in visited
    }

    fun isValid(pattern: List<Int>): Boolean =
        pattern.distinct().size == pattern.size &&
            pattern.indices.drop(1).all { i -> validStep(pattern.take(i), pattern[i - 1], pattern[i]) }

    fun generate(length: Int, random: Random = Random.Default): List<Int> {
        val n = length.coerceIn(2, 9)
        while (true) {
            val path = mutableListOf(random.nextInt(9))
            while (path.size < n) {
                val options = (0..8).filter { it !in path && validStep(path, path.last(), it) }
                if (options.isEmpty()) break
                path += options[random.nextInt(options.size)]
            }
            if (path.size == n) return path
        }
    }
}

/** "Tìm ô khác màu": lưới n x n cùng màu, có một ô sáng hoặc tối hơn một chút. */
data class OddRound(
    val size: Int,
    val odd: Int,
    val hue: Float,
    val saturation: Float,
    val value: Float,
    val oddValue: Float,
)

object OddColor {
    fun size(d: Int) = level(listOf(3, 4, 5), d)
    /** Độ chênh độ sáng giữa ô khác và các ô còn lại: càng khó càng nhỏ. */
    fun delta(d: Int) = level(listOf(0.22f, 0.14f, 0.09f), d)
    fun target(d: Int) = level(listOf(5, 7, 9), d)

    fun next(d: Int, random: Random = Random.Default, previous: OddRound? = null): OddRound {
        val n = size(d)
        val value = 0.55f + random.nextFloat() * 0.25f
        val delta = delta(d)
        val oddValue = if (random.nextBoolean() && value + delta <= 1f) value + delta else value - delta
        var odd = random.nextInt(n * n)
        if (previous != null && n * n > 1) while (odd == previous.odd) odd = random.nextInt(n * n)
        return OddRound(
            size = n,
            odd = odd,
            hue = random.nextFloat() * 359f,
            saturation = 0.45f + random.nextFloat() * 0.35f,
            value = value,
            oddValue = oddValue,
        )
    }
}

/** "Nhớ dãy số": nhìn dãy số vài giây rồi bấm lại (bằng bàn phím số trên màn hình, không dùng bàn phím hệ thống). */
object DigitSpan {
    fun length(d: Int) = level(listOf(4, 5, 7), d)
    fun rounds(d: Int) = level(listOf(2, 3, 3), d)
    /** Thời gian hiện dãy: dài hơn với dãy dài hơn. */
    fun showMs(length: Int): Long = 800L + 550L * length

    /** Dãy số ngẫu nhiên, hai số liền nhau không trùng (dễ nhìn nhầm thành một). */
    fun generate(length: Int, random: Random = Random.Default): List<Int> {
        val out = mutableListOf<Int>()
        while (out.size < length) {
            val d = random.nextInt(10)
            if (out.lastOrNull() != d) out += d
        }
        return out
    }
}

/** "Xếp hình trượt" 3x3: ô số 1..8 và một ô trống (0). Xáo bằng các nước đi hợp lệ nên luôn giải được. */
object SlidePuzzle {
    val SOLVED = listOf(1, 2, 3, 4, 5, 6, 7, 8, 0)

    fun moves(d: Int) = level(listOf(12, 24, 40), d)

    fun neighbors(i: Int): List<Int> {
        val r = i / 3
        val c = i % 3
        return buildList {
            if (r > 0) add(i - 3)
            if (r < 2) add(i + 3)
            if (c > 0) add(i - 1)
            if (c < 2) add(i + 1)
        }
    }

    fun canMove(tiles: List<Int>, i: Int): Boolean = i in neighbors(tiles.indexOf(0))

    /** Chạm ô i: nếu nằm cạnh ô trống thì trượt vào ô trống, không thì giữ nguyên. */
    fun move(tiles: List<Int>, i: Int): List<Int> {
        if (!canMove(tiles, i)) return tiles
        val blank = tiles.indexOf(0)
        return tiles.toMutableList().also {
            it[blank] = tiles[i]
            it[i] = 0
        }
    }

    fun isSolved(tiles: List<Int>): Boolean = tiles == SOLVED

    /** Số ô đã đúng chỗ (0 đến 8), dùng làm tiến độ. */
    fun correctCount(tiles: List<Int>): Int = (0 until 8).count { tiles[it] == it + 1 }

    fun shuffled(moves: Int, random: Random = Random.Default): List<Int> {
        while (true) {
            var t = SOLVED
            var previousBlank = -1
            repeat(moves) {
                val blank = t.indexOf(0)
                // Không đẩy ô trống quay lại chỗ vừa đi, để xáo thật sự.
                val options = neighbors(blank).filter { it != previousBlank }
                val pick = options[random.nextInt(options.size)]
                previousBlank = blank
                t = move(t, pick)
            }
            if (!isSolved(t)) return t
        }
    }
}

/** "Giữ thăng bằng": nghiêng máy để đưa viên bi vào vòng tròn đích và giữ yên. Tọa độ trong hình tròn bán kính 1. */
object TiltMath {
    /** Gia tốc (m/s²) ứng với mép vòng tròn, khoảng 35 độ nghiêng. */
    const val MAX_ACCEL = 5.5f
    fun targetRadius(d: Int) = level(listOf(0.24f, 0.18f, 0.13f), d)
    fun targets(d: Int) = level(listOf(2, 3, 4), d)
    const val HOLD_MS = 2_000L

    /**
     * Vị trí bi từ gia tốc kế (máy dựng dọc, trục x sang phải, trục y lên trên màn hình): nghiêng mép phải xuống thì bi lăn
     * sang phải, nghiêng mép trên xuống thì bi lăn lên. Trả về (x, y) theo hướng màn hình (y dương là xuống), kẹp trong
     * hình tròn bán kính 1.
     */
    fun ball(ax: Float, ay: Float): Pair<Float, Float> {
        var x = -ax / MAX_ACCEL
        var y = ay / MAX_ACCEL
        val len = sqrt(x * x + y * y)
        if (len > 1f) {
            x /= len
            y /= len
        }
        return x to y
    }

    /** Làm mượt để bi không rung theo tay. */
    fun smooth(previous: Float, next: Float, alpha: Float = 0.25f): Float = previous + (next - previous) * alpha

    fun inside(bx: Float, by: Float, tx: Float, ty: Float, radius: Float): Boolean {
        val dx = bx - tx
        val dy = by - ty
        return dx * dx + dy * dy <= radius * radius
    }

    /** Đích mới: cách tâm 0,35 đến 0,65 (phải nghiêng thật), không trùng đích trước. */
    fun nextTarget(random: Random = Random.Default, previous: Pair<Float, Float>? = null): Pair<Float, Float> {
        while (true) {
            val angle = random.nextDouble() * 2 * Math.PI
            val r = 0.35 + random.nextDouble() * 0.3
            val t = (r * kotlin.math.cos(angle)).toFloat() to (r * kotlin.math.sin(angle)).toFloat()
            if (previous == null || abs(t.first - previous.first) + abs(t.second - previous.second) > 0.5f) return t
        }
    }
}

/** "So sánh nhanh": hai phép tính, chọn bên có kết quả lớn hơn. Hai kết quả luôn khác nhau và khá sát nhau. */
data class CompareItem(val left: String, val leftValue: Int, val right: String, val rightValue: Int) {
    /** true nếu bên trái lớn hơn. */
    val leftBigger: Boolean get() = leftValue > rightValue
}

object CompareQuiz {
    fun target(d: Int) = level(listOf(6, 8, 10), d)

    private fun expression(d: Int, random: Random): Pair<String, Int> {
        val kind = when (d.coerceIn(1, 3)) {
            1 -> 0
            2 -> random.nextInt(2)
            else -> random.nextInt(3)
        }
        return when (kind) {
            0 -> {
                val a = random.nextInt(10, 50)
                val b = random.nextInt(10, 50)
                "$a + $b" to a + b
            }
            1 -> {
                val a = random.nextInt(40, 100)
                val b = random.nextInt(10, a)
                "$a − $b" to a - b
            }
            else -> {
                val a = random.nextInt(3, 13)
                val b = random.nextInt(3, 13)
                "$a × $b" to a * b
            }
        }
    }

    fun next(d: Int, random: Random = Random.Default): CompareItem {
        var best: CompareItem? = null
        repeat(300) {
            val (l, lv) = expression(d, random)
            val (r, rv) = expression(d, random)
            if (lv != rv) {
                val item = CompareItem(l, lv, r, rv)
                // Ưu tiên hai kết quả sát nhau (chênh không quá 15%) để phải tính thật chứ không nhìn qua là biết.
                if (abs(lv - rv) <= maxOf(3, (maxOf(lv, rv) * 0.15).toInt())) return item
                if (best == null) best = item
            }
        }
        return best ?: CompareItem("10 + 11", 21, "10 + 10", 20)
    }
}

/** "Đọc đồng hồ": đồng hồ kim hiện một giờ, chọn đáp án đúng trong 4 lựa chọn. */
data class ClockItem(val hour: Int, val minute: Int, val options: List<String>) {
    val answer: String get() = ClockQuiz.text(hour, minute)
}

object ClockQuiz {
    fun target(d: Int) = level(listOf(3, 4, 5), d)
    /** Bước phút: dễ là 15 phút, vừa 5 phút, khó từng phút. */
    fun step(d: Int) = level(listOf(15, 5, 1), d)

    fun text(hour: Int, minute: Int): String = "%d:%02d".format(hour, minute)

    private fun wrapHour(h: Int): Int = ((h - 1) % 12 + 12) % 12 + 1

    fun next(d: Int, random: Random = Random.Default): ClockItem {
        val step = step(d)
        val hour = random.nextInt(1, 13)
        val minute = random.nextInt(60 / step) * step
        val correct = text(hour, minute)
        val candidates = linkedSetOf<String>()
        // Nhầm kim giờ với kim phút: lỗi hay gặp nhất khi còn ngái ngủ.
        val swappedHour = if (minute == 0) 12 else minute / 5
        if (minute % 5 == 0) candidates += text(swappedHour, (hour % 12) * 5)
        candidates += text(wrapHour(hour + 1), minute)
        candidates += text(wrapHour(hour - 1), minute)
        candidates += text(hour, (minute + maxOf(step, 5)) % 60)
        candidates += text(hour, (minute - maxOf(step, 5) + 60) % 60)
        val options = mutableListOf(correct)
        for (c in candidates.shuffled(random)) {
            if (options.size == 4) break
            if (c !in options) options += c
        }
        while (options.size < 4) {
            val c = text(random.nextInt(1, 13), random.nextInt(60 / step) * step)
            if (c !in options) options += c
        }
        return ClockItem(hour, minute, options.shuffled(random))
    }
}

/**
 * So khớp câu gõ lại (thử thách "Gõ lại câu"). Gõ có dấu hay không dấu đều được và không phân biệt hoa thường:
 * máy nào không có bàn phím tiếng Việt vẫn gõ được, và lúc bộ gõ Telex/VNI đang ghép dấu chữ không bị báo sai.
 * Khoảng trắng thừa (nhiều dấu cách liền nhau, dấu cách ở đầu, dấu cách bàn phím tự thêm ở cuối) cũng không tính.
 */
object TypingMatch {
    /** Bỏ dấu, đ thành d, viết thường. Mỗi ký tự của câu gốc (dạng NFC) cho đúng một ký tự. */
    fun fold(text: String): String {
        val out = StringBuilder(text.length)
        for (c in java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)) {
            if (Character.getType(c) == Character.NON_SPACING_MARK.toInt()) continue
            out.append(if (c == 'đ' || c == 'Đ') 'd' else c.lowercaseChar())
        }
        return out.toString()
    }

    /** Chuỗi người dùng gõ sau khi bỏ dấu và gộp khoảng trắng. */
    fun normalizeTyped(typed: String): String = fold(typed).replace(WHITESPACE, " ").trimStart()

    /** Số ký tự đầu câu đã gõ đúng (để tô màu và tính tiến độ). */
    fun correctPrefix(phrase: String, typed: String): Int {
        val p = fold(phrase)
        val t = normalizeTyped(typed)
        var i = 0
        while (i < p.length && i < t.length && p[i] == t[i]) i++
        return i
    }

    /** Từng ký tự của câu: true = gõ đúng, false = gõ sai, null = chưa gõ tới. */
    fun charStates(phrase: String, typed: String): List<Boolean?> {
        val p = fold(phrase)
        val t = normalizeTyped(typed)
        return p.indices.map { i -> if (i >= t.length) null else p[i] == t[i] }
    }

    fun isDone(phrase: String, typed: String): Boolean = normalizeTyped(typed).trimEnd() == fold(phrase)

    private val WHITESPACE = Regex("\\s+")
}
