package com.nhom.challengealarm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Trạng thái tab Giấc ngủ. report và plan được tính sẵn trong ViewModel, giao diện chỉ hiển thị. */
data class SleepUiState(
    val profile: UserProfile? = null,
    val age: Int = 0,
    val group: AgeGroup = AgeGroup.ADULT,
    val logs: List<SleepLog> = emptyList(),
    val report: SleepReport? = null,
    val plan: SleepPlan? = null,
    val nextWake: Long? = null,
    /** Số phút ngủ mục tiêu theo tuổi (dùng cho thanh tiến độ "Thời lượng"). */
    val targetMin: Int = 480,
    val usageOk: Boolean = false,
    val supported: Boolean = true,
    val now: Long = System.currentTimeMillis(),
)

/** ViewModel của tab Giấc ngủ và bảng hồ sơ: hồ sơ, đêm ngủ, lời khuyên theo tuổi, kế hoạch tối nay. */
class SleepViewModel(app: Application) : AndroidViewModel(app) {
    private val sleepRepo = AppGraph.sleep(app)
    private val alarmRepo = AppGraph.alarms(app)
    private val usageOk = MutableStateFlow(sleepRepo.hasUsageAccess())

    val profile: StateFlow<UserProfile?> = sleepRepo.profile

    val state: StateFlow<SleepUiState> =
        combine(
            sleepRepo.profile,
            sleepRepo.sleepLogsSince(System.currentTimeMillis() - WINDOW_MS),
            alarmRepo.alarms,
            usageOk,
            clockFlow(60_000),
        ) { profile, logs, alarms, usage, now ->
            val nextWake = AlarmViewModel.nextOf(alarms, now, includeNaps = false)?.at
            val age = profile?.age(now) ?: 0
            SleepUiState(
                profile = profile,
                age = age,
                group = if (profile != null) AgeGroup.of(age) else AgeGroup.ADULT,
                logs = logs,
                report = profile?.let { SleepAdvisor.report(logs.map { l -> l.toNight() }, it, age, now = now) },
                plan = if (profile != null && nextWake != null) SleepAdvisor.plan(profile, age, nextWake) else null,
                nextWake = nextWake,
                targetMin = profile?.let { SleepAdvisor.targetMinutes(it, age) } ?: 480,
                usageOk = usage,
                supported = sleepRepo.supported(),
                now = now,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            // Trạng thái ban đầu đã có hồ sơ (nếu có), để không nháy màn "Điền hồ sơ" lúc mới mở tab.
            initialState(),
        )

    init {
        // Có quyền đọc màn hình (hoặc vừa có thêm lần dậy mới) thì tính bù các đêm còn thiếu.
        viewModelScope.launch {
            combine(usageOk, alarmRepo.wakeLogsSince(System.currentTimeMillis() - WINDOW_MS)) { ok, logs -> ok to logs.size }
                .distinctUntilChanged()
                .collect { (ok, _) -> if (ok) sleepRepo.backfill() }
        }
    }

    private fun initialState(): SleepUiState {
        val profile = sleepRepo.profile.value
        val age = profile?.age() ?: 0
        return SleepUiState(
            profile = profile,
            age = age,
            group = if (profile != null) AgeGroup.of(age) else AgeGroup.ADULT,
            usageOk = usageOk.value,
        )
    }

    /** Gọi khi quay lại app (người dùng có thể vừa bật quyền trong Cài đặt). */
    fun refreshPermissions() {
        usageOk.value = sleepRepo.hasUsageAccess()
    }

    fun openUsageAccess() = sleepRepo.openUsageAccess()

    fun saveProfile(profile: UserProfile) {
        viewModelScope.launch { sleepRepo.saveProfile(profile) }
    }

    private companion object {
        const val WINDOW_MS = 30L * 24 * 3_600_000L
    }
}
