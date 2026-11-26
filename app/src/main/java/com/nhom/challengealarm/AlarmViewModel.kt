package com.nhom.challengealarm

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Chuông sẽ reo kế tiếp và còn bao nhiêu phút nữa. */
data class NextAlarm(val alarm: AlarmEntity, val at: Long, val minutesLeft: Long)

/** Đồng hồ cho giao diện: phát giờ hiện tại mỗi intervalMs để các con số "còn bao lâu" tự cập nhật. */
internal fun clockFlow(intervalMs: Long): Flow<Long> = flow {
    while (true) {
        emit(System.currentTimeMillis())
        delay(intervalMs)
    }
}

/**
 * ViewModel của tab Báo thức và tab Thử thách: danh sách chuông, chuông kế tiếp, thư viện thử thách
 * và mọi thao tác thêm, sửa, bật/tắt, xóa. Giao diện chỉ đọc StateFlow và gọi hàm, không đụng vào dữ liệu.
 */
class AlarmViewModel(private val app: Application) : AndroidViewModel(app) {
    private val repo = AppGraph.alarms(app)

    /** Thông báo ngắn cho người dùng (hiện bằng Snackbar). */
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** null = đang tải (để giao diện không nháy trạng thái "chưa có chuông" lúc mới mở). */
    val alarms: StateFlow<List<AlarmEntity>?> =
        repo.alarms.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val library: StateFlow<List<ChallengeItem>> =
        repo.library.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Kỳ nghỉ của tôi (lịch nghỉ). */
    val vacations: StateFlow<List<Vacation>> = repo.vacations

    val next: StateFlow<NextAlarm?> =
        combine(repo.alarms, clockFlow(30_000), repo.vacations) { list, now, _ -> nextOf(list, now) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Id các chuông đang reo hoặc đang hoãn (bị khóa), cập nhật mỗi 15 giây. */
    val lockedIds: StateFlow<Set<Int>> =
        combine(repo.alarms, clockFlow(15_000)) { _, _ -> repo.lockedIds() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    /** Kiểm tra đã tỉnh đang chờ (null nếu không có), đọc lại mỗi 3 giây để thẻ "Bạn còn thức không?" hiện kịp. */
    val wakeCheck: StateFlow<WakeCheckState?> =
        clockFlow(3_000).map { WakeCheck.load(app) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WakeCheck.load(app))

    init {
        // Mở app: đặt lại lịch reo và lời nhắc đi ngủ (phòng khi hệ thống đã xóa, ví dụ app bị buộc dừng).
        viewModelScope.launch {
            repo.rescheduleAll()
            repo.refreshReminder()
        }
    }

    /** Trả lời "Tôi đã dậy" ngay trong app. */
    fun confirmAwake() {
        if (WakeCheck.confirm(app)) _messages.tryEmit("Tốt lắm! Chúc bạn một ngày tốt lành.")
    }

    /** "Bỏ qua lần reo tới" (bấm lần nữa để hủy). Chỉ cho chuông lặp, và không cho khi chuông đang reo/hoãn. */
    fun toggleSkip(alarm: AlarmEntity) {
        if (alarm.daysMask == 0 || !canEdit(alarm)) return
        viewModelScope.launch {
            val until = repo.toggleSkip(alarm)
            _messages.tryEmit(
                when {
                    until == null -> "Chuông này không có lần reo kế tiếp."
                    until == 0L -> "Đã hủy bỏ qua, chuông reo lại như cũ."
                    else -> "Đã bỏ qua lần reo ${weekdayShort(until)} ${hhmm(until)}. Sau đó chuông reo lại như cũ."
                }
            )
        }
    }

    /** Thêm kỳ nghỉ (từ start đến end, tính cả hai đầu). */
    fun addVacation(start: Ymd, end: Ymd, name: String) {
        viewModelScope.launch {
            val today = Ymd.of(System.currentTimeMillis())
            repo.setVacations(DaysOff.add(repo.vacations.value, Vacation(minOf(start, end), maxOf(start, end), name), today))
            val days = daysBetween(minOf(start, end), maxOf(start, end)) + 1
            _messages.tryEmit("Đã thêm \"$name\" ($days ngày). Chuông bật \"Không reo ngày nghỉ\" sẽ nghỉ những ngày này.")
        }
    }

    fun removeVacation(v: Vacation) {
        viewModelScope.launch {
            repo.setVacations(repo.vacations.value - v)
            _messages.tryEmit("Đã xóa kỳ nghỉ \"${v.name}\".")
        }
    }

    /** Tạo chuông chợp mắt minutes phút. */
    fun startNap(minutes: Int) {
        viewModelScope.launch {
            val nap = repo.createNap(minutes)
            _messages.tryEmit("Chợp mắt ngon nhé! Chuông reo lúc %02d:%02d.".format(nap.hour, nap.minute))
        }
    }

    /** Có được mở/sửa chuông này không. Chuông đang reo hoặc đang hoãn thì báo lý do và trả false. */
    fun canEdit(alarm: AlarmEntity): Boolean {
        if (alarm.id != 0 && repo.isLocked(alarm.id)) {
            _messages.tryEmit(LOCKED_MESSAGE)
            return false
        }
        return true
    }

    fun toggle(alarm: AlarmEntity, on: Boolean) {
        if (!on && !canEdit(alarm)) return
        viewModelScope.launch { repo.setEnabled(alarm, on) }
    }

    fun save(alarm: AlarmEntity) {
        if (!canEdit(alarm)) return
        viewModelScope.launch {
            repo.save(alarm)
            _messages.tryEmit("Đã lưu chuông %02d:%02d".format(alarm.hour, alarm.minute))
        }
    }

    fun delete(alarm: AlarmEntity) {
        if (!canEdit(alarm)) return
        viewModelScope.launch { repo.delete(alarm) }
    }

    fun addChallenge(type: ChallengeType, name: String, param: String) {
        viewModelScope.launch {
            repo.addChallenge(type, name, param)
            _messages.tryEmit("Đã thêm \"$name\" vào thư viện")
        }
    }

    fun deleteChallenge(item: ChallengeItem) {
        viewModelScope.launch {
            val affected = repo.deleteChallenge(item.id)
            _messages.tryEmit(
                when {
                    affected == null -> "\"${item.name}\" đang dùng cho chuông đang reo hoặc hoãn, chưa xóa được."
                    (affected ?: 0) > 0 -> "Đã xóa \"${item.name}\" và bỏ khỏi $affected chuông."
                    else -> "Đã xóa \"${item.name}\"."
                }
            )
        }
    }

    companion object {
        const val LOCKED_MESSAGE = "Chuông này đang reo hoặc đang hoãn. Làm xong thử thách mới sửa được."

        /**
         * Chuông bật sớm nhất tính từ now. Hàm thuần nên dùng lại được ở nơi khác (trang Giấc ngủ).
         * includeNaps = false để bỏ chuông chợp mắt (khi tính giờ ngủ đêm).
         */
        fun nextOf(alarms: List<AlarmEntity>, now: Long, includeNaps: Boolean = true): NextAlarm? =
            alarms.asSequence()
                .filter { it.enabled && (includeNaps || !it.nap) }
                .mapNotNull { a -> AlarmScheduler.nextTriggerMillis(a, now)?.let { a to it } }
                .minByOrNull { it.second }
                ?.let { (a, at) -> NextAlarm(a, at, (at - now) / 60_000L) }
    }
}
