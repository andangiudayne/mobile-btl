package com.nhom.challengealarm

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Widget màn hình chính: chuông kế tiếp, chuỗi ngày dậy đúng giờ, ngày nghỉ sắp tới và nút "Chợp mắt 20 phút".
 * Người dùng ít khi mở app nên hay quên chuông đang tắt hay bật; nhìn màn hình chính là biết.
 * Cập nhật khi chuông thay đổi, khi chuông reo xong, khi mở app và tự làm mới mỗi 30 phút.
 */
class NextAlarmWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val views = render(context.applicationContext)
                ids.forEach { manager.updateAppWidget(it, views) }
            } catch (_: Exception) {
                // Không đọc được dữ liệu thì giữ nội dung cũ, lần cập nhật sau sẽ thử lại.
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val REQ_OPEN = 600_000
        private const val REQ_NAP = 600_001

        /** Gọi sau mọi thay đổi chuông/nhật ký. Không có widget nào trên màn hình thì không làm gì. */
        fun refresh(context: Context) {
            runCatching {
                val app = context.applicationContext
                val manager = AppWidgetManager.getInstance(app) ?: return
                val ids = manager.getAppWidgetIds(ComponentName(app, NextAlarmWidget::class.java))
                if (ids.isEmpty()) return
                app.sendBroadcast(
                    Intent(app, NextAlarmWidget::class.java)
                        .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                )
            }
        }

        private suspend fun render(context: Context): RemoteViews {
            DaysOffStore.ensureLoaded(context)
            val now = System.currentTimeMillis()
            val db = AppDatabase.get(context)
            val info = WidgetContent.build(
                alarms = db.alarmDao().getAll(),
                logs = db.wakeLogDao().getSince(now - 60L * 24 * 3_600_000L),
                now = now,
            )
            val views = RemoteViews(context.packageName, R.layout.widget_next_alarm)
            views.setTextViewText(R.id.widget_time, info.time)
            views.setTextViewText(R.id.widget_detail, info.detail)
            views.setTextViewText(R.id.widget_streak, info.streak)
            views.setTextViewText(R.id.widget_day_off, info.dayOff.orEmpty())
            views.setViewVisibility(R.id.widget_day_off, if (info.dayOff == null) android.view.View.GONE else android.view.View.VISIBLE)

            val open = PendingIntent.getActivity(
                context, REQ_OPEN,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)
            val nap = PendingIntent.getBroadcast(
                context, REQ_NAP,
                Intent(context, WidgetActionReceiver::class.java).setAction(AppShortcuts.NAP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_nap, nap)
            return views
        }
    }
}

/** Nút "Chợp mắt" trên widget: tạo chuông chợp mắt ngay, không cần mở app. Không cho app khác gọi (exported=false). */
class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AppShortcuts.NAP) return
        val app = context.applicationContext
        val at = NapPlanner.ringAt(AppShortcuts.NAP_MINUTES, System.currentTimeMillis())
        val c = java.util.Calendar.getInstance().apply { timeInMillis = at }
        Toast.makeText(
            app,
            "Chợp mắt ngon nhé! Chuông reo lúc %02d:%02d.".format(c.get(java.util.Calendar.HOUR_OF_DAY), c.get(java.util.Calendar.MINUTE)),
            Toast.LENGTH_LONG,
        ).show()
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AppGraph.alarms(app).createNap(AppShortcuts.NAP_MINUTES)
            } finally {
                pending.finish()
            }
        }
    }
}
