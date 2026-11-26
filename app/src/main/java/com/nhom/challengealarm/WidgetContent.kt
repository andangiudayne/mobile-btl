package com.nhom.challengealarm

import java.util.Calendar

/** Nội dung widget, tính sẵn (hàm thuần trong WidgetContent) rồi mới đổ vào RemoteViews. */
data class WidgetInfo(
    val time: String,
    val detail: String,
    val streak: String,
    val dayOff: String?,
)

/** Tính chữ hiện trên widget từ danh sách chuông và nhật ký. Không đụng Android nên test được. */
object WidgetContent {
    fun build(alarms: List<AlarmEntity>, logs: List<WakeLog>, now: Long): WidgetInfo {
        // Chuông bật sớm nhất (giống AlarmViewModel.nextOf, viết lại ở đây để hàm này thuần, test được).
        val next = alarms.asSequence()
            .filter { it.enabled }
            .mapNotNull { a -> AlarmScheduler.nextTriggerMillis(a, now)?.let { a to it } }
            .minByOrNull { it.second }
        val stats = StatsCalculator.compute(logs, now)
        val today = Ymd.of(now)
        // Ngày nghỉ trong 7 ngày tới mà có chuông đang tự nghỉ: báo luôn để người dùng yên tâm.
        val skipping = alarms.any { it.enabled && it.skipHolidays && it.daysMask != 0 }
        val dayOff = if (skipping) DaysOff.next(today, days = 7)?.let { (d, name) -> "Nghỉ ${d.weekdayShort} ${d.shortText}: $name" } else null
        return WidgetInfo(
            time = next?.let { hhmmOf(it.second) } ?: "--:--",
            detail = next?.let { (alarm, at) ->
                val day = Ymd.of(at)
                val whenText = when (daysBetween(today, day)) {
                    0 -> "Hôm nay"
                    1 -> "Ngày mai"
                    else -> "${day.weekdayShort} ${day.shortText}"
                }
                whenText + alarm.label.trim().takeIf { l -> l.isNotEmpty() }?.let { l -> " · $l" }.orEmpty()
            } ?: "Chưa có chuông nào đang bật",
            streak = if (stats.streakDays > 0) "${stats.streakDays} ngày dậy đúng giờ liên tiếp" else "Bắt đầu chuỗi ngày dậy đúng giờ",
            dayOff = dayOff,
        )
    }

    private fun hhmmOf(t: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = t }
        return "%02d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
    }
}
