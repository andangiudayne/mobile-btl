package com.nhom.challengealarm

import java.util.Calendar
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SleepAnalyzerTest {

    private val zone: TimeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
    private val min = 60_000L

    /** day = ngày trong tháng 10/2026. */
    private fun at(day: Int, hour: Int, minute: Int): Long = Calendar.getInstance(zone).apply {
        clear()
        set(2026, Calendar.OCTOBER, day, hour, minute, 0)
    }.timeInMillis

    private fun on(t: Long) = ScreenEvent(t, true)
    private fun off(t: Long) = ScreenEvent(t, false)

    private val wake = at(11, 6, 0)
    private val window = at(10, 23, 0)

    @Test
    fun windowStartIsLatestNightStartBeforeWake() {
        assertEquals(at(10, 23, 0), SleepAnalyzer.windowStart(wake, 23 * 60, zone))
        assertEquals(at(10, 21, 0), SleepAnalyzer.windowStart(wake, 21 * 60, zone))
    }

    @Test
    fun afternoonAlarmIsNotANight() {
        assertNull(SleepAnalyzer.windowStart(at(11, 16, 0), 23 * 60, zone))
    }

    @Test
    fun screenOffAfterElevenCountsAsSleep() {
        val events = listOf(on(at(10, 22, 0)), off(at(10, 23, 40)))
        val night = SleepAnalyzer.analyze(events, window, wake)
        assertNotNull(night)
        assertEquals(at(10, 23, 40), night!!.sleepStart)
        assertEquals(6 * 60 + 20, (night.asleepMs / min).toInt())
        assertEquals(0, night.interruptions)
    }

    @Test
    fun screenAlreadyOffAtWindowStartStartsAtWindow() {
        val events = listOf(on(at(10, 21, 0)), off(at(10, 22, 30)))
        val night = SleepAnalyzer.analyze(events, window, wake)!!
        assertEquals(window, night.sleepStart)
    }

    @Test
    fun shortPhoneCheckAtNightIsSubtractedAndCounted() {
        val events = listOf(
            off(at(10, 23, 30)),
            on(at(11, 3, 0)), off(at(11, 3, 5)),
        )
        val night = SleepAnalyzer.analyze(events, window, wake)!!
        assertEquals(at(10, 23, 30), night.sleepStart)
        assertEquals(1, night.interruptions)
        assertEquals(5L, night.awakeMs / min)
        assertEquals(6 * 60 + 30 - 5, (night.asleepMs / min).toInt())
    }

    @Test
    fun puttingPhoneDownThenUsingItForAnHourIsNotSleep() {
        // 23:05 đặt máy xuống 20 phút, 23:25 cầm lên dùng đến 0:40 mới ngủ thật.
        val events = listOf(
            on(at(10, 22, 50)), off(at(10, 23, 5)),
            on(at(10, 23, 25)), off(at(11, 0, 40)),
        )
        val night = SleepAnalyzer.analyze(events, window, wake)!!
        assertEquals(at(11, 0, 40), night.sleepStart)
        assertEquals(0, night.interruptions)
    }

    @Test
    fun briefPickupBetweenTwoLongOffPeriodsKeepsEarlierStart() {
        // 23:10 tắt máy, 23:40 cầm lên xem giờ 2 phút, rồi ngủ tiếp.
        val events = listOf(
            off(at(10, 23, 10)),
            on(at(10, 23, 40)), off(at(10, 23, 42)),
        )
        val night = SleepAnalyzer.analyze(events, window, wake)!!
        assertEquals(at(10, 23, 10), night.sleepStart)
        assertEquals(1, night.interruptions)
    }

    @Test
    fun usingPhoneAllNightGivesNoRecord() {
        val events = listOf(on(at(10, 22, 0)), off(at(11, 5, 50)))
        assertNull(SleepAnalyzer.analyze(events, window, wake))
    }

    @Test
    fun dayStartIsMidnightOfWakeDay() {
        assertEquals(at(11, 0, 0), SleepAnalyzer.dayStart(wake, zone))
    }
}
