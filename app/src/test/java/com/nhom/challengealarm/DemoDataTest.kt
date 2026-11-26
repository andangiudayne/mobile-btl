package com.nhom.challengealarm

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoDataTest {

    private val zone: TimeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")

    // Thứ Năm 22/10/2026, 20:00
    private val now: Long = Calendar.getInstance(zone).apply {
        clear()
        set(2026, Calendar.OCTOBER, 22, 20, 0, 0)
    }.timeInMillis

    private val bundle = DemoData.generate(now, zone, schoolAlarmId = 1, weekendAlarmId = 2)

    @Test
    fun coversAboutAMonthWithoutSundays() {
        // 30 ngày trừ 4 hoặc 5 Chủ nhật.
        assertTrue(bundle.wakeLogs.size in 25..26)
        assertEquals(bundle.wakeLogs.size, bundle.sleepLogs.size)
        val sundays = bundle.wakeLogs.count {
            Calendar.getInstance(zone).apply { timeInMillis = it.firedAt }.get(Calendar.DAY_OF_WEEK) == Calendar.SUNDAY
        }
        assertEquals(0, sundays)
        assertTrue(bundle.wakeLogs.all { it.firedAt < now })
    }

    @Test
    fun logsBelongToTheTwoRealAlarms() {
        bundle.wakeLogs.forEach {
            val saturday = Calendar.getInstance(zone).apply { timeInMillis = it.firedAt }.get(Calendar.DAY_OF_WEEK) == Calendar.SATURDAY
            assertEquals(if (saturday) 2 else 1, it.alarmId)
            assertEquals(if (saturday) DemoData.WEEKEND_LABEL else DemoData.SCHOOL_LABEL, it.label)
            val hour = Calendar.getInstance(zone).apply { timeInMillis = it.firedAt }.get(Calendar.HOUR_OF_DAY)
            assertEquals(if (saturday) 7 else 6, hour)
        }
        // Thử thách đã dùng phải là thử thách của đúng chuông đó.
        val school = setOf(WakeMethod.MATH, WakeMethod.TYPING, WakeMethod.MEMORY, WakeMethod.STROOP, WakeMethod.ODD)
        val weekend = setOf(WakeMethod.SLIDE, WakeMethod.CLOCK)
        val extra = setOf(WakeMethod.AWAKE, WakeMethod.EMERGENCY)
        bundle.wakeLogs.forEach { assertTrue(it.method in extra || it.method in (if (it.alarmId == 2) weekend else school)) }
    }

    @Test
    fun sameSeedGivesSameData() {
        assertEquals(bundle, DemoData.generate(now, zone, schoolAlarmId = 1, weekendAlarmId = 2))
    }

    @Test
    fun nightsAreRealisticAndUnique() {
        assertEquals(bundle.sleepLogs.size, bundle.sleepLogs.map { it.nightOf }.toSet().size)
        bundle.sleepLogs.forEach {
            assertTrue("ngủ ${it.asleepMin} phút", it.asleepMin in 240..600)
            assertTrue(it.sleepStart < it.wakeAt)
        }
    }

    @Test
    fun habitsImproveOverTheMonth() {
        val sorted = bundle.sleepLogs.sortedBy { it.nightOf }
        val firstWeek = sorted.take(6).map { it.asleepMin }.average()
        val lastWeek = sorted.takeLast(6).map { it.asleepMin }.average()
        assertTrue("ngủ nhiều hơn về cuối tháng: $firstWeek -> $lastWeek", lastWeek > firstWeek + 30)
    }

    @Test
    fun statsAndInsightsHaveSomethingToShow() {
        val stats = StatsCalculator.compute(bundle.wakeLogs, now, zone)
        assertTrue("chuỗi ngày ${stats.streakDays}", stats.streakDays >= 5)
        assertEquals(2, stats.relapses)
        assertTrue(stats.onTimeRate!! < 1f)
        val insight = Insights.sleepVsWake(bundle.sleepLogs.map { it.toNight() }, bundle.wakeLogs, 480, zone)
        assertNotNull(insight)
        assertTrue(insight!!.fasterPct > 0)
        val debt = SleepAdvisor.sleepDebtMin(bundle.sleepLogs.map { it.toNight() }, 480, now)
        assertNotNull(debt)
    }

    @Test
    fun looksGoodWhicheverDayTheAppIsInstalled() {
        // Cài app vào bất kỳ giờ nào trong 2 tuần tới: Thống kê vẫn có chuỗi ngày, nhận xét và nợ ngủ.
        for (hours in 0..14 * 24 step 7) {
            val t = now + hours * 3_600_000L
            val b = DemoData.generate(t, zone, schoolAlarmId = 1, weekendAlarmId = 2)
            val stats = StatsCalculator.compute(b.wakeLogs, t, zone)
            assertTrue("chuỗi ngày ${stats.streakDays} lúc +${hours}h", stats.streakDays >= 3)
            assertNotNull("nhận xét lúc +${hours}h", Insights.sleepVsWake(b.sleepLogs.map { it.toNight() }, b.wakeLogs, 480, zone))
        }
    }
}
