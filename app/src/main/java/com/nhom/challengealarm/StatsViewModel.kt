package com.nhom.challengealarm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Trạng thái tab Thống kê. stats = null khi đang tải.
 * insight = so sánh thời gian để tỉnh sau đêm ngủ đủ và đêm ngủ thiếu (null nếu chưa đủ dữ liệu).
 */
data class StatsUiState(
    val stats: WakeStats? = null,
    val recent: List<WakeLog> = emptyList(),
    val insight: SleepWakeInsight? = null,
    val targetMin: Int = SleepAdvisor.DEFAULT_TARGET_MIN,
)

/**
 * ViewModel của tab Thống kê: chuỗi ngày, tỉ lệ đúng giờ, biểu đồ 7 ngày từ nhật ký 30 ngày,
 * và ghép với nhật ký giấc ngủ để xem ngủ đủ có giúp dậy nhanh hơn không.
 */
class StatsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = AppGraph.alarms(app)
    private val sleepRepo = AppGraph.sleep(app)

    val state: StateFlow<StatsUiState> =
        combine(
            repo.wakeLogsSince(System.currentTimeMillis() - WINDOW_MS),
            sleepRepo.sleepLogsSince(System.currentTimeMillis() - WINDOW_MS),
            sleepRepo.profile,
            clockFlow(60_000),
        ) { logs, sleepLogs, profile, now ->
            val target = profile?.let { SleepAdvisor.targetMinutes(it, it.age(now)) } ?: SleepAdvisor.DEFAULT_TARGET_MIN
            StatsUiState(
                stats = StatsCalculator.compute(logs, now),
                recent = logs.take(RECENT_COUNT),
                insight = Insights.sleepVsWake(sleepLogs.map { it.toNight() }, logs, target),
                targetMin = target,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    private companion object {
        const val WINDOW_MS = 30L * 24 * 3_600_000L
        const val RECENT_COUNT = 6
    }
}
