package com.nhom.challengealarm

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Chế độ giao diện: tự động theo giờ (6:00 đến 18:00 sáng, còn lại đêm), hoặc người dùng ép sáng/đêm
 * bằng nút ở góc trên (để thử cả hai giao diện bất kỳ lúc nào). Lựa chọn được lưu lại.
 */
class ThemeViewModel(private val app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _override = MutableStateFlow(load())
    val override: StateFlow<ModeOverride> = _override.asStateFlow()

    /** Chế độ đang dùng. Kiểm tra giờ mỗi 30 giây nên tự đổi đúng lúc 6:00 và 18:00. */
    val mode: StateFlow<AppMode> =
        combine(_override, clockFlow(30_000)) { o, now -> resolve(o, now) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, resolve(_override.value, System.currentTimeMillis()))

    /** Tự động → Luôn sáng → Luôn đêm → Tự động. */
    fun cycle() {
        val next = when (_override.value) {
            ModeOverride.AUTO -> ModeOverride.DAY
            ModeOverride.DAY -> ModeOverride.NIGHT
            ModeOverride.NIGHT -> ModeOverride.AUTO
        }
        _override.value = next
        prefs.edit().putString(KEY, next.name).apply()
    }

    private fun load(): ModeOverride =
        runCatching { ModeOverride.valueOf(prefs.getString(KEY, null) ?: "AUTO") }.getOrDefault(ModeOverride.AUTO)

    companion object {
        private const val PREFS = "ui"
        private const val KEY = "mode_override"

        fun resolve(o: ModeOverride, now: Long): AppMode = when (o) {
            ModeOverride.AUTO -> AppMode.byClock(now)
            ModeOverride.DAY -> AppMode.DAY
            ModeOverride.NIGHT -> AppMode.NIGHT
        }
    }
}
