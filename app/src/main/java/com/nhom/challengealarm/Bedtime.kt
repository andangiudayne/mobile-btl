package com.nhom.challengealarm

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Nhắc đi ngủ: trước giờ lên giường đề xuất 30 phút, gửi một thông báo.
 * Giờ lên giường = giờ chuông kế tiếp - thời lượng ngủ mục tiêu theo tuổi - 15 phút để chìm vào giấc ngủ.
 * Gọi refresh() mỗi khi chuông hoặc hồ sơ thay đổi, khi tắt chuông buổi sáng và sau khi khởi động máy.
 */
object BedtimeReminder {
    const val LEAD_MIN = 30
    private const val REQUEST_CODE = 300_000
    private const val CHANNEL_ID = "bedtime"
    private const val NOTIF_ID = 2001

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, REQUEST_CODE, Intent(context, BedtimeReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    /** Giờ chuông sáng kế tiếp (bỏ qua chuông chợp mắt, đã tính "bỏ qua lần reo tới"). */
    suspend fun nextWake(context: Context, now: Long = System.currentTimeMillis()): Long? =
        AppDatabase.get(context).alarmDao().getAll()
            .also { DaysOffStore.ensureLoaded(context) } // chuông nghỉ lễ thì giờ dậy là lần reo sau kỳ nghỉ
            .filter { it.enabled && !it.nap }
            .mapNotNull { AlarmScheduler.nextTriggerMillis(it, now) }
            .minOrNull()

    /** Thời điểm sẽ nhắc cho kế hoạch này (để hiện trên giao diện). */
    fun remindAt(plan: SleepPlan): Long = plan.bedtime - LEAD_MIN * 60_000L

    suspend fun refresh(context: Context) {
        val app = context.applicationContext
        val am = app.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pendingIntent(app))
        val profile = ProfileStore.load(app) ?: return
        if (!profile.remind) return
        val now = System.currentTimeMillis()
        val wake = nextWake(app, now) ?: return
        val at = remindAt(SleepAdvisor.plan(profile, profile.age(now), wake))
        if (at <= now) return
        // Lời nhắc không cần chính xác đến giây nên dùng báo thức thường, không tốn quyền báo thức chính xác.
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pendingIntent(app))
    }

    fun show(context: Context, plan: SleepPlan, wake: Long) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Nhắc đi ngủ", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        val open = PendingIntent.getActivity(
            context, REQUEST_CODE, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = "Lên giường lúc ${fmt.format(Date(plan.bedtime))} để ngủ đủ " +
            "${SleepAdvisor.durationText(plan.targetMin)} trước chuông ${fmt.format(Date(wake))}. " +
            "Bỏ điện thoại xuống và giảm đèn từ bây giờ nhé."
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Sắp đến giờ ngủ")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(NOTIF_ID, n) }
    }
}

class BedtimeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val profile = ProfileStore.load(context)
                val now = System.currentTimeMillis()
                val wake = BedtimeReminder.nextWake(context, now)
                if (profile != null && profile.remind && wake != null) {
                    val plan = SleepAdvisor.plan(profile, profile.age(now), wake)
                    // Chỉ nhắc khi đúng là sắp tới giờ ngủ (chuông có thể đã bị sửa sau khi đặt lời nhắc).
                    if (plan.bedtime - now in -15 * 60_000L..2 * 3_600_000L) {
                        BedtimeReminder.show(context, plan, wake)
                    }
                }
                BedtimeReminder.refresh(context)
            } finally {
                pending.finish()
            }
        }
    }
}
