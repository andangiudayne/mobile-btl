package com.nhom.challengealarm

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class StatsTest {

    private val zone: TimeZone = TimeZone.getDefault()

    private fun millis(day: Int, hour: Int, minute: Int): Long = Calendar.getInstance(zone).apply {
        clear()
        set(2026, Calendar.OCTOBER, day, hour, minute, 0)
    }.timeInMillis

    private val now = millis(10, 12, 0)

    private fun log(day: Int, solveSeconds: Long, method: String = WakeMethod.MATH): WakeLog {
        val fired = millis(day, 6, 30)
        return WakeLog(alarmId = 1, label = "", firedAt = fired, dismissedAt = fired + solveSeconds * 1000, method = method)
    }

    @Test
    fun emptyLogsGiveEmptyStats() {
        val s = StatsCalculator.compute(emptyList(), now, zone)
        assertEquals(0, s.total)
        assertNull(s.onTimeRate)
        assertNull(s.avgSeconds)
        assertEquals(0, s.streakDays)
        assertEquals(7, s.last7.size)
    }

    @Test
    fun streakCountsOnTimeDaysAndSkipsShortGaps() {
        // Ngày 7 không có chuông (1 ngày trống) nên chuỗi nối tiếp sang ngày 6.
        val logs = listOf(log(10, 60), log(9, 90), log(8, 30), log(6, 40))
        assertEquals(4, StatsCalculator.compute(logs, now, zone).streakDays)
    }

    @Test
    fun streakSurvivesUntilTodayEndsIfTodayHasNoWakeYet() {
        val logs = listOf(log(9, 60), log(8, 60))
        assertEquals(2, StatsCalculator.compute(logs, now, zone).streakDays)
    }

    @Test
    fun streakBreaksAfterMoreThanTwoDaysWithoutAlarm() {
        val logs = listOf(log(7, 60), log(6, 60)) // 8, 9, 10 không có chuông
        assertEquals(0, StatsCalculator.compute(logs, now, zone).streakDays)
    }

    @Test
    fun weekendWithoutAlarmDoesNotBreakStreak() {
        // 10/10/2026 là thứ Bảy. Chuông chỉ đặt ngày thường: T2 05 đến T6 09, và T6 02.
        // T7 03 và CN 04 không có chuông nên không làm đứt chuỗi: 5 ngày tuần này + T6 tuần trước = 6.
        val logs = listOf(log(9, 60), log(8, 60), log(7, 60), log(6, 60), log(5, 60), log(2, 60))
        assertEquals(6, StatsCalculator.compute(logs, now, zone).streakDays)
    }

    @Test
    fun lateWakeTodayResetsStreak() {
        val logs = listOf(log(10, ON_TIME_LIMIT_MS / 1000 + 60), log(9, 60), log(8, 60))
        assertEquals(0, StatsCalculator.compute(logs, now, zone).streakDays)
    }

    @Test
    fun slowWakeAndEmergencyExitDoNotCountAsOnTime() {
        val slow = log(10, ON_TIME_LIMIT_MS / 1000 + 1)
        val emergency = log(9, 5, WakeMethod.EMERGENCY)
        val s = StatsCalculator.compute(listOf(slow, emergency), now, zone)
        assertEquals(0f, s.onTimeRate!!, 0.001f)
        assertEquals(0, s.streakDays)
    }

    @Test
    fun onTimeRateAndAverageIgnoreEmergencyExitsInAverage() {
        val logs = listOf(log(10, 60), log(9, 120), log(8, 10, WakeMethod.EMERGENCY), log(7, 400))
        val s = StatsCalculator.compute(logs, now, zone)
        assertEquals(4, s.total)
        assertEquals(0.5f, s.onTimeRate!!, 0.001f) // 60s và 120s đúng giờ; 400s và thoát khẩn cấp thì không
        assertEquals(193L, s.avgSeconds) // (60 + 120 + 400) / 3
    }

    @Test
    fun last7CoversSevenDaysEndingToday() {
        val s = StatsCalculator.compute(listOf(log(10, 80), log(4, 50)), now, zone)
        assertEquals(7, s.last7.size)
        assertEquals(80L, s.last7.last().avgSeconds)
        assertNotNull(s.last7.first().avgSeconds) // ngày 04/10 là ngày đầu của cửa sổ 7 ngày
        assertNull(s.last7[1].avgSeconds)
    }

    @Test
    fun multiRoundAlarmCountsTimeToFirstRoundNotBreaks() {
        val fired = millis(10, 6, 30)
        // Qua màn 1 sau 2 phút, nghỉ giữa màn rồi tắt hẳn sau 15 phút: vẫn là dậy đúng giờ.
        val log = WakeLog(
            alarmId = 1, label = "", firedAt = fired, dismissedAt = fired + 15 * 60_000L,
            method = WakeMethod.AWAKE, wokeAt = fired + 120_000L, rounds = 2,
        )
        val s = StatsCalculator.compute(listOf(log), now, zone)
        assertEquals(1f, s.onTimeRate!!, 0.001f)
        assertEquals(120L, s.avgSeconds)
    }

    @Test
    fun fallingBackAsleepIsNotOnTimeAndBreaksStreak() {
        // Hôm nay tắt chuông nhanh nhưng không trả lời "Bạn còn thức không?" (ngủ lại): không tính đúng giờ.
        val relapsed = log(10, 40).copy(relapsed = true)
        val s = StatsCalculator.compute(listOf(relapsed, log(9, 60), log(8, 60)), now, zone)
        assertEquals(1, s.relapses)
        assertEquals(2f / 3f, s.onTimeRate!!, 0.001f)
        assertEquals(0, s.streakDays)
    }
}
