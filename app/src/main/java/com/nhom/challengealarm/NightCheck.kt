package com.nhom.challengealarm

import java.util.Calendar

enum class CheckLevel { OK, WARN, BAD }

/** Một mục trong "Kiểm tra trước khi ngủ". fixLabel = chữ trên nút sửa (null = không có cách sửa trong app). */
data class NightCheckItem(
    val id: String,
    val title: String,
    val detail: String,
    val level: CheckLevel,
    val fixLabel: String? = null,
)

/** Trạng thái máy lúc bấm "Bắt đầu ngủ" (đọc ở DeviceStatus, tách riêng để logic test được trên JVM). */
data class NightInputs(
    val now: Long,
    val nextWake: Long?,
    val batteryPct: Int?,
    val charging: Boolean,
    val alarmsBlockedByDnd: Boolean,
    val exactAllowed: Boolean,
    val notificationsAllowed: Boolean,
    val sleepTrackingSupported: Boolean,
    val usageAccess: Boolean,
)

/**
 * "Kiểm tra trước khi ngủ". Vấn đề: chuông không reo vì máy hết pin qua đêm, Không làm phiền chặn cả báo thức,
 * quên bật chuông, hoặc thiếu quyền; và lo chuông không reo cũng làm khó ngủ. Bấm "Bắt đầu ngủ" là app kiểm tra
 * những nguyên nhân hay gặp và chỉ cách sửa ngay. Hàm thuần.
 */
object NightCheck {
    const val BATTERY_BAD = 20
    const val BATTERY_WARN = 35
    /** Chuông kế tiếp xa hơn chừng này thì không phải chuông sáng mai. */
    const val FAR_WAKE_HOURS = 16

    const val ID_ALARM = "alarm"
    const val ID_BATTERY = "battery"
    const val ID_DND = "dnd"
    const val ID_EXACT = "exact"
    const val ID_NOTIF = "notif"
    const val ID_USAGE = "usage"

    private fun clock(t: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = t }
        return "%02d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
    }

    private fun weekday(t: Long): String {
        val dow = Calendar.getInstance().apply { timeInMillis = t }.get(Calendar.DAY_OF_WEEK)
        return DAY_FULL[(dow + 5) % 7]
    }

    fun evaluate(i: NightInputs): List<NightCheckItem> {
        val out = mutableListOf<NightCheckItem>()

        val wake = i.nextWake
        out += when {
            wake == null -> NightCheckItem(
                ID_ALARM, "Chưa có chuông nào đang bật", "Sáng mai sẽ không có chuông đánh thức bạn.",
                CheckLevel.BAD, "Thêm chuông",
            )
            wake - i.now > FAR_WAKE_HOURS * 3_600_000L -> NightCheckItem(
                ID_ALARM, "Sáng mai không có chuông",
                "Chuông kế tiếp là ${weekday(wake).lowercase()} lúc ${clock(wake)}. Nếu mai cần dậy, hãy thêm chuông.",
                CheckLevel.WARN, "Thêm chuông",
            )
            else -> NightCheckItem(
                ID_ALARM, "Chuông ${clock(wake)}",
                "Còn ${SleepAdvisor.durationText(((wake - i.now) / 60_000L).toInt().coerceAtLeast(0))} nữa.",
                CheckLevel.OK,
            )
        }

        val pct = i.batteryPct
        if (pct != null) {
            out += when {
                i.charging -> NightCheckItem(ID_BATTERY, "Đang sạc", "Pin $pct%.", CheckLevel.OK)
                pct < BATTERY_BAD -> NightCheckItem(
                    ID_BATTERY, "Pin còn $pct%",
                    "Máy có thể hết pin và tắt nguồn trước giờ chuông. Cắm sạc qua đêm nhé.", CheckLevel.BAD,
                )
                pct < BATTERY_WARN -> NightCheckItem(
                    ID_BATTERY, "Pin còn $pct%", "Nên cắm sạc qua đêm cho chắc.", CheckLevel.WARN,
                )
                else -> NightCheckItem(ID_BATTERY, "Pin $pct%", "Đủ cho cả đêm.", CheckLevel.OK)
            }
        }

        out += if (i.alarmsBlockedByDnd) {
            NightCheckItem(
                ID_DND, "Không làm phiền đang chặn báo thức",
                "Chế độ Không làm phiền hiện tại tắt cả tiếng báo thức. Cho phép báo thức trong cài đặt.",
                CheckLevel.BAD, "Mở cài đặt",
            )
        } else {
            NightCheckItem(ID_DND, "Không làm phiền không chặn chuông", "Báo thức vẫn reo bình thường.", CheckLevel.OK)
        }

        if (!i.exactAllowed) {
            out += NightCheckItem(
                ID_EXACT, "Chưa có quyền báo thức chính xác",
                "Hệ thống có thể dời chuông trễ vài phút khi máy ngủ sâu.", CheckLevel.BAD, "Cấp quyền",
            )
        }
        if (!i.notificationsAllowed) {
            out += NightCheckItem(
                ID_NOTIF, "Chưa bật thông báo",
                "Chuông vẫn reo, nhưng app không hỏi lại được \"Bạn còn thức không?\" sau khi bạn tắt chuông.",
                CheckLevel.WARN, "Bật",
            )
        }
        if (i.sleepTrackingSupported && !i.usageAccess) {
            out += NightCheckItem(
                ID_USAGE, "Chưa bật đo giấc ngủ",
                "Sáng mai sẽ không có số liệu đêm nay. Cần quyền \"Truy cập dữ liệu sử dụng\".",
                CheckLevel.WARN, "Bật",
            )
        }
        return out
    }

    /** Không còn mục nào cần xử lý. */
    fun ready(items: List<NightCheckItem>): Boolean = items.all { it.level == CheckLevel.OK }

    /** Mục cần xử lý, mục nặng trước. */
    fun problems(items: List<NightCheckItem>): List<NightCheckItem> =
        items.filter { it.level != CheckLevel.OK }.sortedByDescending { it.level.ordinal }
}
