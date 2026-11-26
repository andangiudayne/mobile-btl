package com.nhom.challengealarm

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Lịch sử 30 ngày nạp sẵn khi cài app (xem InitialData), để Thống kê, Giấc ngủ có số liệu ngay từ lần mở đầu tiên.
 * Một sinh viên: ngày thường chuông 6:30 ("Đi học", 3 màn), thứ Bảy 7:30 ("Cuối tuần"), Chủ nhật không đặt chuông.
 * Đầu tháng ngủ muộn, ngủ thiếu, dậy chậm; cuối tháng ngủ sớm hơn và dậy nhanh hơn, để các biểu đồ, chuỗi ngày,
 * nợ ngủ và nhận xét "ngủ đủ thì dậy nhanh hơn" đều có số liệu.
 *
 * Nhật ký gắn với id hai chuông thật "Đi học" và "Cuối tuần" (truyền vào schoolAlarmId, weekendAlarmId).
 * Hàm thuần, cùng seed thì cùng kết quả, test được trên JVM.
 */
object DemoData {
    /** Id cũ của bản dữ liệu thử trước đây (chỉ dùng để dọn); nay nhật ký gắn với chuông thật. */
    const val ALARM_ID = -100
    const val SCHOOL_LABEL = "Đi học"
    const val WEEKEND_LABEL = "Cuối tuần"
    const val DAYS = 30
    const val SEED = 2026

    data class Bundle(val wakeLogs: List<WakeLog>, val sleepLogs: List<SleepLog>)

    /** Thử thách của hai chuông (khớp với cách InitialData tạo chuông). */
    val SCHOOL_CHALLENGES = listOf(0, -3, -4, -10, -14) // giải toán, gõ câu, nhớ màu, màu chữ, ô khác màu
    val WEEKEND_CHALLENGES = listOf(-16, -19) // xếp hình trượt, đọc đồng hồ

    private val SCHOOL_METHODS = listOf(WakeMethod.MATH, WakeMethod.TYPING, WakeMethod.MEMORY, WakeMethod.STROOP, WakeMethod.ODD)
    private val WEEKEND_METHODS = listOf(WakeMethod.SLIDE, WakeMethod.CLOCK)

    private fun at(dayStart: Long, hour: Int, minute: Int, zone: TimeZone): Long =
        Calendar.getInstance(zone).apply {
            timeInMillis = dayStart
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    private fun daysBefore(dayStart: Long, days: Int, zone: TimeZone): Long =
        Calendar.getInstance(zone).apply {
            timeInMillis = dayStart
            add(Calendar.DAY_OF_YEAR, -days)
        }.timeInMillis

    /** Hồ sơ sinh viên 21 tuổi đi kèm lịch sử (sửa lại được trong Hồ sơ), để tab Giấc ngủ có gợi ý theo tuổi. */
    fun sampleProfile(now: Long): UserProfile = UserProfile(
        name = "",
        birthYear = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.YEAR) - 21,
        occupation = Occupation.UNIVERSITY,
        activity = ActivityLevel.MEDIUM,
        chronotype = Chronotype.NEUTRAL,
        lateCaffeine = true,
        nightStartMin = AgeGroup.YOUNG.defaultNightStart,
        remind = true,
    )

    fun generate(
        now: Long,
        zone: TimeZone = TimeZone.getDefault(),
        seed: Int = SEED,
        schoolAlarmId: Int = ALARM_ID,
        weekendAlarmId: Int = ALARM_ID,
    ): Bundle {
        val random = Random(seed)
        val today = SleepAnalyzer.dayStart(now, zone)
        val wakeLogs = mutableListOf<WakeLog>()
        val sleepLogs = mutableListOf<SleepLog>()

        for (back in DAYS - 1 downTo 0) {
            val day = daysBefore(today, back, zone)
            val dow = Calendar.getInstance(zone).apply { timeInMillis = day }.get(Calendar.DAY_OF_WEEK)
            if (dow == Calendar.SUNDAY) continue // Chủ nhật không đặt chuông
            val weekend = dow == Calendar.SATURDAY
            val firedAt = if (weekend) at(day, 7, 30, zone) else at(day, 6, 30, zone)
            if (firedAt >= now) continue // sáng nay chưa tới giờ chuông

            // 0 = đầu tháng, 1 = hôm nay: thói quen tốt dần lên.
            val p = (DAYS - 1 - back) / (DAYS - 1f)

            // Giờ đi ngủ (phút so với 0h; âm là trước nửa đêm): từ khoảng 0:40 dời dần về khoảng 22:45.
            // Tối thứ Sáu (sáng thứ Bảy dậy) thường ngủ muộn hơn nửa tiếng.
            val weekendLate = if (weekend) 30 else 0
            val bedOffset = (40 - 115 * p + random.nextInt(-20, 21) + weekendLate).roundToInt()
            val sleepStart = day + bedOffset * 60_000L
            val interruptions = if (p < 0.5f) random.nextInt(0, 4) else random.nextInt(0, 2)
            val awakeMin = interruptions * random.nextInt(2, 7)
            val asleepMin = ((firedAt - sleepStart) / 60_000L).toInt() - awakeMin
            sleepLogs += SleepLog(
                nightOf = day,
                sleepStart = sleepStart,
                wakeAt = firedAt,
                asleepMin = asleepMin,
                awakeMin = awakeMin,
                interruptions = interruptions,
            )

            // Ngủ càng ít thì càng lâu mới tỉnh (khoảng 40 giây khi ngủ đủ, vài phút khi thiếu ngủ nhiều).
            val shortfall = (480 - asleepMin).coerceAtLeast(0)
            var wakeSec = (40 + shortfall * 1.8f + random.nextInt(0, 45)).roundToInt()
            if (p > 0.7f) wakeSec = wakeSec.coerceAtMost(200) // cuối tháng đã quen, dậy đúng giờ đều

            val rounds = if (weekend) 1 else 3
            val wokeAt = firedAt + wakeSec * 1000L
            // Chuông 3 màn: thêm thời gian các màn sau và giờ nghỉ 1 phút giữa màn.
            val dismissedAt = wokeAt + (rounds - 1) * (60_000L + random.nextInt(25, 70) * 1000L)
            val method = when {
                wakeLogs.size == 7 -> WakeMethod.EMERGENCY // một lần có việc gấp phải thoát khẩn cấp
                rounds > 1 && random.nextInt(4) == 0 -> WakeMethod.AWAKE
                else -> (if (weekend) WEEKEND_METHODS else SCHOOL_METHODS).let { it[random.nextInt(it.size)] }
            }
            // Hai lần đầu tháng tắt chuông xong lại ngủ tiếp (không trả lời "Bạn còn thức không?").
            val relapsed = wakeLogs.size == 2 || wakeLogs.size == 9
            wakeLogs += WakeLog(
                alarmId = if (weekend) weekendAlarmId else schoolAlarmId,
                label = if (weekend) WEEKEND_LABEL else SCHOOL_LABEL,
                firedAt = firedAt,
                dismissedAt = dismissedAt,
                method = method,
                wokeAt = wokeAt,
                rounds = when (method) {
                    WakeMethod.EMERGENCY -> 0
                    WakeMethod.AWAKE -> 2 // qua 2 màn rồi giữ "Bạn đã tỉnh"
                    else -> rounds
                },
                relapsed = relapsed,
            )
        }
        return Bundle(wakeLogs, sleepLogs)
    }
}
