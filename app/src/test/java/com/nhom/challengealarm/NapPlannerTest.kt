package com.nhom.challengealarm

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NapPlannerTest {

    private fun at(hour: Int, minute: Int, second: Int = 0): Long = Calendar.getInstance().apply {
        clear()
        set(2026, Calendar.OCTOBER, 14, hour, minute, second)
    }.timeInMillis

    @Test
    fun ringTimeAddsFallAsleepMinutesAndRoundsUp() {
        // 13:00:00 + 20 phút ngủ + 5 phút chìm vào giấc = 13:25
        assertEquals(at(13, 25), NapPlanner.ringAt(20, at(13, 0)))
        // 13:00:40 thì làm tròn lên 13:26, không reo sớm hơn số phút đã chọn
        assertEquals(at(13, 26), NapPlanner.ringAt(20, at(13, 0, 40)))
    }

    @Test
    fun napAlarmIsOneShotEasyAndMarked() {
        val a = NapPlanner.alarmFor(20, at(13, 0))
        assertEquals(13, a.hour)
        assertEquals(25, a.minute)
        assertEquals(0, a.daysMask)
        assertEquals(1, a.rounds)
        assertEquals(0, a.snoozeLimit)
        assertEquals(0, a.breakMinutes)
        assertTrue(a.nap)
        assertTrue(a.enabled)
        assertEquals(NapPlanner.WAKE_CHECK_MIN, a.wakeCheckMin)
        assertEquals("Chợp mắt 20 phút", a.label)
    }

    @Test
    fun napAcrossMidnightRingsNextDay() {
        val now = at(23, 50)
        val ring = NapPlanner.ringAt(10, now)
        val a = NapPlanner.alarmFor(10, now)
        assertEquals(0, a.hour)
        assertEquals(5, a.minute)
        assertEquals(ring, nextTriggerAt(a.hour, a.minute, a.daysMask, 0L, now))
    }

    @Test
    fun lateAfternoonNapIsFlagged() {
        assertFalse(NapPlanner.isLate(at(13, 0)))
        assertTrue(NapPlanner.isLate(at(15, 30)))
    }

    @Test
    fun recommendedOptionIsTwentyMinutes() {
        assertTrue(NapPlanner.RECOMMENDED in NapPlanner.OPTIONS)
        assertTrue(NapPlanner.advice(20).startsWith("Khuyên dùng"))
        assertTrue(NapPlanner.advice(30).contains("uể oải"))
    }
}
