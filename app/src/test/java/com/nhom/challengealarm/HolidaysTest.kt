package com.nhom.challengealarm

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HolidaysTest {
    private fun dates(year: Int) = VnHolidays.forYear(year).map { it.date }

    private fun millis(y: Int, m: Int, d: Int, h: Int, min: Int): Long = Calendar.getInstance().apply {
        clear()
        set(y, m - 1, d, h, min)
    }.timeInMillis

    @Test
    fun publicHolidays2026() {
        val days = dates(2026)
        // Tết Nguyên đán Bính Ngọ: mùng 1 là 17/2, nghỉ từ 16/2 đến 20/2.
        (16..20).forEach { assertTrue("Tết $it/2", Ymd(2026, 2, it) in days) }
        assertFalse(Ymd(2026, 2, 21) in days)
        // Giỗ Tổ 26/4 là Chủ nhật: nghỉ bù thứ Hai 27/4.
        assertTrue(Ymd(2026, 4, 26) in days)
        assertTrue(VnHolidays.on(Ymd(2026, 4, 27))!!.makeUp)
        assertTrue(Ymd(2026, 4, 30) in days && Ymd(2026, 5, 1) in days && Ymd(2026, 1, 1) in days)
        assertTrue(Ymd(2026, 9, 2) in days && Ymd(2026, 9, 1) in days)
    }

    @Test
    fun nationalDayMatchesRecentYears() {
        // 2024: 2/9 thứ Hai, nghỉ thêm 3/9. 2025: 2/9 thứ Ba, nghỉ thêm 1/9.
        assertTrue(Ymd(2024, 9, 3) in dates(2024))
        assertTrue(Ymd(2025, 9, 1) in dates(2025))
        // 2023: 2/9 thứ Bảy, nghỉ 1/9 và nghỉ bù thứ Hai 4/9.
        assertTrue(Ymd(2023, 9, 1) in dates(2023))
        assertTrue(Ymd(2023, 9, 4) in dates(2023))
    }

    @Test
    fun weekdayAlarmSkipsHolidaysAndComesBack() {
        val off: (Ymd) -> Boolean = { DaysOff.isOff(it, emptyList()) }
        // Tối thứ Hai 31/8/2026: thứ Ba 1/9 và thứ Tư 2/9 nghỉ Quốc khánh, chuông reo lại thứ Năm 3/9.
        val now = millis(2026, 8, 31, 20, 0)
        assertEquals(millis(2026, 9, 3, 6, 30), nextTriggerAt(6, 30, WEEKDAYS_MASK, 0L, now, off))
        // Không bật bỏ qua ngày nghỉ thì vẫn reo thứ Ba như cũ.
        assertEquals(millis(2026, 9, 1, 6, 30), nextTriggerAt(6, 30, WEEKDAYS_MASK, 0L, now))
        // Tết: chiều 13/2/2026 (thứ Sáu) -> qua Tết (16 đến 20/2) -> thứ Hai 23/2.
        assertEquals(millis(2026, 2, 23, 6, 30), nextTriggerAt(6, 30, WEEKDAYS_MASK, 0L, millis(2026, 2, 13, 18, 0), off))
        // Chuông một lần không xét ngày nghỉ.
        assertEquals(millis(2026, 9, 2, 6, 30), nextTriggerAt(6, 30, 0, 0L, millis(2026, 9, 1, 21, 0), off))
    }

    @Test
    fun vacationsAreSkippedToo() {
        val holiday = listOf(Vacation(Ymd(2026, 12, 21), Ymd(2027, 1, 3), "Nghỉ học"))
        val off: (Ymd) -> Boolean = { DaysOff.isOff(it, holiday) }
        val now = millis(2026, 12, 18, 21, 0)
        assertEquals(millis(2027, 1, 4, 6, 30), nextTriggerAt(6, 30, WEEKDAYS_MASK, 0L, now, off))
        assertEquals("Nghỉ học", DaysOff.reason(Ymd(2026, 12, 25), holiday))
        // Ngày lễ đặt tên trước kỳ nghỉ.
        assertEquals("Tết Dương lịch", DaysOff.reason(Ymd(2027, 1, 1), holiday))
        assertNull(DaysOff.reason(Ymd(2026, 12, 18), holiday))
        // Ngày nghỉ gần nhất.
        assertEquals(Ymd(2026, 12, 21) to "Nghỉ học", DaysOff.next(Ymd(2026, 12, 1), vacations = holiday))
    }

    @Test
    fun vacationStorageRoundTrip() {
        val list = listOf(
            Vacation(Ymd(2026, 12, 21), Ymd(2027, 1, 3), "Nghỉ học"),
            Vacation(Ymd(2027, 6, 1), Ymd(2027, 6, 1), "Du lịch"),
        )
        assertEquals(list, DaysOff.decode(DaysOff.encode(list)))
        assertEquals(emptyList<Vacation>(), DaysOff.decode(""))
        assertEquals(1, DaysOff.decode("abc;20270601-20270601|Du lịch").size)
        // Thêm kỳ nghỉ: bỏ kỳ đã qua, sắp theo ngày.
        val added = DaysOff.add(list, Vacation(Ymd(2026, 11, 1), Ymd(2026, 11, 2), "Ốm"), today = Ymd(2026, 12, 25))
        assertEquals(listOf("Nghỉ học", "Du lịch"), added.map { it.name })
    }

    @Test
    fun alarmWithHolidayOptionUsesSchedulerRule() {
        DaysOff.vacations = emptyList()
        val alarm = AlarmEntity(hour = 6, minute = 30, daysMask = WEEKDAYS_MASK, skipHolidays = true)
        val now = millis(2026, 4, 29, 20, 0) // tối thứ Tư 29/4
        assertEquals(millis(2026, 5, 4, 6, 30), AlarmScheduler.nextTriggerMillis(alarm, now))
        assertEquals(millis(2026, 4, 30, 6, 30), AlarmScheduler.nextIgnoringDaysOff(alarm, now))
        assertEquals(millis(2026, 4, 30, 6, 30), AlarmScheduler.nextTriggerMillis(alarm.copy(skipHolidays = false), now))
    }

    @Test
    fun holidaysGroupedForDisplay() {
        val groups = groupHolidays(VnHolidays.forYear(2026))
        val tet = groups.first { it.name == "Tết Nguyên đán" }
        assertEquals(Ymd(2026, 2, 16), tet.start)
        assertEquals(5, tet.days)
        assertEquals("Mùng 1 Tết Bính Ngọ: T3 17/2", tet.note)
        val national = groups.first { it.name == "Quốc khánh" }
        assertEquals("T3 1/9 – T4 2/9", national.rangeText)
        assertEquals("Ngày mùng 10 tháng 3 âm lịch", groups.first { it.name == "Giỗ Tổ Hùng Vương" }.note)
        assertTrue(groups.any { it.name == "Nghỉ bù Giỗ Tổ Hùng Vương" && it.start == Ymd(2026, 4, 27) })
        // Danh sách sắp tới trải qua năm mới.
        val upcoming = VnHolidays.upcoming(Ymd(2026, 12, 1), 60)
        assertEquals(Ymd(2027, 1, 1), upcoming.first().date)
        assertEquals("T6", Ymd(2027, 1, 1).weekdayShort)
        assertEquals(31, daysBetween(Ymd(2026, 12, 1), Ymd(2027, 1, 1)))
    }
}
