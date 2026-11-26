package com.nhom.challengealarm

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * "Kiểm tra đã tỉnh" (phần chạy trên Android; quy tắc nằm ở WakeCheckRules).
 *
 *  tắt chuông ──(N phút)──> hỏi "Bạn còn thức không?" ──(90 giây)──> không trả lời: chuông reo lại 1 màn,
 *                                       │                                  nhật ký sáng nay bị đánh dấu "ngủ lại"
 *                                       └── bấm "Tôi đã dậy" (trên thông báo hoặc trong app): xong
 *
 * Trạng thái lưu trong SharedPreferences để sống qua việc app bị giết hoặc khởi động lại máy (BootReceiver gọi restore).
 */
object WakeCheck {
    private const val PREFS = "wake_check"
    const val ACTION_ASK = "com.nhom.challengealarm.WAKE_CHECK_ASK"
    const val ACTION_TIMEOUT = "com.nhom.challengealarm.WAKE_CHECK_TIMEOUT"
    const val ACTION_CONFIRM = "com.nhom.challengealarm.WAKE_CHECK_CONFIRM"
    /** Có trong Intent mở MainActivity từ thông báo: chạm vào thông báo cũng là trả lời "Tôi đã dậy". */
    const val EXTRA_CONFIRM = "wake_check_confirm"
    private const val REQ_ASK = 400_000
    private const val REQ_TIMEOUT = 400_001
    private const val REQ_CONFIRM = 400_002
    private const val REQ_OPEN = 400_003
    private const val CHANNEL_ID = "wake_check"
    private const val NOTIF_ID = 3001

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(context: Context): WakeCheckState? {
        val p = prefs(context)
        if (!p.contains("askAt")) return null
        return WakeCheckState(
            alarmId = p.getInt("alarmId", -1),
            logId = p.getLong("logId", -1L),
            askAt = p.getLong("askAt", 0L),
            deadline = p.getLong("deadline", 0L),
        )
    }

    private fun save(context: Context, s: WakeCheckState) {
        prefs(context).edit()
            .putInt("alarmId", s.alarmId)
            .putLong("logId", s.logId)
            .putLong("askAt", s.askAt)
            .putLong("deadline", s.deadline)
            .commit() // ghi ngay: tiến trình có thể bị giết bất cứ lúc nào
    }

    private fun clear(context: Context) {
        prefs(context).edit().clear().commit()
    }

    /** Có hiện được thông báo không (không hiện được thì không hỏi được, nên không hẹn kiểm tra). */
    fun notificationsAllowed(context: Context): Boolean {
        val permission = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return permission && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun broadcast(context: Context, action: String, request: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, request,
            Intent(context, WakeCheckReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun setAt(context: Context, at: Long, pi: PendingIntent) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (AlarmScheduler.canScheduleExact(context)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun cancelAlarms(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(broadcast(context, ACTION_ASK, REQ_ASK))
        am.cancel(broadcast(context, ACTION_TIMEOUT, REQ_TIMEOUT))
    }

    private fun cancelNotification(context: Context) {
        runCatching { context.getSystemService(NotificationManager::class.java).cancel(NOTIF_ID) }
    }

    /** Hẹn hỏi sau minutes phút kể từ now. Kiểm tra cũ (nếu có) bị thay. Trả về lúc sẽ hỏi. */
    fun schedule(context: Context, alarmId: Int, logId: Long, minutes: Int, now: Long = System.currentTimeMillis()): Long {
        val app = context.applicationContext
        val askAt = now + minutes * 60_000L
        cancelAlarms(app)
        cancelNotification(app)
        save(app, WakeCheckState(alarmId, logId, askAt, 0L))
        setAt(app, askAt, broadcast(app, ACTION_ASK, REQ_ASK))
        return askAt
    }

    /** Đến giờ: hiện "Bạn còn thức không?" và hẹn hạn trả lời. */
    fun ask(context: Context, now: Long = System.currentTimeMillis()) {
        val app = context.applicationContext
        val s = load(app) ?: return
        val deadline = now + WakeCheckRules.ANSWER_WINDOW_MS
        save(app, s.copy(deadline = deadline))
        setAt(app, deadline, broadcast(app, ACTION_TIMEOUT, REQ_TIMEOUT))
        showQuestion(app)
    }

    /**
     * Người dùng trả lời "Tôi đã dậy" (nút trên thông báo, chạm thông báo, hoặc thẻ trong app).
     * Chỉ có tác dụng khi app đã hỏi (WakeCheckRules.canConfirm). Trả về true nếu đã xác nhận.
     */
    fun confirm(context: Context): Boolean {
        val app = context.applicationContext
        if (!WakeCheckRules.canConfirm(load(app))) return false
        cancelAlarms(app)
        clear(app)
        cancelNotification(app)
        return true
    }

    /** Hết hạn mà không trả lời: coi là đã ngủ lại. Đánh dấu nhật ký và cho chuông reo lại một màn. */
    suspend fun timeout(context: Context) {
        val app = context.applicationContext
        val s = load(app) ?: return
        cancelAlarms(app)
        clear(app)
        cancelNotification(app)
        val repo = AppGraph.alarms(app)
        runCatching { repo.markRelapsed(s.logId) }
        if (s.alarmId < 0) return
        // Phiên reo lại: RingViewModel đọc cờ recheck để chỉ bắt làm 1 màn, không hoãn, không ghi nhật ký mới.
        val now = System.currentTimeMillis()
        RingSession.save(app, RingSessionState(s.alarmId, now, 0, 0L, 0L, 0, recheck = true))
        val ringtone = runCatching { repo.alarm(s.alarmId)?.ringtoneUri }.getOrNull().orEmpty()
        val svc = Intent(app, AlarmService::class.java)
            .setAction(AlarmService.ACTION_START)
            .putExtra(AlarmScheduler.EXTRA_ALARM_ID, s.alarmId)
            .putExtra(AlarmScheduler.EXTRA_SNOOZE_COUNT, 0)
            .putExtra(AlarmScheduler.EXTRA_RINGTONE, ringtone)
        runCatching { ContextCompat.startForegroundService(app, svc) }
            .onFailure { AlarmScheduler.scheduleReRing(app, s.alarmId, 0, ringtone, 2_000L) }
    }

    /** Sau khởi động lại máy hoặc đổi giờ: đặt lại hẹn giờ cho kiểm tra đang chờ. */
    suspend fun restore(context: Context, now: Long = System.currentTimeMillis()) {
        val app = context.applicationContext
        val s = load(app) ?: return
        when (WakeCheckRules.stepAt(s, now)) {
            WakeCheckRules.Step.WAIT -> setAt(app, s.askAt, broadcast(app, ACTION_ASK, REQ_ASK))
            WakeCheckRules.Step.ASK -> ask(app, now)
            WakeCheckRules.Step.TIMEOUT -> timeout(app)
            WakeCheckRules.Step.DROP -> {
                cancelAlarms(app)
                clear(app)
                cancelNotification(app)
            }
        }
    }

    private fun showQuestion(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Kiểm tra đã tỉnh", NotificationManager.IMPORTANCE_HIGH)
            channel.description = "Hỏi lại vài phút sau khi tắt chuông để chắc bạn không ngủ tiếp"
            nm.createNotificationChannel(channel)
        }
        val open = PendingIntent.getActivity(
            context, REQ_OPEN,
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_CONFIRM, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val seconds = WakeCheckRules.ANSWER_WINDOW_MS / 1000L
        val text = "Bấm \"Tôi đã dậy\" trong $seconds giây, nếu không chuông sẽ reo lại."
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Bạn còn thức không?")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, "Tôi đã dậy", broadcast(context, ACTION_CONFIRM, REQ_CONFIRM))
            .build()
        runCatching { nm.notify(NOTIF_ID, n) }
    }
}

/** Nhận ba tín hiệu của kiểm tra đã tỉnh: đến giờ hỏi, người dùng bấm "Tôi đã dậy", hết hạn trả lời. */
class WakeCheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            WakeCheck.ACTION_ASK -> WakeCheck.ask(context)
            WakeCheck.ACTION_CONFIRM -> WakeCheck.confirm(context)
            WakeCheck.ACTION_TIMEOUT -> {
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        WakeCheck.timeout(context)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }
}
