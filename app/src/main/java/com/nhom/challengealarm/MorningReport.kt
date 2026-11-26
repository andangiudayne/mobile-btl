package com.nhom.challengealarm

import java.util.TimeZone
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Bản tin buổi sáng, hiện ngay sau khi tắt chuông. Vấn đề: số liệu nằm trong tab Thống kê, Giấc ngủ mà ít ai mở;
 * phản hồi ngay lúc vừa dậy (tỉnh nhanh hay chậm, chuỗi ngày, đêm qua ngủ bao lâu) giúp giữ thói quen tốt.
 */
data class MorningSummary(
    val nap: Boolean = false,
    val wakeSeconds: Long? = null,
    val onTime: Boolean = false,
    val streakDays: Int = 0,
    val sleptMin: Int? = null,
    val bedAt: Long? = null,
    val targetMin: Int? = null,
    val debtMin: Int? = null,
    /** Lúc sẽ hỏi "Bạn còn thức không?" (null = không hỏi). */
    val wakeCheckAt: Long? = null,
    val tips: List<String> = emptyList(),
)

/** So sánh thời gian để tỉnh sau đêm ngủ đủ và đêm ngủ thiếu (giây trung bình). */
data class SleepWakeInsight(
    val enoughSec: Long,
    val shortSec: Long,
    val enoughNights: Int,
    val shortNights: Int,
) {
    /** Ngủ đủ thì tỉnh nhanh hơn bao nhiêu phần trăm (âm nếu chậm hơn). */
    val fasterPct: Int get() = if (shortSec <= 0) 0 else ((shortSec - enoughSec) * 100.0 / shortSec).roundToInt()
}

/** Hàm thuần, test được trên JVM. */
object MorningReport {
    /** Thiếu từ mức này trở lên so với mục tiêu thì nhắc tối nay ngủ sớm hơn. */
    const val SHORT_NIGHT_MIN = 45

    const val TIP_LIGHT = "Ra chỗ có ánh sáng tự nhiên 5 đến 10 phút và uống một cốc nước: ánh sáng buổi sáng giúp tỉnh hẳn " +
        "và tối dễ ngủ đúng giờ hơn."
    const val TIP_NAP = "Đứng dậy, rửa mặt và ra chỗ sáng: khoảng 10 đến 15 phút nữa sẽ hết ngái ngủ."
    const val TIP_NO_DATA = "Chưa đo được giấc ngủ đêm qua. Bật quyền \"Truy cập dữ liệu sử dụng\" để sáng mai có số liệu."

    /**
     * log = lần dậy vừa ghi; logs = nhật ký 30 ngày (đã có log); lastNight = đêm qua nếu đo được;
     * nights = các đêm gần đây; targetMin = mục tiêu theo tuổi (null nếu chưa có hồ sơ).
     */
    fun build(
        log: WakeLog,
        logs: List<WakeLog>,
        lastNight: SleepNight?,
        nights: List<SleepNight>,
        targetMin: Int?,
        wakeCheckAt: Long?,
        now: Long,
        zone: TimeZone = TimeZone.getDefault(),
    ): MorningSummary {
        val wakeMs = (log.awakeAt - log.firedAt).coerceAtLeast(0)
        val onTime = log.method != WakeMethod.EMERGENCY && wakeMs <= ON_TIME_LIMIT_MS
        val all = if (logs.any { it.firedAt == log.firedAt && it.alarmId == log.alarmId }) logs else logs + log
        val streak = StatsCalculator.compute(all, now, zone).streakDays
        val target = targetMin ?: SleepAdvisor.DEFAULT_TARGET_MIN
        val slept = lastNight?.let { (it.asleepMs / 60_000L).toInt() }
        val debt = SleepAdvisor.sleepDebtMin(nights, target, now)

        val tips = mutableListOf<String>()
        when {
            slept == null -> tips += TIP_NO_DATA
            slept < target - SHORT_NIGHT_MIN -> tips += "Đêm qua ngủ thiếu ${SleepAdvisor.durationText(target - slept)} so với " +
                "mục tiêu ${SleepAdvisor.durationText(target)}. Tối nay thử lên giường sớm hơn 30 phút."
            else -> tips += "Đêm qua ngủ đủ giấc. Giữ giờ ngủ này nhé!"
        }
        if (debt != null && debt >= SleepAdvisor.DEBT_WARN_MIN) {
            tips += "Nợ ngủ 7 ngày: ${SleepAdvisor.durationText(debt)}. Trả dần bằng vài tối ngủ sớm, đừng ngủ bù một lần quá 2 tiếng."
        }
        tips += TIP_LIGHT

        return MorningSummary(
            nap = false,
            wakeSeconds = wakeMs / 1000L,
            onTime = onTime,
            streakDays = streak,
            sleptMin = slept,
            bedAt = lastNight?.sleepStart,
            targetMin = targetMin,
            debtMin = debt,
            wakeCheckAt = wakeCheckAt,
            tips = tips,
        )
    }

    /** Bản tin sau khi chợp mắt: ngắn gọn, không có số liệu giấc ngủ đêm. */
    fun nap(wakeCheckAt: Long?): MorningSummary =
        MorningSummary(nap = true, wakeCheckAt = wakeCheckAt, tips = listOf(TIP_NAP))
}

/** Nhận xét rút ra từ việc ghép nhật ký giấc ngủ với nhật ký thức dậy. Hàm thuần. */
object Insights {
    const val MIN_NIGHTS_PER_GROUP = 2
    /** Ngủ thiếu dưới mục tiêu không quá chừng này phút vẫn tính là "đủ". */
    const val ENOUGH_TOLERANCE_MIN = 30

    /**
     * Ghép mỗi đêm với lần dậy đầu tiên của buổi sáng đó, chia hai nhóm "ngủ đủ" và "ngủ thiếu",
     * rồi so thời gian để tỉnh. null nếu một trong hai nhóm có ít hơn MIN_NIGHTS_PER_GROUP đêm.
     */
    fun sleepVsWake(
        nights: List<SleepNight>,
        logs: List<WakeLog>,
        targetMin: Int,
        zone: TimeZone = TimeZone.getDefault(),
    ): SleepWakeInsight? {
        val firstByDay = logs
            .filter { it.method != WakeMethod.EMERGENCY }
            .groupBy { SleepAnalyzer.dayStart(it.firedAt, zone) }
            .mapValues { (_, l) -> l.minByOrNull { it.firedAt }!! }
        val pairs = nights.mapNotNull { n ->
            firstByDay[SleepAnalyzer.dayStart(n.wakeAt, zone)]?.let { log ->
                (n.asleepMs / 60_000L).toInt() to (log.awakeAt - log.firedAt).coerceAtLeast(0) / 1000L
            }
        }
        val (enough, short) = pairs.partition { it.first >= targetMin - ENOUGH_TOLERANCE_MIN }
        if (enough.size < MIN_NIGHTS_PER_GROUP || short.size < MIN_NIGHTS_PER_GROUP) return null
        return SleepWakeInsight(
            enoughSec = enough.map { it.second }.average().roundToLong(),
            shortSec = short.map { it.second }.average().roundToLong(),
            enoughNights = enough.size,
            shortNights = short.size,
        )
    }
}
