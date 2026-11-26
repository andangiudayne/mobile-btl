package com.nhom.challengealarm

import org.junit.Assert.assertEquals
import org.junit.Test

class RepeatTest {
    @Test fun noRepeat() = assertEquals("Không lặp lại", repeatSummary(0))
    @Test fun everyDay() = assertEquals("Hằng ngày", repeatSummary(ALL_DAYS_MASK))
    @Test fun weekdays() = assertEquals("Ngày thường", repeatSummary(WEEKDAYS_MASK))
    @Test fun weekend() = assertEquals("Cuối tuần", repeatSummary(WEEKEND_MASK))
    @Test fun custom() = assertEquals("T2, T4", repeatSummary(0b0000101))
    @Test fun customWithSunday() = assertEquals("T3, CN", repeatSummary(0b1000010))
}
