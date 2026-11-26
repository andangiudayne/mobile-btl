package com.nhom.challengealarm

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Thoát khẩn cấp: chỉ mở sau 3 phút chuông reo, và phải giữ liền 15 giây. */
const val EMERGENCY_UNLOCK_MS = 3 * 60_000L
const val EMERGENCY_HOLD_MS = 15_000L
/** Nút "Đổi sang giải toán" chỉ hiện sau 90 giây ở một màn, và toán đổi sang khó hơn một bậc. */
const val MATH_SWITCH_AFTER_MS = 90_000L
/** Đánh dấu bài toán thay thế cho thử thách không dùng được (thiếu quyền, thiếu cảm biến, đã bị xóa). */
const val FALLBACK_PARAM = "fallback"

private val PLAIN_MATH = ActiveChallenge(ChallengeType.MATH, "Giải toán", "")

/** Toàn bộ trạng thái màn hình chuông reo. Giao diện vẽ lại theo state này, không tự giữ tiến độ. */
data class RingUiState(
    val ready: Boolean = false,
    val label: String = "",
    val difficulty: Int = 1,
    val mathCount: Int = 3,
    val rounds: Int = 1,
    val roundsDone: Int = 0,
    val breakUntil: Long = 0L,
    val current: ActiveChallenge = PLAIN_MATH,
    val roundFraction: Float = 0f,
    val snoozesLeft: Int = 0,
    val firedAt: Long = 0L,
    val emergencyUnlockAt: Long = 0L,
    /** Reo lại vì không trả lời "Bạn còn thức không?": 1 màn, không hoãn. */
    val recheck: Boolean = false,
    /** Chuông chợp mắt (ngủ trưa). */
    val nap: Boolean = false,
    /** Đã tắt chuông, đang hiện bản tin buổi sáng. summary = null nghĩa là đang tổng hợp. */
    val done: Boolean = false,
    val summary: MorningSummary? = null,
    val closed: Boolean = false,
) {
    val inBreak: Boolean get() = breakUntil > 0L
    val progress: Float get() = RoundPlanner.overallProgress(roundsDone, roundFraction, rounds)
    val canUseAwake: Boolean get() = RoundPlanner.canUseAwake(roundsDone)
    val isLastRound: Boolean get() = roundsDone + 1 >= rounds
}

/**
 * ViewModel của màn hình chuông reo: chọn thử thách cho từng màn, đếm màn, giờ nghỉ, hoãn, "Bạn đã tỉnh",
 * thoát khẩn cấp; lưu tiến độ vào RingSession và ra lệnh cho AlarmService (im chuông, reo lại, dừng).
 * RingActivity chỉ lo phần gắn với cửa sổ: ghim màn hình, giảm độ sáng, đóng màn hình khi closed = true.
 */
class RingViewModel(private val app: Application) : AndroidViewModel(app) {
    private val repo = AppGraph.alarms(app)
    private val _state = MutableStateFlow(RingUiState())
    val state: StateFlow<RingUiState> = _state.asStateFlow()

    private var started = false
    private var alarmId = -1
    private var alarm: AlarmEntity? = null
    private var pool: List<ActiveChallenge> = listOf(PLAIN_MATH)
    private var snoozeCount = 0
    private var breakMinutes = 0
    /** Đếm số lần màn hình này được dùng cho một chuông mới (xem restartIfFinished). */
    @Volatile private var runId = 0

    /** Gọi một lần từ onCreate. Gọi lại (xoay màn hình, tạo lại Activity) thì bỏ qua vì ViewModel vẫn giữ trạng thái. */
    fun start(alarmId: Int, intentSnoozeCount: Int) {
        if (started) return
        started = true
        this.alarmId = alarmId
        val session = RingSession.begin(app, alarmId)
        // Lấy số lần đã hoãn lớn nhất giữa intent và phiên đã lưu: app bị giết rồi dựng lại cũng không được hoãn lại từ đầu.
        snoozeCount = maxOf(intentSnoozeCount, session.snoozeCount)

        viewModelScope.launch {
            // Đọc cơ sở dữ liệu lỗi thì vẫn hiện thử thách (giải toán 1 màn), không để màn hình reo bị kẹt.
            val a = runCatching { repo.alarm(alarmId) }.getOrNull()
            alarm = a
            val ids = a?.challengeIdList().orEmpty().take(MAX_CHALLENGES_PER_ALARM).ifEmpty { listOf(0) }
            pool = ids.map { id -> runCatching { resolve(id) }.getOrDefault(PLAIN_MATH) }
            // Chuông thử (không có trong cơ sở dữ liệu) và lần reo lại sau "Bạn còn thức không?" chỉ có 1 màn.
            val recheck = session.recheck
            val rounds = if (recheck) 1 else (a?.rounds ?: 1).coerceIn(1, MAX_ROUNDS)
            breakMinutes = if (recheck) 0 else (a?.breakMinutes ?: 0).coerceIn(0, MAX_BREAK_MINUTES)
            val now = System.currentTimeMillis()
            _state.value = RingUiState(
                ready = true,
                label = if (recheck) "Bạn đã ngủ tiếp, làm thêm 1 màn nhé" else a?.label.orEmpty(),
                difficulty = a?.mathDifficulty ?: 1,
                mathCount = (a?.mathCount ?: 3).coerceAtLeast(1),
                rounds = rounds,
                roundsDone = session.roundsDone.coerceIn(0, rounds - 1),
                breakUntil = session.breakUntil.takeIf { it > now } ?: 0L,
                current = pickNext(null),
                snoozesLeft = if (recheck) 0 else ((a?.snoozeLimit ?: 0) - snoozeCount).coerceAtLeast(0),
                firedAt = session.firedAt,
                // Tính từ lần reo này (không phải lần reo đầu), để hoãn xong quay lại không thoát khẩn cấp được ngay.
                emergencyUnlockAt = if (alarmId < 0) 0L else now + EMERGENCY_UNLOCK_MS,
                recheck = recheck,
                nap = a?.nap == true,
            )
        }
    }

    /**
     * Màn hình đang hiện bản tin buổi sáng (chuông cũ đã tắt) mà có chuông mới reo (chuông khác, hoặc reo lại vì không trả
     * lời "Bạn còn thức không?"): bắt đầu lại từ đầu cho chuông mới, không để bản tin cũ che mất thử thách.
     */
    fun restartIfFinished(alarmId: Int, intentSnoozeCount: Int) {
        val s = _state.value
        if (!s.done && !s.closed) return // vẫn đang reo chuông hiện tại: giữ nguyên
        runId++
        started = false
        alarm = null
        pool = listOf(PLAIN_MATH)
        _state.value = RingUiState()
        start(alarmId, intentSnoozeCount)
    }

    fun onRoundProgress(fraction: Float) {
        _state.update { it.copy(roundFraction = fraction.coerceIn(0f, 1f)) }
    }

    /**
     * Xong một màn. Hết màn thì tắt chuông; còn màn thì chọn thử thách mới,
     * và nếu màn vừa xong là màn trí não thì cho nghỉ (chuông im) 1 đến 2 phút.
     * type = loại thử thách gốc của màn, để đổi từ đi bộ sang toán không được hưởng giờ nghỉ.
     */
    fun completeRound(method: String, type: ChallengeType) {
        val s = _state.value
        if (s.closed || s.done || !s.ready) return
        val done = s.roundsDone + 1
        if (done >= s.rounds) {
            finish(method, done)
            return
        }
        val now = System.currentTimeMillis()
        val pauseMs = RoundPlanner.breakMillis(type.isMental, breakMinutes, done, s.rounds)
        val until = if (pauseMs > 0) now + pauseMs else 0L
        _state.value = s.copy(roundsDone = done, roundFraction = 0f, current = pickNext(s.current), breakUntil = until)

        val saved = RingSession.load(app, alarmId) ?: RingSession.begin(app, alarmId, now)
        RingSession.save(
            app,
            saved.copy(roundsDone = done, wokeAt = if (saved.wokeAt > 0) saved.wokeAt else now, breakUntil = until),
        )
        if (until > 0) AlarmService.pause(app, until)
    }

    /** Hết giờ nghỉ hoặc người dùng bấm "Làm tiếp ngay". */
    fun endBreak() {
        if (!_state.value.inBreak) return
        _state.update { it.copy(breakUntil = 0L) }
        RingSession.load(app, alarmId)?.let { RingSession.save(app, it.copy(breakUntil = 0L)) }
        AlarmService.resume(app)
    }

    /** Giữ nút "Bạn đã tỉnh" (từ màn 2). */
    fun awake() = finish(WakeMethod.AWAKE, _state.value.roundsDone)

    fun emergency() = finish(WakeMethod.EMERGENCY, _state.value.roundsDone)

    /**
     * Hoãn chuông: giữ phiên (giờ reo đầu tiên, màn đã qua, số lần đã hoãn) chứ không xóa.
     * Trong lúc hoãn, màn hình chính khóa chuông này lại, không tắt hay xóa được.
     */
    fun snooze() {
        val a = alarm ?: return
        val s = _state.value
        if (s.closed || s.done || s.recheck || s.snoozesLeft <= 0) return
        val newCount = snoozeCount + 1
        AlarmScheduler.scheduleSnooze(app, a, newCount)
        RingSession.load(app, alarmId)?.let { RingSession.save(app, it.copy(breakUntil = 0L, snoozeCount = newCount)) }
        close()
    }

    /**
     * Tắt hẳn chuông. Sau đó (chạy nền): ghi nhật ký, tính giấc ngủ đêm qua, đặt lại lời nhắc đi ngủ,
     * hẹn "Bạn còn thức không?" và tổng hợp bản tin buổi sáng.
     * Chuông thử, lần reo lại sau kiểm tra và thoát khẩn cấp thì đóng ngay, không hiện bản tin.
     */
    private fun finish(method: String, roundsDone: Int) {
        val s = _state.value
        if (s.closed || s.done) return
        val now = System.currentTimeMillis()
        val session = RingSession.load(app, alarmId)
        val log = WakeLog(
            alarmId = alarmId,
            label = s.label,
            firedAt = s.firedAt.takeIf { it > 0 } ?: now,
            dismissedAt = now,
            method = method,
            wokeAt = session?.wokeAt?.takeIf { it > 0 } ?: now,
            rounds = roundsDone,
        )
        val isTest = alarmId < 0
        // Chỉ ghi thống kê cho chuông sáng thật: không ghi chuông thử, chuông chợp mắt, lần reo lại sau kiểm tra.
        val writeLog = !isTest && !s.recheck && !s.nap
        val showSummary = !isTest && !s.recheck && method != WakeMethod.EMERGENCY
        val checkMin = if (isTest || s.recheck) 0 else alarm?.wakeCheckMin ?: 0
        val doCheck = WakeCheckRules.shouldCheck(method, checkMin, WakeCheck.notificationsAllowed(app))
        val nap = s.nap
        val id = alarmId

        RingSession.clear(app, alarmId)
        stopRinging()
        _state.update { if (showSummary) it.copy(done = true, summary = null) else it.copy(closed = true) }
        val myRun = runId

        // Scope riêng (không phải viewModelScope): màn hình có thể đóng trước khi ghi xong nhưng việc ghi vẫn phải xong.
        CoroutineScope(Dispatchers.IO).launch {
            val logId = if (writeLog) runCatching { repo.insertWakeLog(log) }.getOrNull() else null
            if (writeLog) runCatching { SleepTracker.backfill(app) }
            if (nap) runCatching { repo.deleteIfNap(id) }
            runCatching { BedtimeReminder.refresh(app) }
            val checkAt = if (doCheck) runCatching { WakeCheck.schedule(app, id, logId ?: -1L, checkMin) }.getOrNull() else null
            if (showSummary) {
                val summary = if (nap) {
                    MorningReport.nap(checkAt)
                } else {
                    runCatching { buildSummary(log, checkAt) }.getOrNull() ?: MorningSummary(wakeCheckAt = checkAt)
                }
                // Trong lúc tổng hợp mà đã có chuông mới reo (restartIfFinished) thì bỏ bản tin cũ.
                if (runId == myRun) _state.update { it.copy(summary = summary) }
            }
        }
    }

    private suspend fun buildSummary(log: WakeLog, checkAt: Long?): MorningSummary {
        val now = System.currentTimeMillis()
        val sleepRepo = AppGraph.sleep(app)
        val logs = repo.recentWakeLogs(now - 30L * 24 * 3_600_000L)
        val sleepLogs = sleepRepo.recentSleepLogs(now - 8L * 24 * 3_600_000L)
        val profile = sleepRepo.profile.value
        val target = profile?.let { SleepAdvisor.targetMinutes(it, it.age(now)) }
        val night = SleepAnalyzer.dayStart(log.firedAt)
        val lastNight = sleepLogs.firstOrNull { it.nightOf == night }?.toNight()
        return MorningReport.build(log, logs, lastNight, sleepLogs.map { it.toNight() }, target, checkAt, now)
    }

    /** Người dùng đóng bản tin buổi sáng (hoặc tự đóng sau vài phút). */
    fun closeSummary() {
        _state.update { it.copy(closed = true) }
    }

    /** Dừng tiếng chuông và báo service không mở lại màn hình thử thách nữa. */
    private fun stopRinging() {
        RingState.stopping = true
        AlarmScheduler.cancelReRing(app, alarmId)
        AlarmService.stop(app)
    }

    /** Dừng chuông và báo màn hình đóng lại. */
    private fun close() {
        stopRinging()
        _state.update { it.copy(closed = true) }
    }

    /** Màn mới: không lặp lại thử thách vừa làm; quét mã/chụp vật mà phải lặp lại thì đổi sang giải toán
     *  (camera vẫn đang hướng vào đúng mã đó, màn mới sẽ qua ngay, mất ý nghĩa). */
    private fun pickNext(previous: ActiveChallenge?): ActiveChallenge {
        val n = RoundPlanner.next(pool, previous)
        val camera = n.type == ChallengeType.QR || n.type == ChallengeType.PHOTO
        return if (n == previous && camera) PLAIN_MATH else n
    }

    /**
     * Đổi id đã chọn thành thử thách cụ thể. Không dùng được (thu hồi quyền camera, thiếu cảm biến, mã QR đã bị xóa)
     * thì đổi sang toán khó hơn một bậc, để việc tắt quyền trước khi ngủ không thành đường tắt.
     */
    private suspend fun resolve(id: Int): ActiveChallenge {
        val fallback = ActiveChallenge(ChallengeType.MATH, "Giải toán", FALLBACK_PARAM)
        val picked = when {
            id == 0 -> PLAIN_MATH
            id < 0 -> BuiltIn.byId(id)?.let { ActiveChallenge(it.type, it.name, "") } ?: fallback
            else -> repo.challengeItem(id)?.let { ActiveChallenge(it.kind, it.name, it.param) } ?: fallback
        }
        return if (isUsable(app, picked.type)) picked else fallback
    }

    companion object {
        /** Máy có cảm biến cần cho thử thách này không (chưa xét quyền). Dùng để báo trước ở trình sửa chuông. */
        fun hasHardware(context: Context, type: ChallengeType): Boolean {
            val sensors = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            return when (type) {
                ChallengeType.SHAKE, ChallengeType.TILT -> sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
                ChallengeType.STEPS -> sensors.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null
                ChallengeType.LIGHT -> sensors.getDefaultSensor(Sensor.TYPE_LIGHT) != null
                else -> true
            }
        }

        /** Máy có đủ quyền và cảm biến cho thử thách này không. */
        fun isUsable(context: Context, type: ChallengeType): Boolean {
            val sensors = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            fun granted(permission: String) =
                ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            return when (type) {
                ChallengeType.QR, ChallengeType.PHOTO -> granted(Manifest.permission.CAMERA)
                ChallengeType.SHAKE, ChallengeType.TILT -> sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null
                ChallengeType.LIGHT -> sensors.getDefaultSensor(Sensor.TYPE_LIGHT) != null
                ChallengeType.STEPS ->
                    sensors.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null &&
                        (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                            granted(Manifest.permission.ACTIVITY_RECOGNITION))
                else -> true
            }
        }
    }
}
