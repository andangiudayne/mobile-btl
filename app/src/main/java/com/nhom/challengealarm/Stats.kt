package com.nhom.challengealarm

import java.util.Calendar
import java.util.TimeZone

/**
 * Tỉnh trong vòng 5 phút (không dùng thoát khẩn cấp, và không ngủ lại sau khi tắt chuông) thì tính là dậy đúng giờ.
 * Với chuông nhiều màn, "tỉnh" là lúc qua xong màn đầu, nên thời gian nghỉ giữa các màn không bị tính.
 */
const val ON_TIME_LIMIT_MS = 5 * 60_000L

/** Số ngày không có chuông liền nhau tối đa mà chuỗi ngày dậy đúng giờ vẫn giữ (đủ cho một cuối tuần). */
const val MAX_GAP_DAYS = 2

data class DayStat(val dayStart: Long, val avgSeconds: Long?)

data class WakeStats(
    val total: Int,
    val onTimeRate: Float?,
    val avgSeconds: Long?,
    val streakDays: Int,
    val last7: List<DayStat>,
    /** Số lần tắt chuông xong lại ngủ tiếp (không trả lời "Bạn còn thức không?"). */
    val relapses: Int = 0,
)

/** Tính thống kê từ nhật ký thức dậy. Hàm thuần, không phụ thuộc Android nên test được trên JVM. */
object StatsCalculator {

    private fun dayStart(millis: Long, zone: TimeZone): Long {
        val cal = Calendar.getInstance(zone).apply {
            timeInMillis = millis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    private fun addDays(dayStart: Long, days: Int, zone: TimeZone): Long {
        val cal = Calendar.getInstance(zone).apply { timeInMillis = dayStart }
        cal.add(Calendar.DAY_OF_YEAR, days)
        return cal.timeInMillis
    }

    private fun secondsToWake(log: WakeLog): Long = (log.awakeAt - log.firedAt).coerceAtLeast(0) / 1000L

    private fun isOnTime(log: WakeLog): Boolean =
        log.method != WakeMethod.EMERGENCY && !log.relapsed && log.awakeAt - log.firedAt <= ON_TIME_LIMIT_MS

    fun compute(logs: List<WakeLog>, now: Long, zone: TimeZone = TimeZone.getDefault()): WakeStats {
        val solved = logs.filter { it.method != WakeMethod.EMERGENCY }
        val avgSeconds = if (solved.isEmpty()) null
        else solved.map { secondsToWake(it) }.average().toLong()
        val onTimeRate = if (logs.isEmpty()) null else logs.count { isOnTime(it) }.toFloat() / logs.size

        // Chuỗi: đếm lùi từ hôm nay những ngày dậy đúng giờ.
        // Ngày có chuông mà không lần nào đúng giờ thì đứt chuỗi. Ngày không có chuông (ví dụ cuối tuần
        // với chuông chỉ đặt ngày thường) thì bỏ qua, nhưng nghỉ quá MAX_GAP_DAYS ngày liền cũng đứt.
        val onTimeDays = logs.filter { isOnTime(it) }.map { dayStart(it.firedAt, zone) }.toSet()
        val loggedDays = logs.map { dayStart(it.firedAt, zone) }.toSet()
        val today = dayStart(now, zone)
        var cursor = today
        var streak = 0
        var gap = 0
        while (true) {
            when {
                cursor in onTimeDays -> {
                    streak++
                    gap = 0
                }
                cursor in loggedDays -> break
                else -> {
                    gap++
                    if (gap > MAX_GAP_DAYS) break
                }
            }
            cursor = addDays(cursor, -1, zone)
        }

        val last7 = (6 downTo 0).map { back ->
            val start = addDays(today, -back, zone)
            val ofDay = solved.filter { dayStart(it.firedAt, zone) == start }
            DayStat(
                dayStart = start,
                avgSeconds = if (ofDay.isEmpty()) null
                else ofDay.map { secondsToWake(it) }.average().toLong(),
            )
        }

        return WakeStats(logs.size, onTimeRate, avgSeconds, streak, last7, logs.count { it.relapsed })
    }
}
