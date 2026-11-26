package com.nhom.challengealarm

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetContentTest {
    private fun millis(y: Int, m: Int, d: Int, h: Int, min: Int): Long = Calendar.getInstance().apply {
        clear()
        set(y, m - 1, d, h, min)
    }.timeInMillis

    private val school = AlarmEntity(id = 1, hour = 6, minute = 30, daysMask = WEEKDAYS_MASK, label = "Đi học", skipHolidays = true)

    @Test
    fun showsNextAlarmAfterHolidayAndTheHoliday() {
        DaysOff.vacations = emptyList()
        val info = WidgetContent.build(listOf(school), emptyList(), millis(2026, 8, 31, 20, 0))
        assertEquals("06:30", info.time)
        assertEquals("T5 3/9 · Đi học", info.detail)
        assertEquals("Nghỉ T3 1/9: Quốc khánh", info.dayOff)
        assertEquals("Bắt đầu chuỗi ngày dậy đúng giờ", info.streak)
    }

    @Test
    fun tomorrowAndNoAlarm() {
        DaysOff.vacations = emptyList()
        val info = WidgetContent.build(listOf(school.copy(skipHolidays = false)), emptyList(), millis(2026, 10, 22, 21, 0))
        assertEquals("Ngày mai · Đi học", info.detail)
        assertNull(info.dayOff)
        val none = WidgetContent.build(listOf(school.copy(enabled = false)), emptyList(), millis(2026, 10, 22, 21, 0))
        assertEquals("--:--", none.time)
        assertEquals("Chưa có chuông nào đang bật", none.detail)
    }

    @Test
    fun streakComesFromHistory() {
        val now = millis(2026, 10, 22, 21, 0)
        val history = DemoData.generate(now, schoolAlarmId = 1, weekendAlarmId = 2)
        val info = WidgetContent.build(listOf(school), history.wakeLogs, now)
        assertTrue(info.streak, info.streak.endsWith("ngày dậy đúng giờ liên tiếp"))
    }
}
