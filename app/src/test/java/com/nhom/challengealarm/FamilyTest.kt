package com.nhom.challengealarm

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FamilyTest {

    private val zone: TimeZone = TimeZone.getDefault()
    private val min = 60_000L

    private fun at(day: Int, hour: Int, minute: Int): Long = Calendar.getInstance(zone).apply {
        clear()
        set(2026, Calendar.OCTOBER, day, hour, minute, 0)
    }.timeInMillis

    private val now = at(22, 9, 0)
    private val today = SleepAnalyzer.dayStart(now, zone)

    private fun member(kind: MemberKind, age: Int, nights: List<NightReport>, plan: FamilyPlan = FamilyPlan.defaultFor(kind)) =
        FamilyMember("x1", "Bố", "Bố", kind, 2026 - age, plan, nights, lastSync = now - 30 * min, paired = true)

    private fun night(asleep: Int, wakings: Int = 1, sleepStart: Long? = at(21, 22, 0), alarmAt: Long? = at(22, 5, 30), dismiss: Int? = 60) =
        NightReport(today, sleepStart, asleep, wakings, alarmAt, dismiss)

    @Test
    fun codesSurviveARoundTrip() {
        val plan = FamilyPlan(21 * 60, 6 * 60 + 15, WEEKDAYS_MASK, ChallengePreset.PLAYFUL, 0)
        val codes = listOf(
            FamilyCode.Invite("abc12345", "Bé Na", "Con", MemberKind.CHILD, 2017, "Mẹ Lan", plan),
            FamilyCode.Report("abc12345", "Bố", MemberKind.ELDER, 1958, now, listOf(night(400), night(380, dismiss = null))),
            FamilyCode.PlanUpdate("abc12345", "Mẹ Lan", now, plan),
        )
        codes.forEach { c -> assertEquals(c, FamilyCodec.decode(FamilyCodec.encode(c))) }
    }

    @Test
    fun codeIsFoundInsideAChatMessage() {
        val code = FamilyCodec.encode(FamilyCode.PlanUpdate("id1", "Lan", now, FamilyPlan.defaultFor(MemberKind.ELDER)))
        val message = "Mã kế hoạch giấc ngủ cho Bố:\n$code\nMở Challenge Alarm > Gia đình > Nhập mã."
        assertNotNull(FamilyCodec.decode(message))
        assertNull(FamilyCodec.decode("xin chào"))
        assertNull(FamilyCodec.decode("CA1:R:!!!"))
    }

    @Test
    fun separatorsInNamesDoNotBreakCodes() {
        val c = FamilyCode.Invite("id1", "Ông|Bà;Nội", "Ông, bà", MemberKind.ELDER, 1950, "Con", FamilyPlan.defaultFor(MemberKind.ELDER))
        val back = FamilyCodec.decode(FamilyCodec.encode(c)) as FamilyCode.Invite
        assertEquals("Ông Bà Nội", back.name)
    }

    @Test
    fun membersAreStoredAndRestored() {
        val m = member(MemberKind.CHILD, 9, listOf(night(500)))
        assertEquals(m, FamilyCodec.memberFromString(FamilyCodec.memberToString(m)))
    }

    @Test
    fun elderWhoHasNotTurnedOffTheAlarmIsUrgent() {
        val m = member(MemberKind.ELDER, 70, listOf(night(420, alarmAt = now - 25 * min, dismiss = null)))
        val alerts = FamilyAdvisor.alerts(m, now, zone)
        assertEquals(FamilyLevel.URGENT, alerts.first().level)
        assertTrue(alerts.first().title.contains("chưa tắt chuông"))
        // Mới reo 5 phút thì chưa báo.
        val early = member(MemberKind.ELDER, 70, listOf(night(420, alarmAt = now - 5 * min, dismiss = null)))
        assertTrue(FamilyAdvisor.alerts(early, now, zone).none { it.level == FamilyLevel.URGENT })
    }

    @Test
    fun elderSleepRulesFollowAge() {
        val fine = member(MemberKind.ELDER, 70, listOf(night(430, wakings = 2)))
        assertEquals(FamilyLevel.OK, FamilyAdvisor.level(fine, now, zone))
        val restless = member(MemberKind.ELDER, 70, listOf(night(430, wakings = 5)))
        assertTrue(FamilyAdvisor.alerts(restless, now, zone).any { it.title.contains("thức giấc") })
        val short = member(MemberKind.ELDER, 70, listOf(night(330)))
        assertTrue(FamilyAdvisor.alerts(short, now, zone).any { it.title.contains("ngủ ít") })
    }

    @Test
    fun childBedtimeIsCheckedAcrossMidnight() {
        // Giờ ngủ đặt 21:00, màn hình còn sáng đến 0:30: muộn 210 phút.
        val late = member(MemberKind.CHILD, 9, listOf(night(560, sleepStart = at(22, 0, 30))))
        val alert = FamilyAdvisor.alerts(late, now, zone).firstOrNull { it.title.contains("ngủ muộn") }
        assertNotNull(alert)
        assertTrue(alert!!.title.contains("210"))
        val onTime = member(MemberKind.CHILD, 9, listOf(night(560, sleepStart = at(21, 21, 10))))
        assertTrue(FamilyAdvisor.alerts(onTime, now, zone).none { it.title.contains("ngủ muộn") })
    }

    @Test
    fun childNeedsMoreSleepThanAdults() {
        // 8 tiếng là ổn với người lớn nhưng thiếu với trẻ 9 tuổi (9 đến 11 tiếng).
        val kid = member(MemberKind.CHILD, 9, listOf(night(480, sleepStart = at(21, 21, 0))))
        assertTrue(FamilyAdvisor.alerts(kid, now, zone).any { it.title.contains("ngủ ít") })
        val adult = member(MemberKind.ADULT, 30, listOf(night(480)))
        assertTrue(FamilyAdvisor.alerts(adult, now, zone).none { it.title.contains("ngủ ít") })
    }

    @Test
    fun staleReportsAreFlagged() {
        val m = member(MemberKind.ELDER, 70, listOf(night(430))).copy(lastSync = now - 40 * 3_600_000L)
        assertTrue(FamilyAdvisor.alerts(m, now, zone).any { it.title.contains("Chưa có báo cáo") })
    }

    @Test
    fun startingFamilyShowsDifferentSituations() {
        val family = FamilyDemo.family(now, zone)
        assertEquals(3, family.size)
        family.forEach { assertEquals(7, it.nights.size) }
        val grandpa = family.first { it.id == FamilyDemo.GRANDPA }
        val grandma = family.first { it.id == FamilyDemo.GRANDMA }
        val kid = family.first { it.id == FamilyDemo.KID }
        assertEquals(FamilyLevel.OK, FamilyAdvisor.level(grandpa, now, zone))
        assertEquals(FamilyLevel.WATCH, FamilyAdvisor.level(grandma, now, zone))
        assertTrue(FamilyAdvisor.alerts(kid, now, zone).isNotEmpty())
        // Ông không tắt chuông: thành khẩn cấp; một buổi sáng tốt: hết cảnh báo khẩn cấp.
        val missed = FamilyDemo.simulateMissedAlarm(grandpa, now, zone)
        assertEquals(FamilyLevel.URGENT, FamilyAdvisor.level(missed, now, zone))
        val good = FamilyDemo.simulateGoodMorning(missed, now, zone)
        assertTrue(FamilyAdvisor.alerts(good, now, zone).none { it.level == FamilyLevel.URGENT })
        val lateKid = FamilyDemo.simulateLateNight(kid, now, zone)
        assertTrue(FamilyAdvisor.alerts(lateKid, now, zone).any { it.title.contains("ngủ muộn") })
    }

    @Test
    fun startingFamilyStaysBelievableEveryDay() {
        // Mở app vào bất kỳ ngày nào trong 3 tuần, giờ bất kỳ: luôn có báo cáo mới, Ông ổn, Bà và Bé Na cần chú ý.
        var family = FamilyDemo.family(now, zone)
        for (hours in 0..21 * 24 step 5) {
            val t = now + hours * 3_600_000L
            family = family.map { FamilyDemo.refresh(it, t, zone) }
            family.forEach { m ->
                assertEquals(7, m.nights.size)
                assertTrue("${m.name} báo cáo cũ", FamilyAdvisor.alerts(m, t, zone).none { it.title.startsWith("Chưa có báo cáo") })
                assertTrue(m.lastSync <= t)
            }
            assertEquals(FamilyLevel.OK, FamilyAdvisor.level(family[0], t, zone))
            assertEquals(FamilyLevel.WATCH, FamilyAdvisor.level(family[1], t, zone))
            assertEquals(FamilyLevel.WATCH, FamilyAdvisor.level(family[2], t, zone))
        }
    }

    @Test
    fun refreshKeepsSameMorningAndHistory() {
        val family = FamilyDemo.family(now, zone)
        // Cùng buổi sáng: giữ nguyên (kể cả tình huống vừa thử).
        val missed = FamilyDemo.simulateMissedAlarm(family[0], now, zone)
        assertTrue(FamilyDemo.refresh(missed, now + 3_600_000L, zone) === missed)
        // Sáng hôm sau: đêm cũ không đổi, thêm đêm mới.
        val next = FamilyDemo.refresh(family[1], now + 24 * 3_600_000L, zone)
        assertEquals(family[1].nights.drop(1), next.nights.dropLast(1))
        // Người đã gửi báo cáo thật thì không tự thêm đêm.
        val real = family[2].copy(sample = false)
        assertTrue(FamilyDemo.refresh(real, now + 48 * 3_600_000L, zone) === real)
    }

    @Test
    fun presetsMatchAgeGroups() {
        assertEquals(ChallengePreset.GENTLE, FamilyPlan.defaultFor(MemberKind.ELDER).preset)
        assertEquals(ChallengePreset.PLAYFUL, FamilyPlan.defaultFor(MemberKind.CHILD).preset)
        assertTrue(-1 !in ChallengePreset.GENTLE.challengeIds) // người lớn tuổi không phải lắc máy
        assertTrue(FamilyPlan.defaultFor(MemberKind.ELDER).checkInMin > 0)
    }
}
