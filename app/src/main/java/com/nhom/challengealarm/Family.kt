package com.nhom.challengealarm

import java.util.Base64
import java.util.Calendar
import java.util.TimeZone
import kotlin.math.roundToInt
import kotlin.random.Random

// =================================================================================================
// GIA ĐÌNH: quản lý giấc ngủ của bố mẹ lớn tuổi và con nhỏ từ điện thoại của mình.
//
// Vấn đề:
//  - Con đi làm xa, lo bố mẹ già ngủ ít, hay thức giấc, sáng không dậy (ở một mình, có chuyện không ai biết).
//  - Bố mẹ lo con thức khuya dùng điện thoại, ngủ không đủ 9 đến 11 tiếng, sáng không dậy nổi đi học.
//
// Cách làm (không cần máy chủ):
//  - Người quản lý thêm thành viên, đặt kế hoạch ngủ (giờ ngủ, giờ dậy, kiểu thử thách hợp lứa tuổi),
//    rồi gửi MÃ MỜI. Người kia mở app, nhập hoặc quét mã, ĐỒNG Ý chia sẻ thì app tự tạo chuông theo kế hoạch.
//  - Mỗi sáng người được quản lý gửi MÃ BÁO CÁO (7 đêm gần nhất) qua QR hoặc Zalo/Messenger; người quản lý nhập mã
//    là thấy giấc ngủ, giờ dậy và cảnh báo theo đúng độ tuổi.
//  - Người lớn tuổi có thể bật "Báo SMS": chuông reo mà N phút chưa tắt thì máy tự nhắn tin cho người thân.
//  - Bản thương mại sẽ thay mã bằng máy chủ (ví dụ Firebase) để đồng bộ tự động; phần logic ở đây giữ nguyên.
//
// File này là logic thuần (không phụ thuộc Android) để test được trên JVM.
// =================================================================================================

enum class FamilyRole { NONE, MANAGER, MEMBER }

enum class MemberKind(val label: String) {
    ELDER("Người lớn tuổi"),
    CHILD("Trẻ em"),
    ADULT("Người lớn"),
}

/** Bộ thử thách theo lứa tuổi. challengeIds là id thử thách có sẵn (xem BuiltIn trong Data.kt). */
enum class ChallengePreset(
    val label: String,
    val hint: String,
    val challengeIds: List<Int>,
    val difficulty: Int,
    val rounds: Int,
    val snooze: Int,
) {
    // -19 Đọc đồng hồ, 0 Giải toán, -12 Bật đèn
    GENTLE("Nhẹ nhàng", "Đọc đồng hồ, toán dễ, bật đèn. Không phải lắc máy hay chạy nhảy, hợp người lớn tuổi.", listOf(-19, 0, -12), 1, 1, 2),
    // -11 Lật cặp, -9 Bắt nắng, -14 Tìm ô khác màu
    PLAYFUL("Vui", "Lật cặp, bắt nắng, tìm ô khác màu. Như chơi trò chơi, hợp trẻ em.", listOf(-11, -9, -14), 1, 2, 1),
    // 0 Giải toán, -3 Gõ lại câu, -2 Đi bộ
    STANDARD("Tiêu chuẩn", "Giải toán, gõ lại câu, đi bộ.", listOf(0, -3, -2), 2, 3, 0),
}

/**
 * Kế hoạch ngủ người quản lý đặt cho một thành viên. Giờ tính bằng phút từ 0h.
 * checkInMin: chuông reo mà quá chừng này phút chưa tắt thì cảnh báo người quản lý (0 = không theo dõi).
 */
data class FamilyPlan(
    val bedtimeMin: Int,
    val wakeMin: Int,
    val daysMask: Int,
    val preset: ChallengePreset,
    val checkInMin: Int,
) {
    companion object {
        val CHECK_IN_OPTIONS = listOf(0, 15, 20, 30)

        fun defaultFor(kind: MemberKind): FamilyPlan = when (kind) {
            MemberKind.ELDER -> FamilyPlan(21 * 60 + 30, 5 * 60 + 30, ALL_DAYS_MASK, ChallengePreset.GENTLE, 20)
            MemberKind.CHILD -> FamilyPlan(21 * 60, 6 * 60 + 15, WEEKDAYS_MASK, ChallengePreset.PLAYFUL, 0)
            MemberKind.ADULT -> FamilyPlan(23 * 60, 6 * 60 + 30, WEEKDAYS_MASK, ChallengePreset.STANDARD, 0)
        }
    }
}

/**
 * Một đêm của thành viên (dữ liệu trong mã báo cáo). Trường null = không đo được.
 * nightOf = 0h của buổi sáng thức dậy. alarmAt = lúc chuông reo; dismissedAfterSec = bao lâu sau thì tắt
 * (null mà alarmAt có = chuông đang reo, chưa tắt).
 */
data class NightReport(
    val nightOf: Long,
    val sleepStart: Long?,
    val asleepMin: Int?,
    val interruptions: Int,
    val alarmAt: Long?,
    val dismissedAfterSec: Int?,
    val relapsed: Boolean = false,
)

data class FamilyMember(
    val id: String,
    val name: String,
    val relation: String,
    val kind: MemberKind,
    val birthYear: Int,
    val plan: FamilyPlan,
    val nights: List<NightReport> = emptyList(),
    /** Lần cuối nhận báo cáo (0 = chưa có). */
    val lastSync: Long = 0L,
    /** Người có sẵn khi cài app: mỗi sáng tự có đêm mới (FamilyDemo.refresh). Nhận báo cáo thật thì thôi. */
    val sample: Boolean = false,
    /** Người đó đã nhận lời mời (đã có báo cáo gửi về). */
    val paired: Boolean = false,
) {
    fun age(now: Long): Int = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.YEAR) - birthYear
    val latest: NightReport? get() = nights.maxByOrNull { it.nightOf }
}

enum class FamilyLevel { OK, WATCH, URGENT }

data class FamilyAlert(
    val memberId: String,
    val level: FamilyLevel,
    val title: String,
    val body: String,
)

private fun clockOf(t: Long, zone: TimeZone): String {
    val c = Calendar.getInstance(zone).apply { timeInMillis = t }
    return "%02d:%02d".format(c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE))
}

private fun minutesOfDay(t: Long, zone: TimeZone): Int {
    val c = Calendar.getInstance(zone).apply { timeInMillis = t }
    return c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
}

/**
 * Cảnh báo cho người quản lý, theo đúng độ tuổi (khuyến nghị NSF/AASM như SleepAdvisor):
 * người lớn tuổi ưu tiên an toàn (chưa tắt chuông, ngủ quá ít, thức giấc quá nhiều), trẻ em ưu tiên giờ ngủ và thời lượng.
 */
object FamilyAdvisor {
    /** Chưa có báo cáo mới quá chừng này thì nhắc người quản lý hỏi thăm. */
    const val STALE_MS = 36 * 3_600_000L
    const val ELDER_MAX_WAKINGS = 4
    const val CHILD_LATE_MIN = 30

    fun alerts(m: FamilyMember, now: Long, zone: TimeZone = TimeZone.getDefault()): List<FamilyAlert> {
        val out = mutableListOf<FamilyAlert>()
        val who = m.name
        val group = AgeGroup.of(m.age(now).coerceAtLeast(AgeGroup.MIN_AGE))
        val minMin = (group.minHours * 60).roundToInt()
        val maxMin = (group.maxHours * 60).roundToInt()
        val last = m.latest

        // 1. An toàn: chuông đã reo mà chưa tắt (quan trọng nhất với người lớn tuổi ở một mình).
        if (last?.alarmAt != null && last.dismissedAfterSec == null) {
            val waited = ((now - last.alarmAt) / 60_000L).toInt()
            val limit = if (m.plan.checkInMin > 0) m.plan.checkInMin else 30
            if (waited >= limit) {
                out += FamilyAlert(
                    m.id, if (m.kind == MemberKind.ELDER) FamilyLevel.URGENT else FamilyLevel.WATCH,
                    "$who chưa tắt chuông sau $waited phút",
                    "Chuông reo lúc ${clockOf(last.alarmAt, zone)} mà chưa ai tắt. " +
                        if (m.kind == MemberKind.ELDER) "Hãy gọi điện hỏi thăm $who ngay." else "Có thể $who còn đang ngủ.",
                )
            }
        }

        // 2. Lâu không có báo cáo mới.
        if (m.paired && m.lastSync > 0 && now - m.lastSync > STALE_MS) {
            out += FamilyAlert(
                m.id, FamilyLevel.WATCH, "Chưa có báo cáo mới từ $who",
                "Lần cuối nhận báo cáo cách đây ${(now - m.lastSync) / 3_600_000L} giờ. Nhắc $who gửi báo cáo hoặc gọi hỏi thăm.",
            )
        }

        val asleep = last?.asleepMin
        if (last != null && asleep != null) {
            // 3. Đêm qua ngủ quá ít so với lứa tuổi.
            if (asleep < minMin - 30) {
                out += FamilyAlert(
                    m.id, FamilyLevel.WATCH, "$who ngủ ít đêm qua",
                    "Ngủ ${SleepAdvisor.durationText(asleep)}, ít hơn mức ${group.rangeText} nên ngủ ở tuổi ${m.age(now)}.",
                )
            }
            // 4. Người lớn tuổi: ngủ quá nhiều hoặc thức giấc quá nhiều lần.
            if (m.kind == MemberKind.ELDER && asleep > maxMin + 90) {
                out += FamilyAlert(
                    m.id, FamilyLevel.WATCH, "$who ngủ nhiều bất thường",
                    "Ngủ ${SleepAdvisor.durationText(asleep)}. Người lớn tuổi ngủ quá nhiều kéo dài có thể là dấu hiệu sức khỏe, nên hỏi thăm.",
                )
            }
            if (m.kind == MemberKind.ELDER && last.interruptions >= ELDER_MAX_WAKINGS) {
                out += FamilyAlert(
                    m.id, FamilyLevel.WATCH, "$who thức giấc ${last.interruptions} lần đêm qua",
                    "Tuổi cao hay tỉnh giữa đêm là bình thường, nhưng thức nhiều lần liên tục nên hỏi bác sĩ " +
                        "(tiểu đêm, đau nhức, ngưng thở khi ngủ...).",
                )
            }
        }

        // 5. Trẻ em: ngủ muộn hơn giờ đã đặt (màn hình còn sáng sau giờ đi ngủ = còn dùng điện thoại).
        if (m.kind == MemberKind.CHILD && last?.sleepStart != null) {
            val bedClock = minutesOfDay(last.sleepStart, zone)
            // Đưa về cùng phía nửa đêm với giờ ngủ đặt (ví dụ 0:30 là muộn hơn 21:00 210 phút, không phải sớm hơn).
            var late = bedClock - m.plan.bedtimeMin
            if (late < -12 * 60) late += 24 * 60
            if (late > 12 * 60) late -= 24 * 60
            if (late >= CHILD_LATE_MIN) {
                out += FamilyAlert(
                    m.id, FamilyLevel.WATCH, "$who ngủ muộn $late phút",
                    "Giờ ngủ đã đặt ${SleepAdvisor.clockText(m.plan.bedtimeMin)} nhưng màn hình điện thoại còn sáng đến " +
                        "${clockOf(last.sleepStart, zone)}. Trẻ ${m.age(now)} tuổi cần ${group.rangeText} mỗi đêm.",
                )
            }
        }

        // 6. Cả tuần thiếu ngủ.
        val week = m.nights.sortedByDescending { it.nightOf }.take(7).mapNotNull { it.asleepMin }
        if (week.size >= 3 && week.average() < minMin) {
            out += FamilyAlert(
                m.id, FamilyLevel.WATCH, "$who thiếu ngủ cả tuần",
                "Trung bình ${SleepAdvisor.durationText(week.average().roundToInt())} mỗi đêm trong ${week.size} đêm gần nhất.",
            )
        }

        // 7. Tắt chuông xong lại ngủ tiếp.
        if (last?.relapsed == true) {
            out += FamilyAlert(m.id, FamilyLevel.WATCH, "$who tắt chuông xong lại ngủ tiếp", "Không trả lời câu hỏi \"Bạn còn thức không?\" sáng nay.")
        }
        return out.sortedByDescending { it.level.ordinal }
    }

    fun level(m: FamilyMember, now: Long, zone: TimeZone = TimeZone.getDefault()): FamilyLevel =
        alerts(m, now, zone).maxByOrNull { it.level.ordinal }?.level ?: FamilyLevel.OK
}

/** Mã trao đổi giữa hai điện thoại (QR hoặc gửi qua tin nhắn). */
sealed class FamilyCode {
    data class Invite(
        val memberId: String,
        val name: String,
        val relation: String,
        val kind: MemberKind,
        val birthYear: Int,
        val managerName: String,
        val plan: FamilyPlan,
    ) : FamilyCode()

    data class Report(
        val memberId: String,
        val name: String,
        val kind: MemberKind,
        val birthYear: Int,
        val sentAt: Long,
        val nights: List<NightReport>,
    ) : FamilyCode()

    data class PlanUpdate(
        val memberId: String,
        val managerName: String,
        val sentAt: Long,
        val plan: FamilyPlan,
    ) : FamilyCode()
}

/**
 * Mã dạng chữ "CA1:R:xxxx" (R = báo cáo, I = lời mời, P = kế hoạch). Phần sau là Base64 (an toàn trong URL và tin nhắn).
 * Thời gian tính bằng phút để mã ngắn. Có thể dán cả đoạn tin nhắn, app tự tìm mã bên trong.
 */
object FamilyCodec {
    private const val PREFIX = "CA1"
    private val TOKEN = Regex("""CA1:([IRPM]):([A-Za-z0-9_-]+)""")

    /** Bỏ ký tự dùng để phân tách khỏi tên. */
    fun clean(s: String): String = s.replace(Regex("""[|;,\n\r]"""), " ").trim().take(30)

    private fun b64(s: String): String = Base64.getUrlEncoder().withoutPadding().encodeToString(s.toByteArray(Charsets.UTF_8))
    private fun unb64(s: String): String = String(Base64.getUrlDecoder().decode(s), Charsets.UTF_8)

    private fun min(t: Long?): String = t?.let { (it / 60_000L).toString() } ?: "-"
    private fun fromMin(s: String): Long? = s.toLongOrNull()?.let { it * 60_000L }
    private fun int(s: String): Int? = s.toIntOrNull()

    fun planToString(p: FamilyPlan): String = listOf(p.bedtimeMin, p.wakeMin, p.daysMask, p.preset.name, p.checkInMin).joinToString(",")

    fun planFromString(s: String): FamilyPlan {
        val f = s.split(',')
        return FamilyPlan(
            bedtimeMin = f[0].toInt().coerceIn(0, 24 * 60 - 1),
            wakeMin = f[1].toInt().coerceIn(0, 24 * 60 - 1),
            daysMask = f[2].toInt() and ALL_DAYS_MASK,
            preset = runCatching { ChallengePreset.valueOf(f[3]) }.getOrDefault(ChallengePreset.STANDARD),
            checkInMin = f[4].toInt().coerceIn(0, 120),
        )
    }

    fun nightToString(n: NightReport): String = listOf(
        min(n.nightOf), min(n.sleepStart), n.asleepMin?.toString() ?: "-", n.interruptions.toString(),
        min(n.alarmAt), n.dismissedAfterSec?.toString() ?: "-", if (n.relapsed) "1" else "0",
    ).joinToString(",")

    fun nightFromString(s: String): NightReport {
        val f = s.split(',')
        return NightReport(
            nightOf = fromMin(f[0]) ?: 0L,
            sleepStart = fromMin(f[1]),
            asleepMin = int(f[2]),
            interruptions = int(f[3]) ?: 0,
            alarmAt = fromMin(f[4]),
            dismissedAfterSec = int(f[5]),
            relapsed = f.getOrNull(6) == "1",
        )
    }

    private fun nightsToString(nights: List<NightReport>) = nights.joinToString(";") { nightToString(it) }
    private fun nightsFromString(s: String) = if (s.isBlank()) emptyList() else s.split(';').map { nightFromString(it) }

    fun encode(code: FamilyCode): String = when (code) {
        is FamilyCode.Invite -> "$PREFIX:I:" + b64(
            listOf(
                code.memberId, clean(code.name), clean(code.relation), code.kind.name, code.birthYear,
                clean(code.managerName), planToString(code.plan),
            ).joinToString("|")
        )
        is FamilyCode.Report -> "$PREFIX:R:" + b64(
            listOf(
                code.memberId, clean(code.name), code.kind.name, code.birthYear, min(code.sentAt), nightsToString(code.nights),
            ).joinToString("|")
        )
        is FamilyCode.PlanUpdate -> "$PREFIX:P:" + b64(
            listOf(code.memberId, clean(code.managerName), min(code.sentAt), planToString(code.plan)).joinToString("|")
        )
    }

    /** Tìm và giải mã trong một đoạn chữ bất kỳ (tin nhắn dán vào, nội dung QR). null nếu không có mã hợp lệ. */
    fun decode(text: String): FamilyCode? = runCatching {
        val m = TOKEN.find(text.trim()) ?: return null
        val f = unb64(m.groupValues[2]).split('|')
        when (m.groupValues[1]) {
            "I" -> FamilyCode.Invite(
                memberId = f[0], name = f[1], relation = f[2], kind = MemberKind.valueOf(f[3]),
                birthYear = f[4].toInt(), managerName = f[5], plan = planFromString(f[6]),
            )
            "R" -> FamilyCode.Report(
                memberId = f[0], name = f[1], kind = MemberKind.valueOf(f[2]), birthYear = f[3].toInt(),
                sentAt = fromMin(f[4]) ?: 0L, nights = nightsFromString(f.getOrElse(5) { "" }),
            )
            "P" -> FamilyCode.PlanUpdate(memberId = f[0], managerName = f[1], sentAt = fromMin(f[2]) ?: 0L, plan = planFromString(f[3]))
            else -> null
        }
    }.getOrNull()

    /** Lưu một thành viên (danh sách của người quản lý) thành một dòng. */
    fun memberToString(m: FamilyMember): String = "$PREFIX:M:" + b64(
        listOf(
            m.id, clean(m.name), clean(m.relation), m.kind.name, m.birthYear, planToString(m.plan),
            min(m.lastSync), if (m.sample) "1" else "0", if (m.paired) "1" else "0", nightsToString(m.nights),
        ).joinToString("|")
    )

    fun memberFromString(s: String): FamilyMember? = runCatching {
        val m = TOKEN.find(s.trim()) ?: return null
        if (m.groupValues[1] != "M") return null
        val f = unb64(m.groupValues[2]).split('|')
        FamilyMember(
            id = f[0], name = f[1], relation = f[2], kind = MemberKind.valueOf(f[3]), birthYear = f[4].toInt(),
            plan = planFromString(f[5]), lastSync = fromMin(f[6]) ?: 0L, sample = f[7] == "1", paired = f[8] == "1",
            nights = nightsFromString(f.getOrElse(9) { "" }),
        )
    }.getOrNull()

    /** Mã định danh ngắn cho thành viên mới. */
    fun newId(random: Random = Random.Default): String {
        val chars = "abcdefghjkmnpqrstuvwxyz23456789"
        return (1..8).map { chars[random.nextInt(chars.length)] }.joinToString("")
    }
}

/**
 * Gia đình có sẵn khi cài app (InitialData): Ông nội 74 tuổi (ngủ ổn), Bà nội 70 tuổi (ngủ ít, hay thức giấc),
 * em gái Bé Na 9 tuổi (hay thức khuya dùng điện thoại). Mỗi người có một thói quen ngủ; mỗi sáng refresh() sinh thêm
 * đêm mới theo thói quen đó, giống như máy của người thân gửi báo cáo về. Kèm vài tình huống để xem trước cảnh báo.
 * Hàm thuần: cùng ngày thì cùng số liệu (mỗi đêm có seed riêng theo ngày), test được trên JVM.
 */
object FamilyDemo {
    const val GRANDPA = "fam-ong"
    const val GRANDMA = "fam-ba"
    const val KID = "fam-na"

    /** Thói quen ngủ: phút ngủ, số lần thức giấc, ngủ muộn hơn giờ đặt (phút), số giây để tắt chuông. */
    private class Habit(
        val asleep: IntRange,
        val wakings: IntRange,
        val bedLate: IntRange,
        val dismissSec: IntRange,
        val salt: Int,
        /** Báo cáo gửi về sau khi tắt chuông chừng này phút. */
        val reportDelayMin: Int,
    )

    private fun habitOf(id: String): Habit? = when (id) {
        GRANDPA -> Habit(425..465, 1..3, -10..15, 30..120, 11, 12)
        GRANDMA -> Habit(320..370, 3..5, 0..40, 20..90, 23, 25)
        KID -> Habit(470..520, 0..1, 35..100, 60..200, 37, 8)
        else -> null
    }

    private fun at(dayStart: Long, minutes: Int, zone: TimeZone): Long =
        Calendar.getInstance(zone).apply {
            timeInMillis = dayStart
            add(Calendar.MINUTE, minutes)
        }.timeInMillis

    private fun day(now: Long, back: Int, zone: TimeZone): Long =
        Calendar.getInstance(zone).apply {
            timeInMillis = SleepAnalyzer.dayStart(now, zone)
            add(Calendar.DAY_OF_YEAR, -back)
        }.timeInMillis

    /** Đêm mới nhất đã có báo cáo: sáng nay nếu đã qua giờ dậy, không thì sáng hôm qua. */
    private fun newestMorning(now: Long, plan: FamilyPlan, zone: TimeZone): Long {
        val today = day(now, 0, zone)
        return if (now >= at(today, plan.wakeMin, zone)) today else day(now, 1, zone)
    }

    private fun IntRange.pick(random: Random): Int = random.nextInt(first, last + 1)

    /** Một đêm (buổi sáng morning) theo thói quen; cùng ngày thì cùng kết quả. */
    private fun night(morning: Long, plan: FamilyPlan, habit: Habit, zone: TimeZone): NightReport {
        val random = Random(habit.salt * 1_000_003 + (morning / 3_600_000L).toInt())
        val alarmAt = at(morning, plan.wakeMin, zone)
        // Giờ ngủ đặt là buổi tối hôm trước (ví dụ 21:00), lệch thêm bedLate phút.
        val sleepStart = at(morning, plan.bedtimeMin - 24 * 60 + habit.bedLate.pick(random), zone)
        val w = habit.wakings.pick(random)
        val windowMin = ((alarmAt - sleepStart) / 60_000L).toInt()
        return NightReport(
            nightOf = morning,
            sleepStart = sleepStart,
            asleepMin = habit.asleep.pick(random).coerceAtMost(windowMin - w * 3),
            interruptions = w,
            alarmAt = alarmAt,
            dismissedAfterSec = habit.dismissSec.pick(random),
        )
    }

    /** 7 đêm gần nhất và giờ nhận báo cáo cuối (ngay sau khi tắt chuông sáng gần nhất). */
    private fun history(m: FamilyMember, habit: Habit, now: Long, zone: TimeZone): FamilyMember {
        val newest = newestMorning(now, m.plan, zone)
        val nights = (6 downTo 0).map { back ->
            val morning = Calendar.getInstance(zone).apply {
                timeInMillis = newest
                add(Calendar.DAY_OF_YEAR, -back)
            }.timeInMillis
            night(morning, m.plan, habit, zone)
        }
        val last = nights.last()
        val sentAt = (last.alarmAt ?: newest) + (last.dismissedAfterSec ?: 0) * 1000L + habit.reportDelayMin * 60_000L
        return m.copy(nights = nights, lastSync = minOf(sentAt, now), paired = true)
    }

    fun family(now: Long, zone: TimeZone = TimeZone.getDefault()): List<FamilyMember> {
        val year = Calendar.getInstance(zone).apply { timeInMillis = now }.get(Calendar.YEAR)
        val elder = FamilyPlan.defaultFor(MemberKind.ELDER)
        val people = listOf(
            FamilyMember(GRANDPA, "Ông nội", "Ông nội", MemberKind.ELDER, year - 74, elder, sample = true),
            FamilyMember(
                GRANDMA, "Bà nội", "Bà nội", MemberKind.ELDER, year - 70,
                elder.copy(bedtimeMin = 22 * 60, wakeMin = 5 * 60), sample = true,
            ),
            FamilyMember(KID, "Bé Na", "Em gái", MemberKind.CHILD, year - 9, FamilyPlan.defaultFor(MemberKind.CHILD), sample = true),
        )
        return people.map { refresh(it, now, zone, force = true) }
    }

    /**
     * Sáng mới thì thêm đêm mới cho người có sẵn (theo kế hoạch hiện tại của họ), như khi nhận báo cáo.
     * Chưa sang sáng mới, hoặc không phải người có sẵn, thì trả lại đúng đối tượng cũ.
     */
    fun refresh(m: FamilyMember, now: Long, zone: TimeZone = TimeZone.getDefault(), force: Boolean = false): FamilyMember {
        if (!m.sample) return m
        val habit = habitOf(m.id) ?: return m
        val newest = newestMorning(now, m.plan, zone)
        if (!force && (m.nights.maxOfOrNull { it.nightOf } ?: 0L) >= newest) return m
        return history(m, habit, now, zone)
    }

    /** Thay (hoặc thêm) đêm của buổi sáng hôm nay. */
    private fun withToday(m: FamilyMember, night: NightReport): FamilyMember =
        m.copy(nights = (m.nights.filter { it.nightOf != night.nightOf } + night).sortedBy { it.nightOf }.takeLast(7))

    /** Mô phỏng: chuông của người này đã reo (checkIn + 5) phút trước mà chưa tắt. */
    fun simulateMissedAlarm(m: FamilyMember, now: Long, zone: TimeZone = TimeZone.getDefault()): FamilyMember {
        val limit = if (m.plan.checkInMin > 0) m.plan.checkInMin else 20
        val today = SleepAnalyzer.dayStart(now, zone)
        val base = m.nights.firstOrNull { it.nightOf == today } ?: m.latest
        val night = NightReport(
            nightOf = today,
            sleepStart = base?.let { b -> b.sleepStart?.plus(today - b.nightOf) },
            asleepMin = base?.asleepMin,
            interruptions = base?.interruptions ?: 1,
            alarmAt = now - (limit + 5) * 60_000L,
            dismissedAfterSec = null,
        )
        return withToday(m, night).copy(lastSync = now)
    }

    /** Mô phỏng: đêm qua thức khuya dùng điện thoại, ngủ muộn hơn giờ đặt 95 phút. */
    fun simulateLateNight(m: FamilyMember, now: Long, zone: TimeZone = TimeZone.getDefault()): FamilyMember {
        val today = SleepAnalyzer.dayStart(now, zone)
        val sleepStart = at(today, m.plan.bedtimeMin - 24 * 60 + 95, zone)
        val alarmAt = at(today, m.plan.wakeMin, zone)
        val night = NightReport(
            nightOf = today,
            sleepStart = sleepStart,
            asleepMin = ((alarmAt - sleepStart) / 60_000L).toInt() - 5,
            interruptions = 1,
            alarmAt = alarmAt,
            dismissedAfterSec = 260,
        )
        return withToday(m, night).copy(lastSync = now)
    }

    /** Mô phỏng: một buổi sáng tốt (ngủ đủ, tắt chuông nhanh) để thấy cảnh báo biến mất. */
    fun simulateGoodMorning(m: FamilyMember, now: Long, zone: TimeZone = TimeZone.getDefault()): FamilyMember {
        val today = SleepAnalyzer.dayStart(now, zone)
        val group = AgeGroup.of(m.age(now).coerceAtLeast(AgeGroup.MIN_AGE))
        val asleep = (group.idealHours * 60).roundToInt()
        val alarmAt = at(today, m.plan.wakeMin, zone)
        val night = NightReport(
            nightOf = today,
            sleepStart = alarmAt - (asleep + 10) * 60_000L,
            asleepMin = asleep,
            interruptions = if (m.kind == MemberKind.ELDER) 2 else 0,
            alarmAt = alarmAt,
            dismissedAfterSec = 45,
        )
        return withToday(m, night).copy(lastSync = now)
    }
}
