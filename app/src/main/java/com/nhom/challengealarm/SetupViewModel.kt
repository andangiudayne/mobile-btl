package com.nhom.challengealarm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** ViewModel của bảng "Thiết lập để chuông chạy chắc": danh sách bước, tích tay, chuông thử. */
class SetupViewModel(private val app: Application) : AndroidViewModel(app) {
    private val _items = MutableStateFlow(Setup.items(app))
    val items: StateFlow<List<SetupItem>> = _items.asStateFlow()

    init {
        // Lưu hồ sơ xong thì bước "Hồ sơ giấc ngủ" tự đánh dấu xong.
        viewModelScope.launch { AppGraph.sleep(app).profile.collect { refresh() } }
    }

    /** Đọc lại trạng thái quyền (gọi mỗi lần quay lại app). */
    fun refresh() {
        _items.value = Setup.items(app)
    }

    fun toggleManual(id: String) {
        Setup.toggleManual(app, id)
        refresh()
    }

    /** true đúng một lần: lần đầu mở app. */
    fun consumeFirstRun(): Boolean = Setup.consumeFirstRun(app)

    fun open(id: String, requestNotification: () -> Unit) = Setup.open(app, id, requestNotification)

    /** Reo chuông thử. Không cho thử khi đang có chuông thật reo hoặc hoãn (trả false). */
    fun startTestRing(): Boolean {
        if (RingSession.active(app).isNotEmpty()) return false
        Setup.startTestRing(app)
        return true
    }
}
