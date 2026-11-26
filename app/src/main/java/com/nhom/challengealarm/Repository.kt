package com.nhom.challengealarm

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// =================================================================================================
// TẦNG MODEL (MVVM): Repository là cửa duy nhất để ViewModel lấy và ghi dữ liệu.
// ViewModel không gọi DAO, AlarmManager hay SharedPreferences trực tiếp, nên đổi cách lưu trữ
// (ví dụ thêm máy chủ sau này) chỉ cần sửa ở đây.
//
//   View (Compose)  ->  ViewModel (StateFlow)  ->  Repository  ->  Room / AlarmManager / SharedPreferences
// =================================================================================================

/** Chuông, thư viện thử thách và nhật ký thức dậy. */
class AlarmRepository(context: Context) {
    private val app = context.applicationContext
    private val db = AppDatabase.get(app)

    init {
        DaysOffStore.ensureLoaded(app)
    }

    /** Kỳ nghỉ người dùng tự thêm (ngày lễ thì tự tính, không cần lưu). */
    val vacations: StateFlow<List<Vacation>> get() = DaysOffStore.vacations

    /** Lưu danh sách kỳ nghỉ rồi đặt lại lịch mọi chuông (chuông có bật "Không reo ngày nghỉ" sẽ đổi lần reo kế tiếp). */
    suspend fun setVacations(list: List<Vacation>) {
        DaysOffStore.save(app, list)
        rescheduleAll()
        refreshReminder()
    }

    val alarms: Flow<List<AlarmEntity>> = db.alarmDao().observeAll()
    val library: Flow<List<ChallengeItem>> = db.challengeDao().observeAll()

    fun wakeLogsSince(since: Long): Flow<List<WakeLog>> = db.wakeLogDao().observeSince(since)

    /** Đọc một lần (không theo dõi), dùng cho bản tin buổi sáng. */
    suspend fun recentWakeLogs(since: Long): List<WakeLog> = db.wakeLogDao().getSince(since)

    suspend fun alarm(id: Int): AlarmEntity? = db.alarmDao().getById(id)

    suspend fun allAlarms(): List<AlarmEntity> = db.alarmDao().getAll()

    suspend fun challengeItem(id: Int): ChallengeItem? = db.challengeDao().getByIds(listOf(id)).firstOrNull()

    /** Chuông đang reo hoặc đang hoãn (chưa làm xong thử thách): khóa, không cho tắt, sửa hay xóa. */
    fun isLocked(alarmId: Int): Boolean = RingSession.isActive(app, alarmId)

    /** Id các chuông đang reo hoặc đang hoãn (để đánh dấu trên danh sách). */
    fun lockedIds(): Set<Int> = RingSession.active(app).map { it.alarmId }.toSet()

    /** Lưu (thêm mới hoặc sửa) rồi đặt lịch reo và lời nhắc đi ngủ. Trả về chuông đã có id. */
    suspend fun save(alarm: AlarmEntity): AlarmEntity {
        val newId = db.alarmDao().upsert(alarm).toInt()
        val saved = if (alarm.id == 0) alarm.copy(id = newId) else alarm
        AlarmScheduler.schedule(app, saved)
        refreshReminder()
        NextAlarmWidget.refresh(app)
        return saved
    }

    suspend fun setEnabled(alarm: AlarmEntity, enabled: Boolean) {
        // Tắt chuông chợp mắt nghĩa là không ngủ trưa nữa: xóa luôn cho gọn danh sách.
        if (alarm.nap && !enabled) {
            delete(alarm)
            return
        }
        db.alarmDao().setEnabled(alarm.id, enabled)
        // Bật lại chuông thì bỏ luôn "bỏ qua lần reo tới" (bật lên là muốn chuông reo).
        if (enabled && alarm.skipUntil != 0L) db.alarmDao().setSkipUntil(alarm.id, 0L)
        AlarmScheduler.schedule(app, alarm.copy(enabled = enabled, skipUntil = if (enabled) 0L else alarm.skipUntil))
        refreshReminder()
        NextAlarmWidget.refresh(app)
    }

    /**
     * "Bỏ qua lần reo tới" cho chuông lặp: bấm lần nữa để hủy bỏ qua.
     * Trả về mốc đang bỏ qua (millis), 0 nếu vừa hủy bỏ qua, null nếu chuông không có lần reo kế tiếp.
     */
    suspend fun toggleSkip(alarm: AlarmEntity, now: Long = System.currentTimeMillis()): Long? {
        val until = if (alarm.skipUntil > now) 0L else AlarmScheduler.upcomingIgnoringSkip(alarm, now) ?: return null
        db.alarmDao().setSkipUntil(alarm.id, until)
        AlarmScheduler.schedule(app, alarm.copy(skipUntil = until))
        refreshReminder()
        NextAlarmWidget.refresh(app)
        return until
    }

    /**
     * Tạo chuông "Chợp mắt" reo sau minutes phút (đã cộng thời gian chìm vào giấc). Chỉ giữ một chuông chợp mắt:
     * tạo mới thì xóa chuông chợp mắt cũ. Trả về chuông đã lưu.
     */
    suspend fun createNap(minutes: Int, now: Long = System.currentTimeMillis()): AlarmEntity {
        db.alarmDao().getNaps().forEach { old ->
            if (!isLocked(old.id)) {
                AlarmScheduler.cancel(app, old.id)
                db.alarmDao().delete(old.id)
            }
        }
        return save(NapPlanner.alarmFor(minutes, now))
    }

    /** Xóa chuông chợp mắt sau khi đã reo xong (chuông thường thì không làm gì). */
    suspend fun deleteIfNap(alarmId: Int) {
        val a = db.alarmDao().getById(alarmId) ?: return
        if (a.nap && !isLocked(a.id)) delete(a)
    }

    suspend fun delete(alarm: AlarmEntity) {
        AlarmScheduler.cancel(app, alarm.id)
        db.alarmDao().delete(alarm.id)
        refreshReminder()
        NextAlarmWidget.refresh(app)
    }

    suspend fun addChallenge(type: ChallengeType, name: String, param: String) {
        db.challengeDao().insert(ChallengeItem(type = type.name, name = name, param = param))
    }

    /**
     * Xóa thử thách khỏi thư viện và bỏ nó khỏi mọi chuông, để không còn "id ma" chiếm chỗ trong 5 thử thách.
     * Trả về số chuông bị ảnh hưởng, hoặc null nếu không cho xóa vì đang dùng ở chuông đang reo/hoãn
     * (xóa mã QR lúc đang hoãn sẽ thành đường tắt để né thử thách).
     */
    suspend fun deleteChallenge(id: Int): Int? {
        val affected = db.alarmDao().getAll().filter { id in it.challengeIdList() }
        if (affected.any { isLocked(it.id) }) return null
        db.challengeDao().delete(id)
        affected.forEach { a ->
            db.alarmDao().upsert(a.copy(challengeIds = (a.challengeIdList() - id).joinToString(",")))
        }
        return affected.size
    }

    /** Ghi một lần dậy, trả về id dòng vừa ghi (để kiểm tra đã tỉnh đánh dấu "ngủ lại" nếu cần). */
    suspend fun insertWakeLog(log: WakeLog): Long = db.wakeLogDao().insert(log).also { NextAlarmWidget.refresh(app) }

    suspend fun markRelapsed(logId: Long) {
        if (logId > 0) db.wakeLogDao().markRelapsed(logId)
    }

    /**
     * Nạp lịch sử lúc mới cài app (InitialData gọi đúng một lần): tạo hai chuông "Đi học" (6:30, thứ Hai đến thứ Sáu,
     * 3 màn) và "Cuối tuần" (7:30 thứ Bảy) nếu chưa có, rồi ghi 30 ngày nhật ký thức dậy và giấc ngủ gắn với hai chuông đó.
     * Máy đã có từ 5 buổi sáng trở lên thì không nạp; ngày nào đã có nhật ký thật thì giữ nguyên ngày đó.
     * Trả về số buổi sáng đã ghi.
     */
    suspend fun seedHistory(now: Long = System.currentTimeMillis()): Int {
        // Bản cũ có nút tạo dữ liệu thử (alarmId = -100): dọn đi để không bị trùng ngày.
        if (LegacyDemo.present(app)) {
            val nights = LegacyDemo.nights(app)
            db.withTransaction {
                db.wakeLogDao().deleteByAlarm(DemoData.ALARM_ID)
                if (nights.isNotEmpty()) db.sleepLogDao().deleteNights(nights)
            }
            LegacyDemo.clear(app)
        }
        val existing = db.wakeLogDao().getSince(now - 31L * 24 * 3_600_000L)
        if (existing.size >= 5) return 0

        val alarms = db.alarmDao().getAll()
        val school = alarms.firstOrNull { it.label == DemoData.SCHOOL_LABEL } ?: save(
            AlarmEntity(
                hour = 6, minute = 30, daysMask = WEEKDAYS_MASK, label = DemoData.SCHOOL_LABEL,
                mathDifficulty = 2, mathCount = 3, challengeIds = DemoData.SCHOOL_CHALLENGES.joinToString(","),
                snoozeLimit = 1, rounds = 3, breakMinutes = 1, wakeCheckMin = 5,
                skipHolidays = true, // đi học: nghỉ lễ, nghỉ Tết thì không reo
            )
        )
        val weekend = alarms.firstOrNull { it.label == DemoData.WEEKEND_LABEL } ?: save(
            AlarmEntity(
                hour = 7, minute = 30, daysMask = SATURDAY_MASK, label = DemoData.WEEKEND_LABEL,
                mathDifficulty = 1, mathCount = 2, challengeIds = DemoData.WEEKEND_CHALLENGES.joinToString(","),
                snoozeLimit = 2, rounds = 1, breakMinutes = 1, wakeCheckMin = 3,
            )
        )

        val bundle = DemoData.generate(now, schoolAlarmId = school.id, weekendAlarmId = weekend.id)
        val usedDays = existing.map { SleepAnalyzer.dayStart(it.firedAt) }.toSet()
        val logs = bundle.wakeLogs.filter { SleepAnalyzer.dayStart(it.firedAt) !in usedDays }
        db.withTransaction {
            logs.forEach { db.wakeLogDao().insert(it) }
            // Đêm đã đo thật (bật "Truy cập dữ liệu sử dụng") thì giữ: bảng bỏ qua dòng trùng đêm.
            bundle.sleepLogs.forEach { db.sleepLogDao().insert(it) }
        }
        refreshReminder()
        return logs.size
    }

    /**
     * Đặt lại lịch reo của mọi chuông. Android xóa sạch báo thức của app khi app bị "Buộc dừng" (Oppo, Xiaomi tự làm
     * khi dọn RAM; Android Studio cũng làm khi bấm Run), nên mỗi lần mở app đều đặt lại cho chắc.
     */
    suspend fun rescheduleAll() {
        runCatching { AlarmScheduler.rescheduleAll(app) }
    }

    /** Đặt lại lời nhắc đi ngủ theo chuông kế tiếp (gọi khi mở app và sau mọi thay đổi chuông). */
    suspend fun refreshReminder() {
        runCatching { BedtimeReminder.refresh(app) }
    }
}

/** Hồ sơ người dùng, nhật ký giấc ngủ và quyền đọc giờ bật/tắt màn hình. */
class SleepRepository(context: Context) {
    private val app = context.applicationContext
    private val db = AppDatabase.get(app)

    private val _profile = MutableStateFlow(ProfileStore.load(app))
    val profile: StateFlow<UserProfile?> = _profile.asStateFlow()

    fun sleepLogsSince(since: Long): Flow<List<SleepLog>> = db.sleepLogDao().observeSince(since)

    suspend fun recentSleepLogs(since: Long): List<SleepLog> = db.sleepLogDao().getSince(since)

    fun supported(): Boolean = SleepTracker.supported()

    fun hasUsageAccess(): Boolean = SleepTracker.hasUsageAccess(app)

    fun openUsageAccess() = SleepTracker.openUsageAccess(app)

    /**
     * Hồ sơ đi kèm lịch sử nạp sẵn: chỉ ghi khi chưa có hồ sơ. Không chạy việc nặng nên gọi được trên luồng chính,
     * trước khi giao diện đọc hồ sơ. Trả về true nếu đã ghi.
     */
    fun seedProfileIfMissing(profile: UserProfile): Boolean {
        if (_profile.value != null) return false
        ProfileStore.save(app, profile)
        _profile.value = profile
        return true
    }

    suspend fun saveProfile(profile: UserProfile) {
        ProfileStore.save(app, profile)
        _profile.value = profile
        runCatching { BedtimeReminder.refresh(app) }
        backfill()
    }

    /** Tính bù những đêm còn thiếu (an toàn khi gọi nhiều lần). */
    suspend fun backfill() {
        runCatching { SleepTracker.backfill(app) }
    }
}

/** Thứ Bảy trong daysMask (bit 0 là thứ Hai). */
private const val SATURDAY_MASK = 0b0100000

/**
 * Dấu vết của nút "tạo dữ liệu thử" ở phiên bản trước: những đêm nó đã ghi (bảng sleep_logs không có cột đánh dấu).
 * Chỉ còn dùng để dọn sạch khi nâng cấp từ bản đó.
 */
private object LegacyDemo {
    private const val PREFS = "demo"

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun nights(context: Context): List<Long> =
        prefs(context).getString("nights", "").orEmpty().split(',').mapNotNull { it.trim().toLongOrNull() }

    fun present(context: Context): Boolean = prefs(context).getBoolean("present", false)

    fun clear(context: Context) {
        prefs(context).edit().clear().apply()
    }
}

/**
 * Nơi tạo và giữ một bản duy nhất của mỗi Repository (service locator đơn giản, thay cho Hilt/Koin
 * để project nhỏ gọn). ViewModel, Activity và Receiver đều lấy Repository từ đây.
 */
object AppGraph {
    @Volatile private var alarmRepo: AlarmRepository? = null
    @Volatile private var sleepRepo: SleepRepository? = null
    @Volatile private var familyRepo: FamilyRepository? = null

    fun alarms(context: Context): AlarmRepository =
        alarmRepo ?: synchronized(this) {
            alarmRepo ?: AlarmRepository(context.applicationContext).also { alarmRepo = it }
        }

    fun sleep(context: Context): SleepRepository =
        sleepRepo ?: synchronized(this) {
            sleepRepo ?: SleepRepository(context.applicationContext).also { sleepRepo = it }
        }

    fun family(context: Context): FamilyRepository =
        familyRepo ?: synchronized(this) {
            familyRepo ?: FamilyRepository(context.applicationContext).also { familyRepo = it }
        }
}
