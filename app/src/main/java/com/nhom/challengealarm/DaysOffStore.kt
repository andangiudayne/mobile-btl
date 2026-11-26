package com.nhom.challengealarm

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lưu "Kỳ nghỉ của tôi" trên máy (SharedPreferences) và đưa vào DaysOff để tính giờ chuông.
 * Ngày lễ không cần lưu: VnHolidays tự tính theo năm. Receiver chạy trong tiến trình mới gọi ensureLoaded() trước.
 */
object DaysOffStore {
    private const val PREFS = "days_off"
    private const val KEY_VACATIONS = "vacations"

    @Volatile private var loaded = false
    private val _vacations = MutableStateFlow<List<Vacation>>(emptyList())
    val vacations: StateFlow<List<Vacation>> = _vacations.asStateFlow()

    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val list = DaysOff.decode(prefs(context).getString(KEY_VACATIONS, "").orEmpty())
            DaysOff.vacations = list
            _vacations.value = list
            loaded = true
        }
    }

    fun save(context: Context, list: List<Vacation>) {
        synchronized(this) {
            prefs(context).edit().putString(KEY_VACATIONS, DaysOff.encode(list)).commit()
            DaysOff.vacations = list
            _vacations.value = list
            loaded = true
        }
    }
}
