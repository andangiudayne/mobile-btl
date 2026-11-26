package com.nhom.challengealarm

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Dữ liệu ban đầu khi cài app, để mở lên là thấy một app đã dùng được một tháng (phục vụ trình bày):
 *   - Hồ sơ: sinh viên 21 tuổi (DemoData.sampleProfile), sửa được trong Hồ sơ.
 *   - Chuông "Đi học" 6:30 thứ Hai đến thứ Sáu (3 màn) và "Cuối tuần" 7:30 thứ Bảy, ĐANG BẬT như chuông thật.
 *   - 30 ngày nhật ký thức dậy và giấc ngủ gắn với hai chuông đó (DemoData.generate).
 *   - Tab Gia đình: Ông nội, Bà nội, Bé Na (FamilyDemo), mỗi sáng có thêm đêm mới (FamilyViewModel.refreshDaily).
 *
 * Mỗi phần chỉ chạy một lần cho mỗi lần cài (cờ trong SharedPreferences) và không đè lên dữ liệu người dùng đã có.
 * Gỡ app rồi cài lại (hoặc Xóa dữ liệu app) thì nạp lại từ đầu.
 */
object InitialData {
    private const val PREFS = "initial_data"
    private const val KEY_PROFILE_FAMILY = "profile_family_v1"
    private const val KEY_HISTORY = "history_v1"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Gọi trong MainActivity.onCreate, trước setContent. Phần hồ sơ và gia đình (SharedPreferences, rất nhanh) làm ngay
     * để giao diện không bật màn "điền hồ sơ"; phần lịch sử (Room) chạy nền, xong thì các màn tự cập nhật qua Flow.
     */
    fun ensure(context: Context) {
        val app = context.applicationContext
        val now = System.currentTimeMillis()
        val p = prefs(app)
        if (!p.getBoolean(KEY_PROFILE_FAMILY, false)) {
            runCatching {
                AppGraph.sleep(app).seedProfileIfMissing(DemoData.sampleProfile(now))
                AppGraph.family(app).seedIfEmpty(now)
            }
            p.edit().putBoolean(KEY_PROFILE_FAMILY, true).apply()
        }
        if (!p.getBoolean(KEY_HISTORY, false)) {
            scope.launch {
                lock.withLock {
                    if (p.getBoolean(KEY_HISTORY, false)) return@withLock
                    val ok = runCatching { AppGraph.alarms(app).seedHistory(now) }.isSuccess
                    // Lỗi (hiếm) thì để lần mở app sau thử lại.
                    if (ok) p.edit().putBoolean(KEY_HISTORY, true).apply()
                }
            }
        }
    }
}
