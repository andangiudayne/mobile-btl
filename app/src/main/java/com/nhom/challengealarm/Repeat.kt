package com.nhom.challengealarm

import java.util.Calendar

/** Tên ngày, thứ tự trùng với các bit của AlarmEntity.daysMask (bit 0 = Thứ hai ... bit 6 = Chủ nhật). */
val DAY_FULL = listOf("Thứ hai", "Thứ ba", "Thứ tư", "Thứ năm", "Thứ sáu", "Thứ bảy", "Chủ nhật")
val DAY_SHORT = listOf("T2", "T3", "T4", "T5", "T6", "T7", "CN")

const val ALL_DAYS_MASK = 0b1111111
const val WEEKDAYS_MASK = 0b0011111
const val WEEKEND_MASK = 0b1100000

/** Mô tả ngắn cách lặp, giống dòng "Lặp lại" của đồng hồ iOS. */
fun repeatSummary(mask: Int): String = when (mask) {
    0 -> "Không lặp lại"
    ALL_DAYS_MASK -> "Hằng ngày"
    WEEKDAYS_MASK -> "Ngày thường"
    WEEKEND_MASK -> "Cuối tuần"
    else -> DAY_SHORT.filterIndexed { i, _ -> (mask shr i) and 1 == 1 }.joinToString(", ")
}

/** Tìm tối đa chừng này ngày khi có ngày nghỉ (kỳ nghỉ hè có thể dài vài tháng). */
private const val MAX_SEARCH_DAYS = 400

/**
 * Lần reo kế tiếp (millis) của chuông hour:minute lặp theo daysMask, hoặc null nếu không có.
 * skipUntil: các lần reo đến hết mốc này bị bỏ qua ("Bỏ qua lần reo tới"); mốc đã qua thì không ảnh hưởng gì.
 * isDayOff: ngày nghỉ (lễ, kỳ nghỉ) thì chuông lặp không reo ngày đó; null = không xét ngày nghỉ.
 * Chuông một lần (daysMask = 0) luôn reo đúng ngày đã đặt, không xét ngày nghỉ.
 * Hàm thuần (chỉ dùng Calendar) để test được trên JVM.
 */
fun nextTriggerAt(
    hour: Int,
    minute: Int,
    daysMask: Int,
    skipUntil: Long,
    now: Long,
    isDayOff: ((Ymd) -> Boolean)? = null,
): Long? {
    // Bắt đầu tìm từ mốc muộn hơn giữa "bây giờ" và "bỏ qua đến". Tìm từ chính mốc đó (không phải từ hôm nay)
    // để chuông chỉ reo thứ Hai mà bỏ qua thứ Hai tuần sau vẫn tìm ra thứ Hai tuần sau nữa.
    val from = maxOf(now, skipUntil)
    val cal = Calendar.getInstance().apply {
        timeInMillis = from
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    if (daysMask == 0) {
        if (cal.timeInMillis <= from) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }
    repeat(if (isDayOff == null) 8 else MAX_SEARCH_DAYS) {
        val dayBit = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7 // Calendar: CN=1..T7=7 -> T2=0..CN=6
        if (cal.timeInMillis > from && (daysMask shr dayBit) and 1 == 1) {
            val day = Ymd(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
            if (isDayOff == null || !isDayOff(day)) return cal.timeInMillis
        }
        cal.add(Calendar.DAY_OF_YEAR, 1)
    }
    return null
}
