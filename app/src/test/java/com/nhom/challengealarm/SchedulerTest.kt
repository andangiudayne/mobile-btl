package com.nhom.challengealarm

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SchedulerTest {

    // Thứ Bảy 03/10/2026, 10:00
    private val now: Long = Calendar.getInstance().apply {
        clear()
        set(2026, Calendar.OCTOBER, 3, 10, 0, 0)
    }.timeInMillis

    private fun at(day: Int, hour: Int, minute: Int): Long = Calendar.getInstance().apply {
        clear()
        set(2026, Calendar.OCTOBER, day, hour, minute, 0)
    }.timeInMillis

    private fun mask(vararg bits: Int): Int = bits.fold(0) { acc, b -> acc or (1 shl b) }

    @Test
    fun onceAlarmLaterTodayFiresToday() {
        val alarm = AlarmEntity(hour = 11, minute = 0)
        assertEquals(at(3, 11, 0), AlarmScheduler.nextTriggerMillis(alarm, now))
    }

    @Test
    fun onceAlarmAlreadyPassedFiresTomorrow() {
        val alarm = AlarmEntity(hour = 7, minute = 0)
        assertEquals(at(4, 7, 0), AlarmScheduler.nextTriggerMillis(alarm, now))
    }

    @Test
    fun alarmAtExactCurrentTimeFiresTomorrow() {
        val alarm = AlarmEntity(hour = 10, minute = 0)
        assertEquals(at(4, 10, 0), AlarmScheduler.nextTriggerMillis(alarm, now))
    }

    @Test
    fun mondayOnlyAlarmFiresNextMonday() {
        // bit 0 = Thứ 2; Thứ Hai kế tiếp là 05/10/2026
        val alarm = AlarmEntity(hour = 7, minute = 0, daysMask = mask(0))
        assertEquals(at(5, 7, 0), AlarmScheduler.nextTriggerMillis(alarm, now))
    }

    @Test
    fun saturdayAlarmLaterTodayFiresToday() {
        // bit 5 = Thứ 7
        val alarm = AlarmEntity(hour = 11, minute = 30, daysMask = mask(5))
        assertEquals(at(3, 11, 30), AlarmScheduler.nextTriggerMillis(alarm, now))
    }

    @Test
    fun saturdayAlarmAlreadyPassedFiresNextWeek() {
        val alarm = AlarmEntity(hour = 7, minute = 0, daysMask = mask(5))
        assertEquals(at(10, 7, 0), AlarmScheduler.nextTriggerMillis(alarm, now))
    }

    @Test
    fun sundayAlarmFiresTomorrow() {
        // bit 6 = Chủ nhật
        val alarm = AlarmEntity(hour = 8, minute = 0, daysMask = mask(6))
        assertEquals(at(4, 8, 0), AlarmScheduler.nextTriggerMillis(alarm, now))
    }

    @Test
    fun everyDayAlarmAlwaysHasNextTrigger() {
        val alarm = AlarmEntity(hour = 6, minute = 15, daysMask = 0b1111111)
        assertNotNull(AlarmScheduler.nextTriggerMillis(alarm, now))
    }
}
