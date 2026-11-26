package com.nhom.challengealarm

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LunarCalendarTest {
    @Test
    fun tetMatchesKnownDates() {
        val known = mapOf(
            2020 to Ymd(2020, 1, 25), 2021 to Ymd(2021, 2, 12), 2022 to Ymd(2022, 2, 1),
            2023 to Ymd(2023, 1, 22), 2024 to Ymd(2024, 2, 10), 2025 to Ymd(2025, 1, 29),
            2026 to Ymd(2026, 2, 17), 2027 to Ymd(2027, 2, 6), 2028 to Ymd(2028, 1, 26),
        )
        known.forEach { (year, date) -> assertEquals("Tết $year", date, LunarCalendar.tet(year)) }
    }

    @Test
    fun knownLunarDays() {
        // Giỗ Tổ Hùng Vương (10/3 âm lịch)
        assertEquals(Ymd(2022, 4, 10), LunarCalendar.toSolar(LunarDate(10, 3, 2022)))
        assertEquals(Ymd(2023, 4, 29), LunarCalendar.toSolar(LunarDate(10, 3, 2023)))
        assertEquals(Ymd(2024, 4, 18), LunarCalendar.toSolar(LunarDate(10, 3, 2024)))
        assertEquals(Ymd(2025, 4, 7), LunarCalendar.toSolar(LunarDate(10, 3, 2025)))
        // Trung thu (15/8 âm lịch)
        assertEquals(Ymd(2024, 9, 17), LunarCalendar.toSolar(LunarDate(15, 8, 2024)))
        assertEquals(Ymd(2025, 10, 6), LunarCalendar.toSolar(LunarDate(15, 8, 2025)))
    }

    @Test
    fun leapMonths() {
        // Năm 2023 nhuận tháng 2, năm 2025 nhuận tháng 6.
        assertEquals(LunarDate(1, 2, 2023, leap = true), LunarCalendar.toLunar(Ymd(2023, 3, 22)))
        assertEquals(LunarDate(1, 6, 2025, leap = true), LunarCalendar.toLunar(Ymd(2025, 7, 25)))
        assertEquals(Ymd(2023, 3, 22), LunarCalendar.toSolar(LunarDate(1, 2, 2023, leap = true)))
        // Năm không nhuận tháng đó thì không có ngày dương tương ứng.
        assertNull(LunarCalendar.toSolar(LunarDate(1, 5, 2024, leap = true)))
    }

    @Test
    fun solarLunarRoundTripEveryDay() {
        var d = Ymd(2000, 1, 1)
        val end = Ymd(2035, 12, 31)
        var count = 0
        while (d <= end) {
            val l = LunarCalendar.toLunar(d)
            assertTrue("ngày âm hợp lệ $d -> $l", l.day in 1..30 && l.month in 1..12)
            assertEquals("đi rồi về $d", d, LunarCalendar.toSolar(l))
            d = d.plusDays(1)
            count++
        }
        assertEquals(13149, count)
    }

    @Test
    fun calendarHelpers() {
        assertEquals(Calendar.THURSDAY, Ymd(2026, 10, 22).dayOfWeek)
        assertEquals(Calendar.MONDAY, Ymd(2024, 9, 2).dayOfWeek)
        assertTrue(Ymd(2026, 4, 26).isWeekend)
        assertFalse(Ymd(2026, 4, 27).isWeekend)
        assertEquals(Ymd(2027, 1, 1), Ymd(2026, 12, 31).plusDays(1))
        assertEquals(Ymd(2024, 2, 29), Ymd(2024, 3, 1).plusDays(-1))
        assertEquals("Bính Ngọ", LunarCalendar.yearName(2026))
        assertEquals("Canh Tý", LunarCalendar.yearName(2020))
        assertEquals("Ất Tỵ", LunarCalendar.yearName(2025))
        assertEquals("mùng 1 tháng Giêng", LunarDate(1, 1, 2026).text)
        assertEquals(Ymd(2026, 9, 2), Ymd.fromKey(Ymd(2026, 9, 2).key))
    }
}
