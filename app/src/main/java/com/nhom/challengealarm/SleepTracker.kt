package com.nhom.challengealarm

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Đọc lịch sử bật/tắt màn hình của Android (UsageStatsManager, có từ Android 9) để tính giờ đi ngủ.
 * Không cần chạy nền cả đêm: lúc tắt chuông buổi sáng, app đọc lại lịch sử đêm qua rồi tính một lần.
 * Cần người dùng cấp quyền "Truy cập dữ liệu sử dụng". Dữ liệu chỉ nằm trên máy.
 */
object SleepTracker {
    private const val LOOKBACK_DAYS = 5
    private const val PRE_WINDOW_MS = 12 * 3_600_000L

    fun supported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    fun hasUsageAccess(context: Context): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Mở trang cấp quyền; máy nào hỗ trợ thì nhảy thẳng tới app này. */
    fun openUsageAccess(context: Context) {
        val direct = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val ok = runCatching { context.startActivity(direct) }.isSuccess
        if (!ok) {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }

    fun screenEvents(context: Context, from: Long, to: Long): List<ScreenEvent> {
        if (!supported()) return emptyList()
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val events = usm.queryEvents(from, to) ?: return emptyList()
        val e = UsageEvents.Event()
        val out = mutableListOf<ScreenEvent>()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            when (e.eventType) {
                UsageEvents.Event.SCREEN_INTERACTIVE -> out += ScreenEvent(e.timeStamp, true)
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> out += ScreenEvent(e.timeStamp, false)
            }
        }
        return out
    }

    /**
     * Tính các đêm còn thiếu trong vài ngày gần đây: mỗi buổi sáng có chuông thật thì lấy lần reo sớm nhất
     * làm giờ thức dậy. Đêm đã có thì bỏ qua, nên gọi bao nhiêu lần cũng được.
     */
    suspend fun backfill(context: Context) = withContext(Dispatchers.IO) {
        if (!supported() || !hasUsageAccess(context)) return@withContext
        val app = context.applicationContext
        val db = AppDatabase.get(app)
        val now = System.currentTimeMillis()
        val since = SleepAnalyzer.dayStart(now - LOOKBACK_DAYS * 24 * 3_600_000L)
        val nightStart = ProfileStore.load(app)?.nightStartMin ?: AgeGroup.ADULT.defaultNightStart

        val done = db.sleepLogDao().nightsSince(since).toSet()
        val firstRingByMorning = db.wakeLogDao().getSince(since)
            .filter { it.alarmId >= 0 }
            .groupBy { SleepAnalyzer.dayStart(it.firedAt) }
            .mapValues { (_, logs) -> logs.minOf { it.firedAt } }

        for ((night, wakeAt) in firstRingByMorning) {
            if (night in done) continue
            val start = SleepAnalyzer.windowStart(wakeAt, nightStart) ?: continue
            val events = runCatching { screenEvents(app, start - PRE_WINDOW_MS, wakeAt) }.getOrDefault(emptyList())
            val result = SleepAnalyzer.analyze(events, start, wakeAt) ?: continue
            db.sleepLogDao().insert(
                SleepLog(
                    nightOf = night,
                    sleepStart = result.sleepStart,
                    wakeAt = result.wakeAt,
                    asleepMin = (result.asleepMs / 60_000L).toInt(),
                    awakeMin = (result.awakeMs / 60_000L).toInt(),
                    interruptions = result.interruptions,
                )
            )
        }
    }
}
