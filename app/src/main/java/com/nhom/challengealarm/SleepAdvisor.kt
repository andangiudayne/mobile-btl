package com.nhom.challengealarm

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class Occupation(val label: String) {
    SCHOOL("Học sinh"),
    UNIVERSITY("Sinh viên"),
    OFFICE("Đi làm giờ hành chính"),
    SHIFT("Làm ca, làm đêm"),
    HOME("Nghỉ hưu, ở nhà"),
}

enum class ActivityLevel(val label: String) { LOW("Ít"), MEDIUM("Vừa"), HIGH("Nhiều") }

enum class Chronotype(val label: String) { LARK("Dậy sớm"), NEUTRAL("Bình thường"), OWL("Cú đêm") }

/**
 * Hồ sơ người dùng (lưu trên máy). nightStartMin = giờ bắt đầu tính giấc ngủ, tính bằng phút từ 0h.
 * remind = có nhắc đi ngủ hay không.
 */
data class UserProfile(
    val name: String,
    val birthYear: Int,
    val occupation: Occupation,
    val activity: ActivityLevel,
    val chronotype: Chronotype,
    val lateCaffeine: Boolean,
    val nightStartMin: Int,
    val remind: Boolean,
) {
    fun age(now: Long = System.currentTimeMillis()): Int =
        Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.YEAR) - birthYear
}

/**
 * Nhóm tuổi và số giờ ngủ khuyến nghị mỗi đêm.
 * Nguồn: National Sleep Foundation (Hirshkowitz và cộng sự, Sleep Health 2015);
 * AASM (Paruthi 2016 cho trẻ em và thiếu niên, Watson 2015 cho người lớn).
 * idealHours là mục tiêu mặc định trong khoảng khuyến nghị; defaultNightStart là giờ bắt đầu tính giấc ngủ.
 */
enum class AgeGroup(
    val label: String,
    val maxAge: Int,
    val minHours: Float,
    val maxHours: Float,
    val idealHours: Float,
    val defaultNightStart: Int,
) {
    CHILD("6–13 tuổi", 13, 9f, 11f, 10f, 19 * 60 + 30),
    TEEN("14–17 tuổi", 17, 8f, 10f, 9f, 21 * 60),
    YOUNG("18–25 tuổi", 25, 7f, 9f, 8f, 21 * 60 + 30),
    ADULT("26–64 tuổi", 64, 7f, 9f, 7.5f, 21 * 60),
    SENIOR("65 tuổi trở lên", Int.MAX_VALUE, 7f, 8f, 7.5f, 20 * 60);

    val rangeText: String get() = "${fmtHours(minHours)}–${fmtHours(maxHours)} tiếng"

    companion object {
        const val MIN_AGE = 6
        fun of(age: Int): AgeGroup = values().first { age <= it.maxAge }
    }
}

private fun fmtHours(h: Float): String = if (h % 1f == 0f) h.toInt().toString() else "%.1f".format(h).replace('.', ',')

/** Kế hoạch cho một đêm, tính từ giờ chuông kế tiếp. */
data class SleepPlan(
    val targetMin: Int,
    val bedtime: Long,
    val latestBedtime: Long,
    val screensOff: Long,
    val caffeineCutoff: Long,
)

data class Advice(val title: String, val body: String, val warn: Boolean)

/** Tổng hợp các đêm gần nhất. avgBedClockMin là giờ ngủ trung bình (phút từ 0h). */
data class SleepReport(
    val nights: Int,
    val avgAsleepMin: Int?,
    val avgBedClockMin: Int?,
    val bedSpreadMin: Int?,
    val avgInterruptions: Float?,
    val advice: List<Advice>,
    /** Điểm chất lượng trung bình 0 đến 100 (xem SleepAdvisor.nightQuality), null nếu chưa có đêm nào. */
    val qualityPct: Int? = null,
    /** Nợ ngủ 7 ngày qua (phút), null nếu chưa có đêm nào. Xem SleepAdvisor.sleepDebtMin. */
    val debtMin: Int? = null,
)

/** Gợi ý giấc ngủ theo tuổi và thói quen. Hàm thuần, test được trên JVM. */
object SleepAdvisor {
    /** Thời gian trung bình để chìm vào giấc ngủ (người khỏe mạnh khoảng 10 đến 20 phút). */
    const val FALL_ASLEEP_MIN = 15
    const val SCREENS_OFF_BEFORE_MIN = 60
    const val CAFFEINE_BEFORE_MIN = 6 * 60
    const val MIN_NIGHTS_FOR_ADVICE = 3
    /** Mục tiêu dùng khi chưa có hồ sơ (người lớn: 7,5 tiếng). */
    const val DEFAULT_TARGET_MIN = 450
    /** Nợ ngủ từ mức này trở lên thì đưa vào lời khuyên. */
    const val DEBT_WARN_MIN = 120
    const val DEBT_DAYS = 7

    fun targetMinutes(profile: UserProfile, age: Int): Int {
        val g = AgeGroup.of(age)
        var target = g.idealHours * 60
        // Vận động nhiều cần hồi phục nhiều hơn, nhưng không vượt mức trên của khuyến nghị.
        if (profile.activity == ActivityLevel.HIGH) target += 30
        return target.coerceIn(g.minHours * 60, g.maxHours * 60).roundToInt()
    }

    /**
     * Điểm chất lượng một đêm (0 đến 100): ngủ đủ thời lượng mục tiêu được tối đa 100,
     * mỗi lần thức dậy dùng điện thoại (từ 1 phút trở lên) trừ 6 điểm.
     */
    fun nightQuality(night: SleepNight, targetMin: Int): Int {
        val asleepMin = night.asleepMs / 60_000.0
        val base = (asleepMin / targetMin.coerceAtLeast(1)).coerceAtMost(1.0) * 100.0
        return (base - 6.0 * night.interruptions).roundToInt().coerceIn(0, 100)
    }

    /**
     * Nợ ngủ trong `days` ngày gần nhất: cộng phần ngủ thiếu so với mục tiêu của từng đêm đo được.
     * Đêm ngủ dư không trừ vào nợ (ngủ bù một đêm không xóa được thiếu ngủ cộng dồn). null nếu không có đêm nào.
     */
    fun sleepDebtMin(nights: List<SleepNight>, targetMin: Int, now: Long, days: Int = DEBT_DAYS): Int? {
        val since = now - days * 24 * 3_600_000L
        val recent = nights.filter { it.wakeAt in since..now }
        if (recent.isEmpty()) return null
        return recent.sumOf { (targetMin - (it.asleepMs / 60_000L).toInt()).coerceAtLeast(0) }
    }

    fun plan(profile: UserProfile, age: Int, wakeAt: Long): SleepPlan {
        val g = AgeGroup.of(age)
        val target = targetMinutes(profile, age)
        val bedtime = wakeAt - (target + FALL_ASLEEP_MIN) * 60_000L
        val latest = wakeAt - ((g.minHours * 60).roundToInt() + FALL_ASLEEP_MIN) * 60_000L
        return SleepPlan(
            targetMin = target,
            bedtime = bedtime,
            latestBedtime = latest,
            screensOff = bedtime - SCREENS_OFF_BEFORE_MIN * 60_000L,
            caffeineCutoff = bedtime - CAFFEINE_BEFORE_MIN * 60_000L,
        )
    }

    /** Đổi giờ trong ngày sang "phút kể từ 12h trưa" để 23:30 và 0:30 nằm cạnh nhau khi lấy trung bình. */
    private fun minutesFromNoon(t: Long, zone: TimeZone): Int {
        val c = Calendar.getInstance(zone).apply { timeInMillis = t }
        val m = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        return (m - 12 * 60 + 24 * 60) % (24 * 60)
    }

    private fun clockFromNoon(m: Int): Int = (m + 12 * 60) % (24 * 60)

    fun clockText(clockMin: Int): String = "%02d:%02d".format(clockMin / 60, clockMin % 60)

    fun durationText(min: Int): String {
        val h = min / 60
        val m = min % 60
        return when {
            h == 0 -> "$m phút"
            m == 0 -> "$h tiếng"
            else -> "$h giờ $m phút"
        }
    }

    fun report(
        nights: List<SleepNight>,
        profile: UserProfile,
        age: Int,
        zone: TimeZone = TimeZone.getDefault(),
        // Mặc định tính nợ ngủ đến đêm gần nhất (để kết quả không phụ thuộc giờ chạy); ViewModel truyền giờ hiện tại.
        now: Long = nights.maxOfOrNull { it.wakeAt } ?: System.currentTimeMillis(),
    ): SleepReport {
        val g = AgeGroup.of(age)
        val advice = mutableListOf<Advice>()
        val recent = nights.sortedByDescending { it.wakeAt }.take(7)
        val debt = sleepDebtMin(nights, targetMinutes(profile, age), now)

        if (recent.size < MIN_NIGHTS_FOR_ADVICE) {
            advice += Advice(
                "Đang thu thập dữ liệu",
                "Cần ít nhất $MIN_NIGHTS_FOR_ADVICE đêm có chuông để đánh giá. Đã có ${recent.size} đêm.",
                false,
            )
            advice += ageTips(g)
            advice += habitTips(profile, null)
            val q = recent.map { nightQuality(it, targetMinutes(profile, age)) }.takeIf { it.isNotEmpty() }?.average()?.roundToInt()
            return SleepReport(recent.size, recent.avgAsleep(), null, null, null, advice, q, debt)
        }

        val avgAsleep = recent.avgAsleep()!!
        val bedFromNoon = recent.map { minutesFromNoon(it.sleepStart, zone) }
        val avgBedNoon = bedFromNoon.average()
        val spread = sqrt(bedFromNoon.map { (it - avgBedNoon) * (it - avgBedNoon) }.average()).roundToInt()
        val avgBedClock = clockFromNoon(avgBedNoon.roundToInt())
        val avgWakeNoon = recent.map { minutesFromNoon(it.wakeAt, zone) }.average()
        val avgInterruptions = recent.map { it.interruptions }.average().toFloat()

        // 1. Thời lượng so với khuyến nghị của nhóm tuổi.
        val minMin = (g.minHours * 60).roundToInt()
        val maxMin = (g.maxHours * 60).roundToInt()
        when {
            avgAsleep < minMin -> advice += Advice(
                "Bạn đang thiếu ngủ",
                "Trung bình bạn ngủ ${durationText(avgAsleep)} mỗi đêm, ít hơn mức tối thiểu ${g.minHours.toInt()} tiếng " +
                    "khuyến nghị cho người ${g.label}. Mỗi đêm thiếu khoảng ${durationText(minMin - avgAsleep)}.",
                true,
            )
            avgAsleep > maxMin + 30 -> advice += Advice(
                "Bạn ngủ nhiều hơn khuyến nghị",
                "Trung bình ${durationText(avgAsleep)} mỗi đêm, nhiều hơn mức ${g.rangeText} cho người ${g.label}. " +
                    "Nếu ngủ nhiều mà vẫn mệt kéo dài, nên hỏi ý kiến bác sĩ.",
                true,
            )
            else -> advice += Advice(
                "Thời lượng ngủ ổn",
                "Trung bình ${durationText(avgAsleep)} mỗi đêm, nằm trong mức ${g.rangeText} cho người ${g.label}.",
                false,
            )
        }

        // 1b. Nợ ngủ cộng dồn trong tuần.
        if (debt != null && debt >= DEBT_WARN_MIN) {
            advice += Advice(
                "Nợ ngủ ${durationText(debt)} trong 7 ngày",
                "Thiếu ngủ cộng dồn làm giảm tập trung dù bạn không thấy buồn ngủ: ngủ 6 tiếng mỗi đêm trong 2 tuần " +
                    "làm giảm tập trung tương đương thức trắng 1 đến 2 đêm (Van Dongen, 2003). Trả dần bằng cách " +
                    "lên giường sớm 30 đến 60 phút trong vài tối; ngủ nướng cuối tuần không bù lại được hết.",
                true,
            )
        }

        // 2. Ngủ muộn so với giờ đề xuất (tính theo giờ dậy trung bình của chính bạn).
        val target = targetMinutes(profile, age)
        val idealBedNoon = avgWakeNoon - target - FALL_ASLEEP_MIN
        val late = (avgBedNoon - idealBedNoon).roundToInt()
        if (late > 30) {
            advice += Advice(
                "Bạn thường ngủ muộn",
                "Giờ ngủ trung bình ${clockText(avgBedClock)}, muộn hơn giờ đề xuất " +
                    "${clockText(clockFromNoon(idealBedNoon.roundToInt()))} khoảng ${durationText(late)}. " +
                    "Đừng dời một lần cả tiếng: lên giường sớm hơn 15 phút, giữ 2 đến 3 tối rồi mới dời tiếp.",
                true,
            )
        }

        // 3. Giờ ngủ thất thường.
        if (spread > 60) {
            advice += Advice(
                "Giờ ngủ chưa đều",
                "Giờ ngủ của bạn lệch nhau khoảng ±${durationText(spread)}. Ngủ và dậy cùng giờ mỗi ngày, " +
                    "kể cả cuối tuần (lệch không quá 1 tiếng), giúp đồng hồ sinh học ổn định và dễ ngủ hơn.",
                true,
            )
        }

        // 4. Thức giấc dùng điện thoại giữa đêm. Người lớn tuổi hay tỉnh giấc hơn là bình thường nên ngưỡng cao hơn.
        val wakeLimit = if (g == AgeGroup.SENIOR) 3f else 1.5f
        if (avgInterruptions >= wakeLimit) {
            advice += Advice(
                "Hay mở điện thoại giữa đêm",
                "Trung bình ${"%.1f".format(avgInterruptions).replace('.', ',')} lần mỗi đêm. Bật chế độ Không làm phiền, " +
                    "úp màn hình hoặc để điện thoại xa giường; ánh sáng màn hình làm khó ngủ lại.",
                true,
            )
        }

        advice += ageTips(g)
        advice += habitTips(profile, clockFromNoon(idealBedNoon.roundToInt()))
        val quality = recent.map { nightQuality(it, target) }.average().roundToInt()
        return SleepReport(recent.size, avgAsleep, avgBedClock, spread, avgInterruptions, advice, quality, debt)
    }

    private fun List<SleepNight>.avgAsleep(): Int? =
        if (isEmpty()) null else map { it.asleepMs / 60_000.0 }.average().roundToInt()

    /** Lời khuyên riêng cho từng nhóm tuổi. */
    fun ageTips(g: AgeGroup): List<Advice> = when (g) {
        AgeGroup.CHILD -> listOf(
            Advice(
                "Trẻ 6–13 tuổi cần ${g.rangeText}",
                "Giữ giờ ngủ cố định cả cuối tuần, tắt TV, máy tính bảng và điện thoại ít nhất 1 tiếng trước khi ngủ.",
                false,
            ),
        )
        AgeGroup.TEEN -> listOf(
            Advice(
                "Tuổi teen dễ thức khuya",
                "Ở tuổi dậy thì đồng hồ sinh học tự lùi muộn hơn, nên 23h vẫn chưa buồn ngủ là bình thường. " +
                    "Giữ giờ dậy cố định và ra ngoài trời nắng buổi sáng để kéo giờ buồn ngủ sớm lại. Cần ${g.rangeText}.",
                false,
            ),
        )
        AgeGroup.YOUNG -> listOf(
            Advice(
                "Người 18–25 tuổi cần ${g.rangeText}",
                "Thức khuya ôn bài thường phản tác dụng: ngủ đủ sau khi học giúp não ghi nhớ bài tốt hơn. " +
                    "Tránh bù ngủ cuối tuần quá 1 tiếng so với ngày thường.",
                false,
            ),
        )
        AgeGroup.ADULT -> listOf(
            Advice(
                "Người 26–64 tuổi cần ${g.rangeText}",
                "Ngủ dưới 7 tiếng thường xuyên làm tăng nguy cơ tăng cân, tăng huyết áp và giảm tập trung. " +
                    "Giữ phòng ngủ tối, mát và yên tĩnh.",
                false,
            ),
        )
        AgeGroup.SENIOR -> listOf(
            Advice(
                "Tuổi 65+ ngủ khác người trẻ",
                "Buồn ngủ sớm, dậy sớm, giấc ngủ nông và hay tỉnh giữa đêm là thay đổi bình thường theo tuổi. " +
                    "Mục tiêu ${g.rangeText}. Ngủ trưa ngắn khoảng 20 phút, tránh ngủ trưa muộn; ra ngoài trời buổi chiều " +
                    "giúp không buồn ngủ quá sớm.",
                false,
            ),
        )
    }

    /** Lời khuyên theo thói quen trong hồ sơ. idealBedClock = giờ ngủ đề xuất (phút từ 0h) nếu đã biết. */
    fun habitTips(profile: UserProfile, idealBedClock: Int?): List<Advice> {
        val out = mutableListOf<Advice>()
        if (profile.lateCaffeine) {
            val cutoff = idealBedClock?.let { clockText((it - CAFFEINE_BEFORE_MIN + 24 * 60) % (24 * 60)) }
            out += Advice(
                "Cà phê, trà buổi chiều",
                "Caffeine uống 6 tiếng trước khi ngủ vẫn làm giấc ngủ ngắn đi đáng kể. " +
                    (cutoff?.let { "Ngừng cà phê, trà, nước tăng lực trước $it." } ?: "Ngừng uống từ đầu giờ chiều."),
                true,
            )
        }
        if (profile.chronotype == Chronotype.OWL) {
            out += Advice(
                "Bạn là cú đêm",
                "Đừng ép ngủ sớm một lần. Dời giờ ngủ sớm dần 15 phút mỗi vài tối, ra nắng ngay sau khi dậy, " +
                    "giảm đèn sáng và màn hình buổi tối.",
                false,
            )
        }
        if (profile.occupation == Occupation.SHIFT) {
            out += Advice(
                "Làm ca",
                "Ngủ ban ngày thì dùng rèm cản sáng và bịt tai, giữ một khối ngủ cố định. " +
                    "Nếu ngủ ban ngày, hãy đổi \"Giờ bắt đầu tính\" trong hồ sơ cho đúng ca của bạn.",
                false,
            )
        }
        if (profile.activity == ActivityLevel.HIGH) {
            out += Advice(
                "Vận động nhiều",
                "Mục tiêu ngủ đã cộng thêm 30 phút để cơ thể hồi phục. Tránh tập nặng trong 2 tiếng trước khi ngủ.",
                false,
            )
        }
        return out
    }
}
