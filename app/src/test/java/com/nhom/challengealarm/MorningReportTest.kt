package com.nhom.challengealarm

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MorningReportTest {

    private val zone: TimeZone = TimeZone.getDefault()
    private val min = 60_000L

    private fun at(day: Int, hour: Int, minute: Int): Long = Calendar.getInstance(zone).apply {
        clear()
        set(2026, Calendar.OCTOBER, day, hour, minute, 0)
    }.timeInMillis

    private fun log(day: Int, wakeSeconds: Long, method: String = WakeMethod.MATH): WakeLog {
        val fired = at(day, 6, 30)
        return WakeLog(alarmId = 1, label = "", firedAt = fired, dismissedAt = fired + wakeSeconds * 1000, method = method)
    }

    /** Đêm ngủ asleepMin phút, dậy 6:30 sáng ngày day. */
    private fun night(day: Int, asleepMin: Int): SleepNight {
        val wake = at(day, 6, 30)
        return SleepNight(wake - asleepMin * min, wake, asleepMin * min, 0L, 0)
    }

    @Test
    fun summaryShowsWakeTimeStreakAndLastNight() {
        val today = log(10, 70)
        val logs = listOf(log(8, 60), log(9, 50), today)
        val lastNight = night(10, 400)
        val s = MorningReport.build(today, logs, lastNight, listOf(lastNight), 480, wakeCheckAt = null, now = at(10, 6, 35), zone = zone)
        assertEquals(70L, s.wakeSeconds)
        assertTrue(s.onTime)
        assertEquals(3, s.streakDays)
        assertEquals(400, s.sleptMin)
        assertEquals(80, s.debtMin)
        assertTrue(s.tips.first().contains("thiếu"))
        assertTrue(s.tips.last().contains("ánh sáng"))
    }

    @Test
    fun logNotYetSavedStillCountsInStreak() {
        val today = log(10, 70)
        val s = MorningReport.build(today, listOf(log(9, 50)), null, emptyList(), null, null, at(10, 6, 35), zone)
        assertEquals(2, s.streakDays)
        assertNull(s.sleptMin)
        assertEquals(MorningReport.TIP_NO_DATA, s.tips.first())
    }

    @Test
    fun slowWakeIsNotOnTime() {
        val today = log(10, 8 * 60)
        val s = MorningReport.build(today, listOf(today), null, emptyList(), 480, null, at(10, 7, 0), zone)
        assertFalse(s.onTime)
    }

    @Test
    fun napSummaryHasNoNightData() {
        val s = MorningReport.nap(wakeCheckAt = 123L)
        assertTrue(s.nap)
        assertNull(s.sleptMin)
        assertEquals(123L, s.wakeCheckAt)
    }

    @Test
    fun sleepDebtAddsShortfallOnlyWithinSevenDays() {
        val now = at(10, 7, 0)
        val nights = listOf(night(10, 420), night(9, 480), night(8, 360), night(1, 0))
        // 8 tiếng mục tiêu: thiếu 60 + 0 + 120; đêm ngày 1 nằm ngoài 7 ngày nên không tính; đêm ngủ dư không bù.
        assertEquals(180, SleepAdvisor.sleepDebtMin(nights, 480, now))
        assertNull(SleepAdvisor.sleepDebtMin(emptyList(), 480, now))
    }

    @Test
    fun insightComparesEnoughAndShortNights() {
        val nights = listOf(night(5, 480), night(6, 470), night(7, 330), night(8, 300))
        val logs = listOf(log(5, 40), log(6, 60), log(7, 200), log(8, 160))
        val insight = Insights.sleepVsWake(nights, logs, 480, zone)
        assertNotNull(insight)
        assertEquals(50L, insight!!.enoughSec)
        assertEquals(180L, insight.shortSec)
        assertEquals(2, insight.enoughNights)
        assertEquals(72, insight.fasterPct)
    }

    @Test
    fun insightNeedsBothGroups() {
        val nights = listOf(night(5, 480), night(6, 470), night(7, 300))
        val logs = listOf(log(5, 40), log(6, 60), log(7, 200))
        assertNull(Insights.sleepVsWake(nights, logs, 480, zone))
    }
}
