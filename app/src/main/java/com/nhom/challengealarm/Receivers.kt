package com.nhom.challengealarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Nhận tín hiệu từ AlarmManager lúc đến giờ: bật service reo chuông và đặt lại lần kế tiếp. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(AlarmScheduler.EXTRA_ALARM_ID, -1)
        if (id < 0) return
        val isSnooze = intent.hasExtra(AlarmScheduler.EXTRA_SNOOZE_COUNT)
        val snoozeCount = intent.getIntExtra(AlarmScheduler.EXTRA_SNOOZE_COUNT, 0)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDatabase.get(context).alarmDao()
                val alarm = dao.getById(id)
                // Chuông hoãn vẫn reo kể cả khi chuông một lần đã tự tắt sau lần reo đầu.
                if (alarm != null && (alarm.enabled || isSnooze)) {
                    // Phòng xa: lịch đặt từ trước khi thêm kỳ nghỉ mà hôm nay lại là ngày nghỉ thì không reo,
                    // chỉ đặt lần reo kế tiếp (chuông hoãn và chuông dự phòng thì luôn reo).
                    DaysOffStore.ensureLoaded(context)
                    val dayOff = !isSnooze && alarm.skipHolidays && alarm.daysMask != 0 &&
                        DaysOff.isOff(Ymd.of(System.currentTimeMillis()))
                    if (!dayOff) {
                        val svc = Intent(context, AlarmService::class.java)
                            .setAction(AlarmService.ACTION_START)
                            .putExtra(AlarmScheduler.EXTRA_ALARM_ID, id)
                            .putExtra(AlarmScheduler.EXTRA_SNOOZE_COUNT, snoozeCount)
                            .putExtra(AlarmScheduler.EXTRA_RINGTONE, alarm.ringtoneUri)
                        ContextCompat.startForegroundService(context, svc)
                    }

                    if (!isSnooze) {
                        if (alarm.daysMask == 0) {
                            dao.setEnabled(id, false) // báo một lần: tự tắt
                        } else {
                            AlarmScheduler.schedule(context, alarm) // lặp: đặt lần sau
                        }
                    }
                    NextAlarmWidget.refresh(context)
                }
            } finally {
                pending.finish()
            }
        }
    }
}

/**
 * Sau khi khởi động lại máy / đổi giờ / đổi múi giờ: đặt lại toàn bộ báo thức.
 * Nếu lúc đó còn một chuông đang reo hoặc đang hoãn chưa tắt hẳn (tắt nguồn hay chỉnh giờ để trốn chuông),
 * cho chuông đó reo lại ngay sau 2 giây và làm tiếp từ màn đang dở.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Mỗi chuông chưa tắt hẳn reo lại sau 2 giây (cách nhau 1 phút nếu có nhiều chuông).
                RingSession.active(context).forEachIndexed { i, session ->
                    val alarm = AppDatabase.get(context).alarmDao().getById(session.alarmId)
                    if (alarm != null) {
                        AlarmScheduler.scheduleReRing(
                            context, alarm.id, session.snoozeCount, alarm.ringtoneUri, 2_000L + i * 60_000L,
                        )
                    }
                }
                AlarmScheduler.rescheduleAll(context)
                runCatching { BedtimeReminder.refresh(context) }
                // Kiểm tra đã tỉnh đang chờ (tắt nguồn để né câu hỏi "Bạn còn thức không?" cũng không thoát được).
                runCatching { WakeCheck.restore(context) }
            } finally {
                pending.finish()
            }
        }
    }
}
