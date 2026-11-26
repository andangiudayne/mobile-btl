package com.nhom.challengealarm

import kotlin.random.Random

/** Từ màn thứ mấy thì hiện nút "Bạn đã tỉnh" (màn đếm từ 1). */
const val AWAKE_FROM_ROUND = 2

/** Giữ nút "Bạn đã tỉnh" bao lâu thì tắt chuông. Đủ dài để không bấm nhầm khi còn ngái ngủ. */
const val AWAKE_HOLD_MS = 3_000L

/** Logic thuần của chuông nhiều màn, không phụ thuộc Android nên test được trên JVM. */
object RoundPlanner {

    /**
     * Chọn thử thách cho màn kế tiếp. Có từ 2 loại trở lên thì không lặp lại đúng thử thách vừa làm,
     * để mỗi màn là một việc khác nhau.
     */
    fun <T> next(pool: List<T>, previous: T?, random: Random = Random.Default): T {
        require(pool.isNotEmpty()) { "pool must not be empty" }
        val choices = if (pool.distinct().size > 1 && previous != null) pool.filter { it != previous } else pool
        return choices[random.nextInt(choices.size)]
    }

    /**
     * Thời gian chuông im sau một màn (millis). Chỉ nghỉ sau màn trí não và khi vẫn còn màn tiếp theo.
     * roundsDone = số màn đã xong, tính cả màn vừa xong.
     */
    fun breakMillis(mental: Boolean, breakMinutes: Int, roundsDone: Int, totalRounds: Int): Long {
        if (!mental || roundsDone >= totalRounds) return 0L
        return breakMinutes.coerceIn(0, MAX_BREAK_MINUTES) * 60_000L
    }

    /** Màn hiện tại (đếm từ 1) đã được phép dùng nút "Bạn đã tỉnh" chưa. */
    fun canUseAwake(roundsDone: Int): Boolean = roundsDone + 1 >= AWAKE_FROM_ROUND

    /** Tiến độ chung từ 0 đến 1: các màn đã xong cộng phần đang làm của màn hiện tại. */
    fun overallProgress(roundsDone: Int, currentFraction: Float, totalRounds: Int): Float {
        val total = totalRounds.coerceAtLeast(1)
        return ((roundsDone + currentFraction.coerceIn(0f, 1f)) / total).coerceIn(0f, 1f)
    }
}
