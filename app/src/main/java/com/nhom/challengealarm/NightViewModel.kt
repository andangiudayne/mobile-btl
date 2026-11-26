package com.nhom.challengealarm

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Đọc trạng thái máy cho "Kiểm tra trước khi ngủ" và mở đúng trang cài đặt để sửa (tầng Model, không có giao diện). */
object DeviceStatus {

    /** Không làm phiền đang chặn cả báo thức? (Tắt hoàn toàn, hoặc chế độ Ưu tiên mà không cho phép báo thức.) */
    fun alarmsBlockedByDnd(context: Context): Boolean {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return false
        return when (nm.currentInterruptionFilter) {
            NotificationManager.INTERRUPTION_FILTER_NONE -> true
            NotificationManager.INTERRUPTION_FILTER_PRIORITY ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    runCatching {
                        (nm.notificationPolicy.priorityCategories and NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS) == 0
                    }.getOrDefault(false)
                } else {
                    false
                }
            else -> false
        }
    }

    fun read(context: Context, nextWake: Long?, now: Long = System.currentTimeMillis()): NightInputs {
        val bm = context.getSystemService(BatteryManager::class.java)
        val pct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)?.takeIf { it in 0..100 }
        return NightInputs(
            now = now,
            nextWake = nextWake,
            batteryPct = pct,
            charging = bm?.isCharging == true,
            alarmsBlockedByDnd = alarmsBlockedByDnd(context),
            exactAllowed = AlarmScheduler.canScheduleExact(context),
            notificationsAllowed = WakeCheck.notificationsAllowed(context),
            sleepTrackingSupported = SleepTracker.supported(),
            usageAccess = SleepTracker.hasUsageAccess(context),
        )
    }

    private fun tryStart(context: Context, intent: Intent): Boolean =
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess

    /** Mở trang cài đặt để sửa mục id của NightCheck. Mục "alarm" do giao diện tự xử lý (mở trình thêm chuông). */
    fun openFix(context: Context, id: String) {
        when (id) {
            NightCheck.ID_DND -> {
                // Trang Không làm phiền; máy nào không có thì mở trang Âm thanh.
                if (!tryStart(context, Intent("android.settings.ZEN_MODE_SETTINGS"))) {
                    tryStart(context, Intent(Settings.ACTION_SOUND_SETTINGS))
                }
            }
            NightCheck.ID_EXACT -> Setup.open(context, "exact") {}
            NightCheck.ID_USAGE -> SleepTracker.openUsageAccess(context)
            NightCheck.ID_NOTIF -> tryStart(
                context,
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
            )
        }
    }
}

/**
 * ViewModel của màn "Chúc ngủ ngon": danh sách kiểm tra trước khi ngủ và âm thanh ru ngủ.
 * refresh() gọi khi mở màn này và mỗi lần quay lại app (người dùng có thể vừa cắm sạc hoặc sửa cài đặt).
 */
class NightViewModel(private val app: Application) : AndroidViewModel(app) {
    private val alarmRepo = AppGraph.alarms(app)
    private val _checks = MutableStateFlow<List<NightCheckItem>>(emptyList())
    val checks: StateFlow<List<NightCheckItem>> = _checks.asStateFlow()

    val noise: StateFlow<NoisePlayback?> = NoiseService.playing

    fun refresh() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val alarms = runCatching { alarmRepo.allAlarms() }.getOrDefault(emptyList())
            val next = AlarmViewModel.nextOf(alarms, now, includeNaps = false)?.at
            _checks.value = NightCheck.evaluate(DeviceStatus.read(app, next, now))
        }
    }

    fun fix(id: String) = DeviceStatus.openFix(app, id)

    fun playNoise(type: NoiseType, minutes: Int) = NoiseService.play(app, type, minutes)

    fun stopNoise() = NoiseService.stop(app)
}
