package com.nhom.challengealarm

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Test

/** "Bỏ qua lần reo tới": chỉ bỏ đúng một lần, sau đó chuông reo lại như cũ. */
class SkipNextTest {

    // Thứ Bảy 03/10/2026, 10:00
    private val now: Long = at(3, 10, 0)

    private fun at(day: Int, hour: Int, minute: Int): Long = Calendar.getInstance().apply {
        clear()
        set(2026, Calendar.OCTOBER, day, hour, minute, 0)
    }.timeInMillis

    private fun mask(vararg bits: Int): Int = bits.fold(0) { acc, b -> acc or (1 shl b) }

    @Test
    fun noSkipKeepsOldBehaviour() {
        assertEquals(at(4, 7, 0), nextTriggerAt(7, 0, ALL_DAYS_MASK, 0L, now))
        assertEquals(at(3, 11, 0), nextTriggerAt(11, 0, 0, 0L, now))
    }

    @Test
    fun dailyAlarmSkipsTomorrowAndRingsTheDayAfter() {
        val tomorrow = nextTriggerAt(7, 0, ALL_DAYS_MASK, 0L, now)!!
        assertEquals(at(4, 7, 0), tomorrow)
        assertEquals(at(5, 7, 0), nextTriggerAt(7, 0, ALL_DAYS_MASK, tomorrow, now))
    }

    @Test
    fun weekdayAlarmSkippingMondayGoesToTuesday() {
        // Chuông ngày thường; lần kế tiếp là Thứ Hai 05/10. Bỏ qua (nghỉ lễ) thì reo Thứ Ba 06/10.
        val monday = at(5, 6, 30)
        assertEquals(monday, nextTriggerAt(6, 30, WEEKDAYS_MASK, 0L, now))
        assertEquals(at(6, 6, 30), nextTriggerAt(6, 30, WEEKDAYS_MASK, monday, now))
    }

    @Test
    fun weeklyAlarmSkipsAWholeWeek() {
        // Chỉ reo Thứ Hai: bỏ qua Thứ Hai 05/10 thì lần sau là Thứ Hai 12/10 (tìm từ mốc bỏ qua, không phải từ hôm nay).
        val monday = at(5, 7, 0)
        assertEquals(at(12, 7, 0), nextTriggerAt(7, 0, mask(0), monday, now))
    }

    @Test
    fun pastSkipHasNoEffect() {
        val longAgo = at(1, 7, 0)
        assertEquals(at(4, 7, 0), nextTriggerAt(7, 0, ALL_DAYS_MASK, longAgo, now))
    }
}
