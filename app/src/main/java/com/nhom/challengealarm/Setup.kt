package com.nhom.challengealarm

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

/** Một bước thiết lập. manual = hệ điều hành không cho app tự biết đã bật chưa, người dùng tự bấm "Đã xong". */
data class SetupItem(
    val id: String,
    val title: String,
    val why: String,
    val done: Boolean,
    val manual: Boolean,
    val canOpen: Boolean,
)

/** Các bước thiết lập để chuông reo chắc và không thoát ra được. Mở app lần đầu sẽ tự hiện. */
object Setup {
    private const val PREFS = "setup"
    private const val KEY_AUTO_OPENED = "auto_opened"
    private const val TEST_ALARM_ID = -2

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Chỉ trả true đúng một lần: lần đầu mở app. */
    fun consumeFirstRun(context: Context): Boolean {
        val p = prefs(context)
        if (p.getBoolean(KEY_AUTO_OPENED, false)) return false
        p.edit().putBoolean(KEY_AUTO_OPENED, true).apply()
        return true
    }

    fun items(context: Context): List<SetupItem> {
        val p = prefs(context)
        fun manual(id: String) = p.getBoolean("manual_$id", false)
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val notifOk = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        val pinOn = runCatching {
            Settings.System.getInt(context.contentResolver, "lock_to_app_enabled", 0) == 1
        }.getOrDefault(false)
        return listOf(
            SetupItem(
                "profile", "Hồ sơ giấc ngủ",
                "Năm sinh và vài thói quen để gợi ý giờ ngủ theo độ tuổi.",
                ProfileStore.load(context) != null, false, true,
            ),
            SetupItem(
                "notif", "Thông báo",
                "Để chuông hiện trên màn hình khóa và hỏi \"Bạn còn thức không?\" sau khi tắt chuông.",
                notifOk, false, true,
            ),
            SetupItem(
                "exact", "Báo thức chính xác", "Để chuông reo đúng giờ, không bị hệ thống dời lại.",
                AlarmScheduler.canScheduleExact(context), false, true,
            ),
            // Android 14 trở lên: phải được phép hiện toàn màn hình thì màn thử thách mới tự bật lên khi đang khóa máy.
            SetupItem(
                "fullscreen", "Hiện toàn màn hình khi khóa máy",
                "Để màn hình thử thách tự bật lên lúc chuông reo, kể cả khi máy đang khóa.",
                fullScreenAllowed(context), false, Build.VERSION.SDK_INT >= 34,
            ),
            SetupItem(
                "overlay", "Hiển thị trên ứng dụng khác",
                "Để màn hình thử thách tự bật lại khi bạn cố thoát ra.",
                Settings.canDrawOverlays(context), false, true,
            ),
            SetupItem(
                "battery", "Không tối ưu pin", "Để hệ thống không giết app trong lúc chờ chuông.",
                pm.isIgnoringBatteryOptimizations(context.packageName), false, true,
            ),
            SetupItem(
                "usage", "Truy cập dữ liệu sử dụng",
                "Để đo giấc ngủ từ lúc màn hình tắt. Tìm Challenge Alarm trong danh sách rồi bật lên.",
                // Android 8 không hỗ trợ đo giấc ngủ: coi như xong để dải thiết lập không hiện mãi.
                !SleepTracker.supported() || SleepTracker.hasUsageAccess(context), false, SleepTracker.supported(),
            ),
            SetupItem(
                "autostart", "Cho phép tự khởi động",
                "Oppo, Xiaomi, Samsung cần bật mục này. Bật xong quay lại bấm \"Đã xong\".",
                manual("autostart"), true, true,
            ),
            SetupItem(
                "pin", "Bật ghim màn hình",
                "Để chuông reo thì không thoát được bằng Home hay đa nhiệm. Bật xong bấm \"Đã xong\".",
                pinOn || manual("pin"), true, true,
            ),
            SetupItem(
                "lock", "Khóa app trong đa nhiệm",
                "Mở màn hình đa nhiệm, giữ thẻ Challenge Alarm rồi chọn Khóa. Oppo sẽ không dọn app khi vuốt.",
                manual("lock"), true, false,
            ),
        )
    }

    /** Android 14 (API 34) trở lên mới cần hỏi quyền này; máy cũ hơn luôn được phép. */
    private fun fullScreenAllowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < 34) return true
        val nm = context.getSystemService(NotificationManager::class.java) ?: return true
        return runCatching { nm.canUseFullScreenIntent() }.getOrDefault(true)
    }

    fun toggleManual(context: Context, id: String) {
        val p = prefs(context)
        p.edit().putBoolean("manual_$id", !p.getBoolean("manual_$id", false)).apply()
    }

    private fun packageUri(context: Context) = Uri.parse("package:${context.packageName}")

    private fun tryStart(context: Context, intent: Intent): Boolean =
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess

    private fun openAppDetails(context: Context) {
        tryStart(context, Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri(context)))
    }

    /** Màn hình quản lý tự khởi động của từng hãng; không có thì mở trang thông tin app. */
    private fun openAutostart(context: Context) {
        val candidates = listOf(
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
            ComponentName("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
            ComponentName("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
            ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            ComponentName("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
        )
        for (c in candidates) {
            if (tryStart(context, Intent().setComponent(c))) return
        }
        openAppDetails(context)
    }

    private fun openPinSettings(context: Context) {
        if (!tryStart(context, Intent(Settings.ACTION_SECURITY_SETTINGS))) openAppDetails(context)
    }

    fun open(context: Context, id: String, requestNotification: () -> Unit) {
        when (id) {
            "notif" -> requestNotification()
            "exact" -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                tryStart(context, Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, packageUri(context)))
            }
            "overlay" -> tryStart(context, Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri(context)))
            "fullscreen" -> if (Build.VERSION.SDK_INT >= 34) {
                if (!tryStart(context, Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, packageUri(context)))) {
                    openAppDetails(context)
                }
            }
            "battery" -> if (!tryStart(context, Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri(context)))) {
                openAppDetails(context)
            }
            "usage" -> SleepTracker.openUsageAccess(context)
            "autostart" -> openAutostart(context)
            "pin" -> openPinSettings(context)
        }
    }

    /** Reo thử một chuông tạm (không lưu, không ghi thống kê) để xem toàn bộ luồng chạy. */
    fun startTestRing(context: Context) {
        val svc = Intent(context, AlarmService::class.java)
            .setAction(AlarmService.ACTION_START)
            .putExtra(AlarmScheduler.EXTRA_ALARM_ID, TEST_ALARM_ID)
            .putExtra(AlarmScheduler.EXTRA_SNOOZE_COUNT, 0)
            .putExtra(AlarmScheduler.EXTRA_RINGTONE, "")
        ContextCompat.startForegroundService(context, svc)
    }
}

/** Dải nhắc ở đầu tab Chuông khi còn bước chưa làm. */
@Composable
fun SetupBanner(done: Int, total: Int, onClick: () -> Unit) {
    val p = AppTheme.palette
    AppCard(Modifier.padding(horizontal = GUTTER, vertical = 10.dp), onClick = onClick, padding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Hoàn tất thiết lập ($done/$total)", style = MaterialTheme.typography.titleSmall, color = p.text)
                Text(
                    "Điền hồ sơ và bật vài quyền để chuông reo chắc, đo được giấc ngủ.",
                    style = MaterialTheme.typography.bodySmall,
                    color = p.muted,
                )
            }
            Text("Mở", style = MaterialTheme.typography.labelLarge, color = p.accent, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { if (total == 0) 0f else done / total.toFloat() },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            color = p.accent,
            trackColor = p.track,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupSheet(
    items: List<SetupItem>,
    onOpen: (String) -> Unit,
    onToggleManual: (String) -> Unit,
    onTest: () -> Unit,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = cs.background,
    ) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = GUTTER).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Thiết lập để chuông chạy chắc", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Làm lần lượt từng mục. Quay lại app, dấu tích sẽ tự cập nhật.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )
            }

            SettingsGroup {
                items.forEachIndexed { index, item ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (item.done) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = if (item.done) "Đã xong" else "Chưa xong",
                            tint = if (item.done) AppTheme.palette.accent else cs.onSurfaceVariant,
                            modifier = Modifier.size(26.dp),
                        )
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(item.title, style = MaterialTheme.typography.titleSmall)
                            Text(item.why, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            if (item.canOpen && !item.done) {
                                TextButton(onClick = { onOpen(item.id) }) {
                                    Text(
                                        when (item.id) {
                                            "notif" -> "Cho phép"
                                            "profile" -> "Điền"
                                            else -> "Mở"
                                        }
                                    )
                                }
                            }
                            if (item.manual) {
                                TextButton(onClick = { onToggleManual(item.id) }) {
                                    Text(if (item.done) "Bỏ tích" else "Đã xong")
                                }
                            }
                        }
                    }
                    if (index < items.lastIndex) RowDivider()
                }
            }

            Button(
                onClick = onTest,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppTheme.palette.accent,
                    contentColor = AppTheme.palette.onAccent,
                ),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text("Thử chuông ngay", style = MaterialTheme.typography.labelLarge) }
            Text(
                "Chuông thử reo ngay với một bài toán. Làm xong để tắt, không ghi vào thống kê.",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
            )
            Spacer(Modifier.height(0.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Để sau") }
        }
    }
}
