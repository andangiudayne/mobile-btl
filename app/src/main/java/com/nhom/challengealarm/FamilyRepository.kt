package com.nhom.challengealarm

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.Calendar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Máy này đang được ai quản lý (chế độ người được quản lý). */
data class MemberLink(
    val memberId: String,
    val name: String,
    val relation: String,
    val kind: MemberKind,
    val birthYear: Int,
    val managerName: String,
    val plan: FamilyPlan,
    /** Id chuông "Gia đình đặt" app đã tạo theo kế hoạch (0 = chưa có). */
    val alarmId: Int,
    val joinedAt: Long,
)

/** Nhắn tin cho người thân khi chuông reo mà chưa tắt (chạy trên máy người được quản lý). */
data class SmsCheckIn(val enabled: Boolean = false, val phone: String = "", val minutes: Int = 20)

data class FamilyState(
    val role: FamilyRole = FamilyRole.NONE,
    /** Danh sách người được quản lý (chế độ người quản lý). */
    val members: List<FamilyMember> = emptyList(),
    /** Liên kết của máy này (chế độ người được quản lý). */
    val me: MemberLink? = null,
    val sms: SmsCheckIn = SmsCheckIn(),
)

/** Kết quả khi nhập một mã. */
sealed class ImportResult {
    data class ReportAdded(val member: FamilyMember, val isNew: Boolean) : ImportResult()
    data class InviteReceived(val invite: FamilyCode.Invite) : ImportResult()
    data class PlanApplied(val plan: FamilyPlan) : ImportResult()
    data class Error(val message: String) : ImportResult()
}

/**
 * Một tài khoản trên máy này. Mỗi tài khoản có vai trò, danh sách thành viên, liên kết riêng, nên một điện thoại
 * đóng được cả hai người (ví dụ "Con" chăm sóc và "Bố" được chăm sóc) để thử chế độ Gia đình khi không có máy thứ hai.
 */
data class FamilyAccount(val id: String, val name: String, val role: FamilyRole)

/** Các tình huống để xem trước cảnh báo trông thế nào. */
enum class FamilyScenario { MISSED_ALARM, LATE_NIGHT, GOOD_MORNING }

/**
 * Tầng Model của chế độ Gia đình: lưu vai trò, danh sách thành viên, liên kết với người quản lý (SharedPreferences),
 * tạo mã mời/kế hoạch/báo cáo, nhập mã, tạo chuông theo kế hoạch. Không có máy chủ: dữ liệu đi giữa hai máy bằng mã.
 */
class FamilyRepository(context: Context) {
    private val app = context.applicationContext
    private val accountPrefs = app.getSharedPreferences(ACCOUNTS_PREFS, Context.MODE_PRIVATE)
    private var currentId: String = accountPrefs.getString("current", DEFAULT_ACCOUNT) ?: DEFAULT_ACCOUNT

    /** Tài khoản mặc định dùng file "family" như bản trước (giữ nguyên dữ liệu cũ). */
    private fun prefsFor(id: String) =
        app.getSharedPreferences(if (id == DEFAULT_ACCOUNT) PREFS else "${PREFS}_$id", Context.MODE_PRIVATE)

    private var prefs = prefsFor(currentId)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<FamilyState> = _state.asStateFlow()

    private val _accounts = MutableStateFlow(readAccounts())
    /** Các tài khoản trên máy này (luôn có ít nhất tài khoản mặc định). */
    val accounts: StateFlow<List<FamilyAccount>> = _accounts.asStateFlow()

    private val _current = MutableStateFlow(currentId)
    val currentAccount: StateFlow<String> = _current.asStateFlow()

    private fun readAccounts(): List<FamilyAccount> {
        val stored = accountPrefs.getString("list", "").orEmpty().split('\n').filter { it.contains('|') }.map {
            val id = it.substringBefore('|')
            FamilyAccount(id, it.substringAfter('|'), roleOf(id))
        }
        return if (stored.none { it.id == DEFAULT_ACCOUNT }) {
            listOf(FamilyAccount(DEFAULT_ACCOUNT, "Tôi", roleOf(DEFAULT_ACCOUNT))) + stored
        } else {
            stored
        }
    }

    private fun roleOf(id: String): FamilyRole =
        runCatching { FamilyRole.valueOf(prefsFor(id).getString("role", null) ?: "NONE") }.getOrDefault(FamilyRole.NONE)

    private fun saveAccounts(list: List<FamilyAccount>) {
        accountPrefs.edit()
            .putString("list", list.joinToString("\n") { "${it.id}|${FamilyCodec.clean(it.name)}" })
            .putString("current", currentId)
            .apply()
        _accounts.value = list.map { it.copy(role = roleOf(it.id)) }
    }

    /** Đổi sang tài khoản khác trên máy này. */
    fun switchAccount(id: String) {
        if (_accounts.value.none { it.id == id }) return
        currentId = id
        prefs = prefsFor(id)
        _state.value = load()
        _current.value = id
        saveAccounts(_accounts.value)
    }

    /**
     * Tạo sẵn 2 tài khoản để thử trên một máy: "Con" (người chăm sóc, là tài khoản đang dùng) và "Bố" (được chăm sóc).
     * Dữ liệu của tài khoản "Bố" cũ (nếu có) bị xóa để bắt đầu lại từ đầu.
     */
    fun createTestPair() {
        prefsFor(TEST_ACCOUNT).edit().clear().commit()
        currentId = DEFAULT_ACCOUNT
        prefs = prefsFor(DEFAULT_ACCOUNT)
        _state.value = load()
        _current.value = DEFAULT_ACCOUNT
        saveAccounts(listOf(FamilyAccount(DEFAULT_ACCOUNT, "Con", FamilyRole.NONE), FamilyAccount(TEST_ACCOUNT, "Bố", FamilyRole.NONE)))
        update { it.copy(role = FamilyRole.MANAGER) }
    }

    /** Bỏ các tài khoản thử, chỉ giữ tài khoản mặc định. */
    fun removeTestAccounts() {
        _accounts.value.filter { it.id != DEFAULT_ACCOUNT }.forEach { prefsFor(it.id).edit().clear().commit() }
        currentId = DEFAULT_ACCOUNT
        prefs = prefsFor(DEFAULT_ACCOUNT)
        _state.value = load()
        _current.value = DEFAULT_ACCOUNT
        saveAccounts(listOf(FamilyAccount(DEFAULT_ACCOUNT, "Tôi", roleOf(DEFAULT_ACCOUNT))))
    }

    fun currentAccountName(): String = _accounts.value.firstOrNull { it.id == currentId }?.name.orEmpty()

    private fun load(): FamilyState {
        val role = runCatching { FamilyRole.valueOf(prefs.getString("role", null) ?: "NONE") }.getOrDefault(FamilyRole.NONE)
        val members = prefs.getString("members", "").orEmpty()
            .split('\n').filter { it.isNotBlank() }.mapNotNull { FamilyCodec.memberFromString(it) }
        val me = (FamilyCodec.decode(prefs.getString("link", "").orEmpty()) as? FamilyCode.Invite)?.let {
            MemberLink(
                it.memberId, it.name, it.relation, it.kind, it.birthYear, it.managerName, it.plan,
                alarmId = prefs.getInt("linkAlarmId", 0), joinedAt = prefs.getLong("linkJoinedAt", 0L),
            )
        }
        val sms = SmsCheckIn(
            enabled = prefs.getBoolean("smsEnabled", false),
            phone = prefs.getString("smsPhone", "").orEmpty(),
            minutes = prefs.getInt("smsMinutes", 20),
        )
        return FamilyState(role, members, me, sms)
    }

    private fun persist(s: FamilyState) {
        val e = prefs.edit()
            .putString("role", s.role.name)
            .putString("members", s.members.joinToString("\n") { FamilyCodec.memberToString(it) })
            .putBoolean("smsEnabled", s.sms.enabled)
            .putString("smsPhone", s.sms.phone)
            .putInt("smsMinutes", s.sms.minutes)
        val me = s.me
        if (me == null) {
            e.remove("link").remove("linkAlarmId").remove("linkJoinedAt")
        } else {
            e.putString(
                "link",
                FamilyCodec.encode(FamilyCode.Invite(me.memberId, me.name, me.relation, me.kind, me.birthYear, me.managerName, me.plan)),
            )
            e.putInt("linkAlarmId", me.alarmId).putLong("linkJoinedAt", me.joinedAt)
        }
        e.apply()
        _state.value = s
        // Vai trò hiện trên thanh chọn tài khoản phải theo kịp.
        if (_accounts.value.any { it.id == currentId && it.role != s.role }) {
            _accounts.value = _accounts.value.map { if (it.id == currentId) it.copy(role = s.role) else it }
        }
    }

    private fun update(change: (FamilyState) -> FamilyState) = persist(change(_state.value))

    fun setRole(role: FamilyRole) = update { it.copy(role = role) }

    // ------------------------------------------------------------------ người quản lý

    fun addMember(name: String, relation: String, kind: MemberKind, birthYear: Int): FamilyMember {
        val m = FamilyMember(
            id = FamilyCodec.newId(),
            name = FamilyCodec.clean(name).ifBlank { relation },
            relation = FamilyCodec.clean(relation),
            kind = kind,
            birthYear = birthYear,
            plan = FamilyPlan.defaultFor(kind),
        )
        update { it.copy(role = FamilyRole.MANAGER, members = it.members + m) }
        return m
    }

    fun updateMember(m: FamilyMember) = update { s -> s.copy(members = s.members.map { if (it.id == m.id) m else it }) }

    fun removeMember(id: String) = update { s -> s.copy(members = s.members.filterNot { it.id == id }) }

    /**
     * Gia đình có sẵn khi cài app (Ông nội, Bà nội, Bé Na; xem FamilyDemo). Chỉ thêm khi máy chưa dùng chế độ Gia đình.
     * Trả về true nếu đã thêm.
     */
    fun seedIfEmpty(now: Long = System.currentTimeMillis()): Boolean {
        val s = _state.value
        if (s.role != FamilyRole.NONE || s.members.isNotEmpty() || s.me != null || _accounts.value.size > 1) return false
        persist(s.copy(role = FamilyRole.MANAGER, members = FamilyDemo.family(now)))
        return true
    }

    /** Sáng mới: người có sẵn có thêm đêm mới (như nhận báo cáo), để thẻ gia đình luôn là số liệu mới nhất. */
    fun refreshDaily(now: Long = System.currentTimeMillis()) {
        val s = _state.value
        if (s.role != FamilyRole.MANAGER || s.members.none { it.sample }) return
        val members = s.members.map { FamilyDemo.refresh(it, now) }
        if (members.indices.any { members[it] !== s.members[it] }) persist(s.copy(members = members))
    }

    /** "Xem trước cảnh báo": dựng một tình huống trên một người. Trả về người đó sau khi đổi (null nếu không tìm thấy). */
    fun simulate(id: String, scenario: FamilyScenario, now: Long = System.currentTimeMillis()): FamilyMember? {
        val m = _state.value.members.firstOrNull { it.id == id } ?: return null
        val changed = when (scenario) {
            FamilyScenario.MISSED_ALARM -> FamilyDemo.simulateMissedAlarm(m, now)
            FamilyScenario.LATE_NIGHT -> FamilyDemo.simulateLateNight(m, now)
            FamilyScenario.GOOD_MORNING -> FamilyDemo.simulateGoodMorning(m, now)
        }
        updateMember(changed)
        return changed
    }

    fun inviteCode(m: FamilyMember, managerName: String): String =
        FamilyCodec.encode(FamilyCode.Invite(m.id, m.name, m.relation, m.kind, m.birthYear, managerName, m.plan))

    fun planCode(m: FamilyMember, managerName: String, now: Long = System.currentTimeMillis()): String =
        FamilyCodec.encode(FamilyCode.PlanUpdate(m.id, managerName, now, m.plan))

    // ------------------------------------------------------------------ nhập mã

    /** Nhập mã (dán từ tin nhắn hoặc quét QR). Lời mời chưa áp dụng ngay: giao diện phải hỏi đồng ý trước. */
    suspend fun importCode(text: String, now: Long = System.currentTimeMillis()): ImportResult {
        val code = FamilyCodec.decode(text) ?: return ImportResult.Error("Không tìm thấy mã hợp lệ. Mã bắt đầu bằng \"CA1:\".")
        val s = _state.value
        return when (code) {
            is FamilyCode.Report -> {
                if (s.role == FamilyRole.MEMBER) {
                    return ImportResult.Error("Máy này đang ở chế độ được quản lý nên không nhận báo cáo.")
                }
                val existing = s.members.firstOrNull { it.id == code.memberId }
                val merged = if (existing != null) {
                    // Đêm mới ghi đè đêm cũ cùng ngày; giữ 14 đêm gần nhất.
                    val byNight = (existing.nights + code.nights).associateBy { it.nightOf }
                    existing.copy(
                        nights = byNight.values.sortedBy { it.nightOf }.takeLast(14),
                        lastSync = maxOf(code.sentAt, existing.lastSync),
                        paired = true,
                        kind = code.kind,
                        birthYear = code.birthYear,
                        sample = false, // đã có báo cáo thật: thôi tự thêm đêm
                    )
                } else {
                    FamilyMember(
                        id = code.memberId, name = code.name, relation = code.name, kind = code.kind,
                        birthYear = code.birthYear, plan = FamilyPlan.defaultFor(code.kind),
                        nights = code.nights.sortedBy { it.nightOf }, lastSync = code.sentAt, paired = true,
                    )
                }
                update { st ->
                    st.copy(
                        role = FamilyRole.MANAGER,
                        members = if (existing != null) st.members.map { if (it.id == merged.id) merged else it } else st.members + merged,
                    )
                }
                FamilyNotifier.notifyUrgent(app, merged, now)
                ImportResult.ReportAdded(merged, existing == null)
            }
            is FamilyCode.Invite -> {
                if (s.role == FamilyRole.MANAGER && s.members.isNotEmpty()) {
                    ImportResult.Error("Máy này đang quản lý gia đình. Muốn được quản lý thì vào \"Đổi vai trò\" trước.")
                } else {
                    ImportResult.InviteReceived(code)
                }
            }
            is FamilyCode.PlanUpdate -> {
                val me = s.me
                if (s.role != FamilyRole.MEMBER || me == null || me.memberId != code.memberId) {
                    ImportResult.Error("Mã kế hoạch này dành cho người khác, hoặc máy này chưa nhận lời mời.")
                } else {
                    applyPlan(me.copy(plan = code.plan, managerName = code.managerName))
                    ImportResult.PlanApplied(code.plan)
                }
            }
        }
    }

    // ------------------------------------------------------------------ người được quản lý

    /** Đồng ý lời mời: chuyển sang chế độ được quản lý và tạo chuông theo kế hoạch. */
    suspend fun acceptInvite(invite: FamilyCode.Invite, now: Long = System.currentTimeMillis()) {
        val old = _state.value
        val link = MemberLink(
            invite.memberId, invite.name, invite.relation, invite.kind, invite.birthYear, invite.managerName, invite.plan,
            alarmId = old.me?.alarmId ?: 0, joinedAt = now,
        )
        update {
            it.copy(
                role = FamilyRole.MEMBER,
                members = it.members.filterNot { m -> m.sample },
                sms = it.sms.copy(minutes = if (invite.plan.checkInMin > 0) invite.plan.checkInMin else it.sms.minutes),
            )
        }
        // Chưa có hồ sơ thì tạo theo lời mời để gợi ý giấc ngủ đúng tuổi.
        val sleepRepo = AppGraph.sleep(app)
        if (sleepRepo.profile.value == null) {
            sleepRepo.saveProfile(
                UserProfile(
                    name = invite.name,
                    birthYear = invite.birthYear,
                    occupation = when (invite.kind) {
                        MemberKind.CHILD -> Occupation.SCHOOL
                        MemberKind.ELDER -> Occupation.HOME
                        MemberKind.ADULT -> Occupation.OFFICE
                    },
                    activity = ActivityLevel.MEDIUM,
                    chronotype = Chronotype.NEUTRAL,
                    lateCaffeine = false,
                    nightStartMin = AgeGroup.of((Calendar.getInstance().get(Calendar.YEAR) - invite.birthYear)
                        .coerceAtLeast(AgeGroup.MIN_AGE)).defaultNightStart,
                    remind = true,
                )
            )
        }
        applyPlan(link)
    }

    /** Tạo hoặc sửa chuông "Gia đình đặt" theo kế hoạch, rồi lưu liên kết. */
    private suspend fun applyPlan(link: MemberLink) {
        val plan = link.plan
        val alarms = AppGraph.alarms(app)
        val existing = if (link.alarmId > 0) alarms.alarm(link.alarmId) else null
        val base = existing ?: AlarmEntity(hour = plan.wakeMin / 60, minute = plan.wakeMin % 60)
        val saved = alarms.save(
            base.copy(
                hour = plan.wakeMin / 60,
                minute = plan.wakeMin % 60,
                daysMask = if (plan.daysMask == 0) ALL_DAYS_MASK else plan.daysMask,
                enabled = true,
                label = LABEL,
                mathDifficulty = plan.preset.difficulty,
                mathCount = if (plan.preset.difficulty <= 1) 2 else 3,
                challengeIds = plan.preset.challengeIds.joinToString(","),
                snoozeLimit = plan.preset.snooze,
                rounds = plan.preset.rounds,
                breakMinutes = 1,
                skipUntil = 0L,
                wakeCheckMin = if (link.kind == MemberKind.CHILD) 5 else 10,
            )
        )
        update { it.copy(role = FamilyRole.MEMBER, me = link.copy(alarmId = saved.id)) }
    }

    /** Ngừng chia sẻ: về chế độ thường. Chuông đã tạo vẫn giữ (người dùng tự xóa nếu muốn). */
    fun leave() = update { it.copy(role = FamilyRole.NONE, me = null, sms = it.sms.copy(enabled = false)) }

    fun setSms(sms: SmsCheckIn) = update { it.copy(sms = sms.copy(phone = sms.phone.filter { c -> c.isDigit() || c == '+' })) }

    /** Mã báo cáo 7 đêm gần nhất của chính máy này (từ nhật ký thức dậy và giấc ngủ). */
    suspend fun myReportCode(now: Long = System.currentTimeMillis()): String? {
        val me = _state.value.me ?: return null
        val since = now - 8L * 24 * 3_600_000L
        val sleeps = AppGraph.sleep(app).recentSleepLogs(since).associateBy { it.nightOf }
        val wakes = AppGraph.alarms(app).recentWakeLogs(since)
            .groupBy { SleepAnalyzer.dayStart(it.firedAt) }
            .mapValues { (_, l) -> l.minByOrNull { it.firedAt }!! }
        val days = (sleeps.keys + wakes.keys).sorted().takeLast(7)
        val nights = days.map { day ->
            val s = sleeps[day]
            val w = wakes[day]
            NightReport(
                nightOf = day,
                sleepStart = s?.sleepStart,
                asleepMin = s?.asleepMin,
                interruptions = s?.interruptions ?: 0,
                alarmAt = w?.firedAt,
                dismissedAfterSec = w?.let { ((it.awakeAt - it.firedAt) / 1000L).toInt().coerceAtLeast(0) },
                relapsed = w?.relapsed == true,
            )
        }.toMutableList()
        // Chuông sáng nay đang reo (chưa tắt): báo luôn để người quản lý biết.
        val ringing = RingSession.active(app).firstOrNull { it.alarmId == me.alarmId }
        if (ringing != null) {
            val day = SleepAnalyzer.dayStart(ringing.firedAt)
            if (nights.none { it.nightOf == day && it.alarmAt != null }) {
                nights.removeAll { it.nightOf == day }
                nights += NightReport(day, sleeps[day]?.sleepStart, sleeps[day]?.asleepMin, sleeps[day]?.interruptions ?: 0, ringing.firedAt, null)
            }
        }
        return FamilyCodec.encode(FamilyCode.Report(me.memberId, me.name, me.kind, me.birthYear, now, nights.sortedBy { it.nightOf }))
    }

    companion object {
        private const val PREFS = "family"
        private const val ACCOUNTS_PREFS = "family_accounts"
        const val DEFAULT_ACCOUNT = "main"
        const val TEST_ACCOUNT = "bo"
        const val LABEL = "Gia đình đặt"
    }
}

/** Thông báo cho người quản lý khi có cảnh báo khẩn cấp (nhập báo cáo hoặc mô phỏng). */
object FamilyNotifier {
    private const val CHANNEL_ID = "family"

    fun notifyUrgent(context: Context, m: FamilyMember, now: Long) {
        val urgent = FamilyAdvisor.alerts(m, now).firstOrNull { it.level == FamilyLevel.URGENT } ?: return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Gia đình", NotificationManager.IMPORTANCE_HIGH))
        }
        val open = PendingIntent.getActivity(
            context, 600_000, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(urgent.title)
            .setContentText(urgent.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(urgent.body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { nm.notify(600_000 + (m.id.hashCode() and 0xffff), n) }
    }
}

/**
 * Báo SMS cho người thân: trên máy người được quản lý, khi chuông reo app hẹn một lần kiểm tra sau N phút;
 * lúc đó chuông vẫn chưa tắt (phiên reo còn) thì tự nhắn tin cho số điện thoại đã đặt. Không cần mạng hay máy chủ.
 */
object CheckIn {
    private const val REQUEST_BASE = 500_000
    const val EXTRA_ALARM_ID = "checkin_alarm"
    const val EXTRA_FIRED_AT = "checkin_fired"

    fun canSend(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    /** Gọi khi chuông bắt đầu reo (AlarmService). */
    fun onAlarmStarted(context: Context, session: RingSessionState) {
        if (session.alarmId < 0 || session.recheck) return
        val s = AppGraph.family(context).state.value
        if (s.role != FamilyRole.MEMBER || s.me == null || !s.sms.enabled || s.sms.phone.isBlank() || !canSend(context)) return
        val at = maxOf(System.currentTimeMillis() + 5_000L, session.firedAt + s.sms.minutes * 60_000L)
        val intent = Intent(context, CheckInReceiver::class.java)
            .putExtra(EXTRA_ALARM_ID, session.alarmId)
            .putExtra(EXTRA_FIRED_AT, session.firedAt)
        val pi = PendingIntent.getBroadcast(
            context, REQUEST_BASE + session.alarmId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        if (AlarmScheduler.canScheduleExact(context)) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    fun message(name: String, firedAt: Long, now: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = firedAt }
        val clock = "%02d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
        return "[Challenge Alarm] $name chưa tắt chuông báo thức lúc $clock (đã ${(now - firedAt) / 60_000L} phút). " +
            "Hãy gọi điện hỏi thăm."
    }

    fun send(context: Context, phone: String, text: String): Boolean = runCatching {
        val sms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }
        sms.sendMultipartTextMessage(phone, null, sms.divideMessage(text), null, null)
        true
    }.getOrDefault(false)
}

class CheckInReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getIntExtra(CheckIn.EXTRA_ALARM_ID, -1)
        val firedAt = intent.getLongExtra(CheckIn.EXTRA_FIRED_AT, 0L)
        val session = RingSession.load(context, alarmId) ?: return // đã tắt chuông: không nhắn
        if (session.firedAt != firedAt) return // đây là lần reo khác
        val s = AppGraph.family(context).state.value
        val me = s.me ?: return
        if (!s.sms.enabled || s.sms.phone.isBlank() || !CheckIn.canSend(context)) return
        CheckIn.send(context, s.sms.phone, CheckIn.message(me.name, firedAt, System.currentTimeMillis()))
    }
}
