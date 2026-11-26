package com.nhom.challengealarm

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepAdvisorTest {

    private val zone: TimeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
    private val min = 60_000L

    private fun at(day: Int, hour: Int, minute: Int): Long = Calendar.getInstance(zone).apply {
        clear()
        set(2026, Calendar.OCTOBER, day, hour, minute, 0)
    }.timeInMillis

    private fun profile(
        activity: ActivityLevel = ActivityLevel.MEDIUM,
        caffeine: Boolean = false,
        chronotype: Chronotype = Chronotype.NEUTRAL,
    ) = UserProfile(
        name = "An",
        birthYear = 2005,
        occupation = Occupation.UNIVERSITY,
        activity = activity,
        chronotype = chronotype,
        lateCaffeine = caffeine,
        nightStartMin = 21 * 60,
        remind = true,
    )

    /** Đêm ngủ từ bedHour:bedMinute hôm trước đến 6:00 sáng ngày day. */
    private fun night(day: Int, bedHour: Int, bedMinute: Int, interruptions: Int = 0): SleepNight {
        val start = if (bedHour >= 12) at(day - 1, bedHour, bedMinute) else at(day, bedHour, bedMinute)
        val wake = at(day, 6, 0)
        return SleepNight(start, wake, wake - start, 0L, interruptions)
    }

    @Test
    fun ageGroupsFollowNsfRanges() {
        assertEquals(AgeGroup.CHILD, AgeGroup.of(10))
        assertEquals(AgeGroup.TEEN, AgeGroup.of(16))
        assertEquals(AgeGroup.YOUNG, AgeGroup.of(21))
        assertEquals(AgeGroup.ADULT, AgeGroup.of(40))
        assertEquals(AgeGroup.SENIOR, AgeGroup.of(70))
        assertEquals(7f, AgeGroup.SENIOR.minHours)
        assertEquals(8f, AgeGroup.SENIOR.maxHours)
        assertEquals(8f, AgeGroup.TEEN.minHours)
    }

    @Test
    fun targetDiffersByAge() {
        val p = profile()
        assertEquals(10 * 60, SleepAdvisor.targetMinutes(p, 10))
        assertEquals(9 * 60, SleepAdvisor.targetMinutes(p, 16))
        assertEquals(8 * 60, SleepAdvisor.targetMinutes(p, 21))
        assertEquals(450, SleepAdvisor.targetMinutes(p, 70))
    }

    @Test
    fun highActivityAddsTimeButStaysInRange() {
        assertEquals(8 * 60 + 30, SleepAdvisor.targetMinutes(profile(ActivityLevel.HIGH), 21))
        assertEquals(8 * 60, SleepAdvisor.targetMinutes(profile(ActivityLevel.HIGH), 70)) // 7,5 + 0,5 = 8 = mức trên
    }

    @Test
    fun planCountsBackFromAlarm() {
        val wake = at(11, 6, 0)
        val plan = SleepAdvisor.plan(profile(), 21, wake)
        assertEquals(at(10, 21, 45), plan.bedtime) // 6:00 - 8 tiếng - 15 phút
        assertEquals(at(10, 22, 45), plan.latestBedtime) // 6:00 - 7 tiếng - 15 phút
        assertEquals(at(10, 15, 45), plan.caffeineCutoff)
        assertEquals(at(10, 20, 45), plan.screensOff)
    }

    @Test
    fun shortSleepIsFlagged() {
        val nights = (5..10).map { night(it, 1, 0) } // ngủ 1h đến 6h = 5 tiếng
        val r = SleepAdvisor.report(nights, profile(), 21, zone)
        assertEquals(300, r.avgAsleepMin)
        assertTrue(r.advice.any { it.warn && it.title.contains("thiếu ngủ") })
        assertTrue(r.advice.any { it.title.contains("ngủ muộn") })
        assertEquals(60, r.avgBedClockMin)
    }

    @Test
    fun bedtimeAverageWrapsAroundMidnight() {
        val nights = listOf(night(8, 23, 30), night(9, 0, 30), night(10, 23, 30), night(11, 0, 30))
        val r = SleepAdvisor.report(nights, profile(), 21, zone)
        assertEquals(0, r.avgBedClockMin) // trung bình 23:30 và 0:30 là 0:00, không phải 12:00
        assertEquals(30, r.bedSpreadMin)
    }

    @Test
    fun goodSleepGetsNoDurationWarning() {
        val nights = (6..10).map { night(it, 22, 0) } // 8 tiếng
        val r = SleepAdvisor.report(nights, profile(), 21, zone)
        assertTrue(r.advice.any { !it.warn && it.title == "Thời lượng ngủ ổn" })
        assertTrue(r.advice.none { it.title.contains("ngủ muộn") })
    }

    @Test
    fun seniorsTolerateMoreNightWakings() {
        val nights = (6..10).map { night(it, 22, 30, interruptions = 2) }
        val young = SleepAdvisor.report(nights, profile(), 21, zone)
        val senior = SleepAdvisor.report(nights, profile(), 70, zone)
        assertTrue(young.advice.any { it.title.contains("giữa đêm") })
        assertTrue(senior.advice.none { it.title.contains("giữa đêm") })
    }

    @Test
    fun tooFewNightsAsksForMoreData() {
        val r = SleepAdvisor.report(listOf(night(10, 23, 0)), profile(caffeine = true), 21, zone)
        assertEquals(1, r.nights)
        assertTrue(r.advice.first().title.contains("thu thập"))
        assertTrue(r.advice.any { it.title.contains("Cà phê") })
    }

    @Test
    fun nightQualityRewardsFullSleepAndPenalisesWakings() {
        val wake = at(11, 6, 0)
        val full = SleepNight(wake - 8 * 60 * min, wake, 8 * 60 * min, 0L, 0)
        val short = SleepNight(wake - 4 * 60 * min, wake, 4 * 60 * min, 0L, 0)
        val restless = SleepNight(wake - 8 * 60 * min, wake, 8 * 60 * min, 10 * min, 3)
        assertEquals(100, SleepAdvisor.nightQuality(full, 480))
        assertEquals(50, SleepAdvisor.nightQuality(short, 480))
        assertEquals(82, SleepAdvisor.nightQuality(restless, 480))
    }

    @Test
    fun reportIncludesAverageQuality() {
        val nights = (6..10).map { night(it, 22, 0) } // 8 tiếng, đúng mục tiêu tuổi 21
        assertEquals(100, SleepAdvisor.report(nights, profile(), 21, zone).qualityPct)
    }

    @Test
    fun weeklySleepDebtIsReportedAndWarned() {
        val nights = (6..10).map { night(it, 1, 0) } // 5 tiếng mỗi đêm, mục tiêu tuổi 21 là 8 tiếng
        val r = SleepAdvisor.report(nights, profile(), 21, zone)
        assertEquals(5 * 180, r.debtMin)
        assertTrue(r.advice.any { it.warn && it.title.startsWith("Nợ ngủ") })
    }

    @Test
    fun noDebtWarningWhenSleepIsEnough() {
        val nights = (6..10).map { night(it, 22, 0) }
        val r = SleepAdvisor.report(nights, profile(), 21, zone)
        assertEquals(0, r.debtMin)
        assertTrue(r.advice.none { it.title.startsWith("Nợ ngủ") })
    }
}
