package com.nhom.challengealarm

import java.util.Calendar
import java.util.TimeZone

/** Một lần màn hình bật (on = true) hoặc tắt (on = false). */
data class ScreenEvent(val at: Long, val on: Boolean)

/** Kết quả một đêm: ngủ từ sleepStart đến wakeAt, trừ các lần thức dậy dùng điện thoại. */
data class SleepNight(
    val sleepStart: Long,
    val wakeAt: Long,
    val asleepMs: Long,
    val awakeMs: Long,
    val interruptions: Int,
)

/**
 * Đoán giờ đi ngủ từ lịch sử bật/tắt màn hình. Hàm thuần, không phụ thuộc Android nên test được trên JVM.
 *
 * Quy tắc:
 * 1. Chỉ xét từ "giờ bắt đầu tính" (mặc định theo tuổi, ví dụ 21:00 hoặc 23:00) đến lúc chuông reo.
 * 2. Màn hình tắt liền từ 15 phút trở lên thì tính là đã ngủ.
 * 3. Lấy quãng tắt màn hình dài nhất làm giấc ngủ chính, rồi lùi về các quãng tắt 15 phút trở lên ngay trước nó
 *    nếu giữa chúng chỉ bật máy chốc lát (dưới 10 phút). Nhờ vậy lúc đặt máy xuống xem phim rồi lại cầm lên dùng
 *    cả tiếng không bị tính là đã ngủ.
 * 4. Các lần bật màn hình sau khi ngủ bị trừ khỏi thời gian ngủ; lần bật từ 1 phút trở lên tính là một lần thức giấc.
 */
object SleepAnalyzer {
    const val MIN_OFF_MS = 15 * 60_000L
    const val MAX_GAP_MS = 10 * 60_000L
    const val MIN_WAKE_MS = 60_000L
    const val MIN_NIGHT_MS = 90 * 60_000L
    /** Chuông reo quá 16 tiếng sau giờ bắt đầu tính thì không phải giấc đêm (ví dụ chuông buổi chiều). */
    const val MAX_WINDOW_MS = 16 * 3_600_000L

    private data class Segment(val start: Long, val end: Long, val on: Boolean) {
        val length: Long get() = end - start
    }

    /** Mốc "giờ bắt đầu tính" gần nhất trước lúc thức dậy, hoặc null nếu cách quá xa. */
    fun windowStart(wakeAt: Long, nightStartMin: Int, zone: TimeZone = TimeZone.getDefault()): Long? {
        val cal = Calendar.getInstance(zone).apply {
            timeInMillis = wakeAt
            set(Calendar.HOUR_OF_DAY, (nightStartMin / 60).coerceIn(0, 23))
            set(Calendar.MINUTE, (nightStartMin % 60).coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (cal.timeInMillis >= wakeAt) cal.add(Calendar.DAY_OF_YEAR, -1)
        val start = cal.timeInMillis
        return if (wakeAt - start in 1..MAX_WINDOW_MS) start else null
    }

    fun analyze(events: List<ScreenEvent>, windowStart: Long, wakeAt: Long): SleepNight? {
        if (wakeAt <= windowStart) return null
        val sorted = events.sortedBy { it.at }

        // Trạng thái lúc bắt đầu cửa sổ: theo sự kiện cuối cùng trước đó. Không có thì suy từ sự kiện đầu tiên
        // trong cửa sổ (sự kiện đầu là "tắt" nghĩa là trước đó màn hình đang bật). Không có gì thì coi như đang tắt.
        var on = sorted.lastOrNull { it.at <= windowStart }?.on
            ?: sorted.firstOrNull { it.at > windowStart && it.at < wakeAt }?.let { !it.on }
            ?: false
        val segments = mutableListOf<Segment>()
        var cursor = windowStart
        for (e in sorted) {
            if (e.at <= windowStart || e.at >= wakeAt) continue
            if (e.on == on) continue
            if (e.at > cursor) segments += Segment(cursor, e.at, on)
            cursor = e.at
            on = e.on
        }
        segments += Segment(cursor, wakeAt, on)

        // Giấc ngủ chính: quãng tắt màn hình dài nhất (đủ 15 phút).
        val main = segments.indices
            .filter { !segments[it].on && segments[it].length >= MIN_OFF_MS }
            .maxByOrNull { segments[it].length } ?: return null

        // Lùi về trước qua những lần cầm máy chốc lát.
        var first = main
        while (first - 2 >= 0) {
            val gap = segments[first - 1]
            val before = segments[first - 2]
            if (gap.on && gap.length < MAX_GAP_MS && !before.on && before.length >= MIN_OFF_MS) {
                first -= 2
            } else {
                break
            }
        }

        val sleepStart = segments[first].start
        val wakeSegments = segments.drop(first).filter { it.on }
        val awakeMs = wakeSegments.sumOf { it.length }
        val interruptions = wakeSegments.count { it.length >= MIN_WAKE_MS }
        val asleepMs = wakeAt - sleepStart - awakeMs
        if (asleepMs < MIN_NIGHT_MS) return null
        return SleepNight(sleepStart, wakeAt, asleepMs, awakeMs, interruptions)
    }

    /** 0h của ngày chứa thời điểm t (dùng làm khóa "đêm của buổi sáng nào"). */
    fun dayStart(t: Long, zone: TimeZone = TimeZone.getDefault()): Long =
        Calendar.getInstance(zone).apply {
            timeInMillis = t
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
}
