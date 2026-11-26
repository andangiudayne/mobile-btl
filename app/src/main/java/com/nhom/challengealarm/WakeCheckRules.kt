package com.nhom.challengealarm

/**
 * Một lần "Kiểm tra đã tỉnh" đang chờ.
 * askAt = lúc sẽ hỏi "Bạn còn thức không?"; deadline = hạn trả lời (0 khi chưa hỏi).
 * logId = dòng nhật ký thức dậy sẽ bị đánh dấu "ngủ lại" nếu không trả lời (-1 nếu không có, ví dụ chợp mắt).
 */
data class WakeCheckState(val alarmId: Int, val logId: Long, val askAt: Long, val deadline: Long) {
    val asking: Boolean get() = deadline > 0L
}

/**
 * Quy tắc của "Kiểm tra đã tỉnh". Vấn đề: giải xong thử thách khi còn ngái ngủ rồi nằm xuống ngủ tiếp,
 * lỗi phổ biến nhất của báo thức thử thách. Vài phút sau khi tắt chuông app hỏi lại; không trả lời trong
 * ANSWER_WINDOW_MS thì chuông reo lại (1 màn) và lần dậy đó bị tính là "ngủ lại".
 * Hàm thuần, test được trên JVM.
 */
object WakeCheckRules {
    /** Các lựa chọn trong trình sửa chuông (phút sau khi tắt chuông), 0 = không hỏi. */
    val OPTIONS = listOf(0, 3, 5, 10)
    /** Thời gian để bấm "Tôi đã dậy". */
    const val ANSWER_WINDOW_MS = 90_000L
    /** Kiểm tra trễ quá lâu (máy tắt nguồn cả tiếng) thì bỏ, không reo bất ngờ giữa buổi sáng. */
    const val MAX_LATE_MS = 60 * 60_000L

    enum class Step { WAIT, ASK, TIMEOUT, DROP }

    /** Có hẹn kiểm tra sau lần tắt chuông này không. Thoát khẩn cấp (có việc gấp) và chưa bật thông báo thì không. */
    fun shouldCheck(method: String, minutes: Int, notificationsAllowed: Boolean): Boolean =
        minutes > 0 && notificationsAllowed && method != WakeMethod.EMERGENCY

    /**
     * Việc cần làm với kiểm tra s ở thời điểm now (dùng sau khi khởi động lại máy hoặc đổi giờ):
     * WAIT = chưa đến giờ hỏi, ASK = hỏi ngay (hoặc hỏi lại), TIMEOUT = đã hết hạn trả lời, DROP = quá cũ, bỏ.
     */
    fun stepAt(s: WakeCheckState, now: Long): Step = when {
        now - s.askAt > MAX_LATE_MS -> Step.DROP
        !s.asking -> if (now < s.askAt) Step.WAIT else Step.ASK
        now < s.deadline -> Step.ASK
        else -> Step.TIMEOUT
    }

    /**
     * Chỉ chấp nhận "Tôi đã dậy" khi đã hỏi, để không thể bấm trước rồi ngủ tiếp. Đã hỏi rồi thì bấm muộn vài giây
     * (báo thức hết hạn bị hệ thống dời) vẫn tính, vì còn bấm được nghĩa là đang thức.
     */
    fun canConfirm(s: WakeCheckState?): Boolean = s != null && s.asking
}
