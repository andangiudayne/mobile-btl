package com.nhom.challengealarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

object AlarmScheduler {

    const val EXTRA_ALARM_ID = "alarm_id"
    const val EXTRA_SNOOZE_COUNT = "snooze_count"
    const val EXTRA_RINGTONE = "ringtone_uri"

    const val SNOOZE_MINUTES = 5
    private const val SNOOZE_REQUEST_BASE = 100_000
    private const val RERING_REQUEST_BASE = 200_000
    const val RERING_DELAY_MS = 10_000L

    /** Từ Android 12, cần người dùng cấp quyền "Báo thức & lời nhắc" thì mới đặt được báo thức chính xác. */
    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return am.canScheduleExactAlarms()
    }

    /** Quy tắc ngày nghỉ của chuông: chỉ chuông lặp có bật "Không reo ngày nghỉ". */
    private fun dayOffRule(alarm: AlarmEntity): ((Ymd) -> Boolean)? {
        if (!alarm.skipHolidays || alarm.daysMask == 0) return null
        return { day -> DaysOff.isOff(day) }
    }

    /** Tính thời điểm báo kế tiếp (millis), hoặc null nếu không có. Đã tính "Bỏ qua lần reo tới" và ngày nghỉ. */
    fun nextTriggerMillis(alarm: AlarmEntity, now: Long = System.currentTimeMillis()): Long? =
        nextTriggerAt(alarm.hour, alarm.minute, alarm.daysMask, alarm.skipUntil, now, dayOffRule(alarm))

    /** Lần reo sẽ bị bỏ qua nếu bấm "Bỏ qua lần reo tới" bây giờ (lần reo kế tiếp khi chưa bỏ qua gì). */
    fun upcomingIgnoringSkip(alarm: AlarmEntity, now: Long = System.currentTimeMillis()): Long? =
        nextTriggerAt(alarm.hour, alarm.minute, alarm.daysMask, 0L, now, dayOffRule(alarm))

    /** Lần reo kế tiếp nếu không có ngày nghỉ (để báo "Nghỉ lễ ..., reo lại ..." trên thẻ chuông). */
    fun nextIgnoringDaysOff(alarm: AlarmEntity, now: Long = System.currentTimeMillis()): Long? =
        nextTriggerAt(alarm.hour, alarm.minute, alarm.daysMask, alarm.skipUntil, now)

    private fun pendingIntent(context: Context, requestCode: Int, intent: Intent): PendingIntent =
        PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun mainPendingIntent(context: Context, alarm: AlarmEntity): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .putExtra(EXTRA_ALARM_ID, alarm.id)
            .putExtra(EXTRA_RINGTONE, alarm.ringtoneUri)
        return pendingIntent(context, alarm.id, intent)
    }

    private fun setAt(context: Context, triggerAt: Long, pi: PendingIntent) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val showIntent = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        if (canScheduleExact(context)) {
            // setAlarmClock: hệ thống coi là báo thức thật, vẫn reo khi Doze.
            am.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAt, showIntent), pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        }
    }

    fun schedule(context: Context, alarm: AlarmEntity) {
        DaysOffStore.ensureLoaded(context) // receiver chạy trong tiến trình mới: nạp kỳ nghỉ trước khi tính giờ
        if (!alarm.enabled) {
            // Chỉ hủy lần reo theo lịch. Chuông hoãn và chuông dự phòng đang chờ thì giữ nguyên,
            // để không thể "hoãn rồi tắt chuông" (hoặc đổi giờ máy) mà thoát được thử thách.
            cancelMain(context, alarm.id)
            return
        }
        val trigger = nextTriggerMillis(alarm) ?: return
        setAt(context, trigger, mainPendingIntent(context, alarm))
    }

    /** Hoãn chuông: reo lại sau SNOOZE_MINUTES phút. snoozeCount = số lần đã hoãn tính cả lần này. */
    fun scheduleSnooze(context: Context, alarm: AlarmEntity, snoozeCount: Int) {
        val intent = Intent(context, AlarmReceiver::class.java)
            .putExtra(EXTRA_ALARM_ID, alarm.id)
            .putExtra(EXTRA_RINGTONE, alarm.ringtoneUri)
            .putExtra(EXTRA_SNOOZE_COUNT, snoozeCount)
        val trigger = System.currentTimeMillis() + SNOOZE_MINUTES * 60_000L
        setAt(context, trigger, pendingIntent(context, SNOOZE_REQUEST_BASE + alarm.id, intent))
    }

    /**
     * Chuông dự phòng: trong lúc đang reo, luôn có sẵn một báo thức hệ thống sắp nổ sau vài giây.
     * Nếu app bị vuốt khỏi màn hình đa nhiệm (hệ thống giết tiến trình), báo thức này vẫn còn
     * và sẽ dựng lại chuông cùng màn hình thử thách. Khi xong thử thách thì hủy nó đi.
     */
    fun scheduleReRing(
        context: Context,
        alarmId: Int,
        snoozeCount: Int,
        ringtone: String,
        delayMs: Long = RERING_DELAY_MS,
    ) {
        if (alarmId < 0) return
        val intent = Intent(context, AlarmReceiver::class.java)
            .putExtra(EXTRA_ALARM_ID, alarmId)
            .putExtra(EXTRA_RINGTONE, ringtone)
            .putExtra(EXTRA_SNOOZE_COUNT, snoozeCount)
        val pi = pendingIntent(context, RERING_REQUEST_BASE + alarmId, intent)
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val at = System.currentTimeMillis() + delayMs
        if (canScheduleExact(context)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun cancelReRing(context: Context, alarmId: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(context, RERING_REQUEST_BASE + alarmId, Intent(context, AlarmReceiver::class.java)))
    }

    private fun cancelMain(context: Context, alarmId: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(context, alarmId, Intent(context, AlarmReceiver::class.java)))
    }

    /** Hủy hết mọi lần reo của chuông (chỉ dùng khi xóa chuông, mà xóa thì bị chặn lúc chuông đang reo/hoãn). */
    fun cancel(context: Context, alarmId: Int) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val base = Intent(context, AlarmReceiver::class.java)
        am.cancel(pendingIntent(context, alarmId, base))
        am.cancel(pendingIntent(context, SNOOZE_REQUEST_BASE + alarmId, base))
        am.cancel(pendingIntent(context, RERING_REQUEST_BASE + alarmId, base))
    }

    suspend fun rescheduleAll(context: Context) {
        AppDatabase.get(context).alarmDao().getAll().forEach { schedule(context, it) }
        NextAlarmWidget.refresh(context)
    }
}
