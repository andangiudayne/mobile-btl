package com.nhom.challengealarm

import java.util.Calendar

/**
 * "Chợp mắt" (ngủ trưa). Vấn đề: ngủ trưa không đặt chuông dễ quá giấc, dậy uể oải và tối khó ngủ.
 *
 * Cơ sở: ngủ ngắn 10–20 phút giúp tỉnh táo mà không ngái ngủ; quá khoảng 30 phút dễ rơi vào giấc sâu,
 * dậy sẽ uể oải một lúc (quán tính giấc ngủ) và buổi tối khó ngủ hơn; ngủ trưa muộn (sau 15h) cũng làm tối khó ngủ.
 * Nguồn: National Sleep Foundation (Napping), Mayo Clinic (Napping: Do's and don'ts for healthy adults),
 * nghiên cứu của NASA về giấc ngủ ngắn khoảng 26 phút cho phi công (Rosekind 1994).
 *
 * Hàm thuần, test được trên JVM.
 */
object NapPlanner {
    val OPTIONS = listOf(10, 20, 30)
    const val RECOMMENDED = 20
    /** Cộng thêm vài phút để chìm vào giấc, để "20 phút" là 20 phút ngủ thật. */
    const val FALL_ASLEEP_MIN = 5
    /** Ngủ trưa từ giờ này trở đi thì nhắc là tối dễ khó ngủ. */
    const val LATE_HOUR = 15
    /** Chợp mắt xong bao lâu thì hỏi "Bạn còn thức không?" (ngủ trưa cũng rất dễ ngủ tiếp). */
    const val WAKE_CHECK_MIN = 3

    /** Lúc chuông reo: bây giờ + số phút ngủ + thời gian chìm vào giấc, làm tròn lên phút kế tiếp. */
    fun ringAt(minutes: Int, now: Long): Long {
        val raw = now + (minutes + FALL_ASLEEP_MIN) * 60_000L
        return ((raw + 59_999L) / 60_000L) * 60_000L
    }

    fun isLate(now: Long): Boolean =
        Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.HOUR_OF_DAY) >= LATE_HOUR

    fun label(minutes: Int): String = "Chợp mắt $minutes phút"

    /** Nhận xét ngắn cho từng lựa chọn. */
    fun advice(minutes: Int): String = when {
        minutes <= 10 -> "Ngắn, dậy là tỉnh ngay. Hợp khi chỉ có ít thời gian."
        minutes <= 20 -> "Khuyên dùng: đủ để hồi sức, dậy không bị ngái ngủ."
        else -> "Dài hơn dễ vào giấc sâu, dậy có thể uể oải 15 đến 30 phút."
    }

    /**
     * Chuông chợp mắt: reo một lần, giải 2 bài toán dễ, không hoãn, 1 màn, hỏi lại sau 3 phút.
     * nap = true để reo xong tự xóa và không tính vào thống kê dậy sáng hay giấc ngủ đêm.
     */
    fun alarmFor(minutes: Int, now: Long): AlarmEntity {
        val c = Calendar.getInstance().apply { timeInMillis = ringAt(minutes, now) }
        return AlarmEntity(
            hour = c.get(Calendar.HOUR_OF_DAY),
            minute = c.get(Calendar.MINUTE),
            daysMask = 0,
            enabled = true,
            label = label(minutes),
            mathDifficulty = 1,
            mathCount = 2,
            challengeIds = "",
            snoozeLimit = 0,
            ringtoneUri = "",
            rounds = 1,
            breakMinutes = 0,
            skipUntil = 0L,
            wakeCheckMin = WAKE_CHECK_MIN,
            nap = true,
        )
    }
}
