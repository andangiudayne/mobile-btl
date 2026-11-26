package com.nhom.challengealarm

import java.util.Calendar
import java.util.TimeZone
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

/** Một ngày dương lịch (không có giờ). So sánh được theo thứ tự thời gian. */
data class Ymd(val year: Int, val month: Int, val day: Int) : Comparable<Ymd> {
    /** Dạng số yyyymmdd, ví dụ 20260902. */
    val key: Int get() = year * 10_000 + month * 100 + day

    override fun compareTo(other: Ymd): Int = key.compareTo(other.key)

    /** Ngày sau/trước n ngày. */
    fun plusDays(n: Int): Ymd = LunarCalendar.fromJulianDay(LunarCalendar.julianDay(this) + n)

    /** Thứ trong tuần theo Calendar (CN = 1 ... T7 = 7). */
    val dayOfWeek: Int get() = (LunarCalendar.julianDay(this) + 1) % 7 + 1

    val isWeekend: Boolean get() = dayOfWeek == Calendar.SATURDAY || dayOfWeek == Calendar.SUNDAY

    /** "2/9" (ngày/tháng, không có năm). */
    val shortText: String get() = "$day/$month"

    override fun toString(): String = "$day/$month/$year"

    companion object {
        fun of(millis: Long, zone: TimeZone = TimeZone.getDefault()): Ymd {
            val c = Calendar.getInstance(zone).apply { timeInMillis = millis }
            return Ymd(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
        }

        fun fromKey(key: Int): Ymd = Ymd(key / 10_000, key / 100 % 100, key % 100)
    }
}

/** Một ngày âm lịch. leap = tháng nhuận. */
data class LunarDate(val day: Int, val month: Int, val year: Int, val leap: Boolean = false) {
    /** Tên năm theo can chi, ví dụ 2026 là "Bính Ngọ". */
    val yearName: String get() = LunarCalendar.yearName(year)

    /** "mùng 1 tháng Giêng", "15 tháng 8", "10 tháng 3 nhuận". */
    val text: String
        get() {
            val d = if (day <= 10) "mùng $day" else "$day"
            val m = when (month) {
                1 -> "Giêng"
                12 -> "Chạp"
                else -> "$month"
            }
            return "$d tháng $m" + if (leap) " nhuận" else ""
        }

    /** "12/9 Bính Ngọ" (ngày/tháng âm và tên năm). */
    val shortText: String get() = "$day/$month${if (leap) " nhuận" else ""} $yearName"
}

/**
 * Lịch âm Việt Nam (múi giờ UTC+7), theo thuật toán thiên văn của Hồ Ngọc Đức: tính ngày Sóc (trăng mới) và vị trí
 * Mặt Trời để xác định tháng 11 âm lịch (tháng chứa Đông chí) và tháng nhuận (tháng không có trung khí).
 * Dùng để biết ngày Tết Nguyên đán, Giỗ Tổ Hùng Vương rơi vào ngày dương lịch nào. Hàm thuần, test được trên JVM.
 */
object LunarCalendar {
    private const val TZ = 7.0
    private const val SYNODIC_MONTH = 29.530588853
    private const val DR = PI / 180

    private val CAN = listOf("Giáp", "Ất", "Bính", "Đinh", "Mậu", "Kỷ", "Canh", "Tân", "Nhâm", "Quý")
    private val CHI = listOf("Tý", "Sửu", "Dần", "Mão", "Thìn", "Tỵ", "Ngọ", "Mùi", "Thân", "Dậu", "Tuất", "Hợi")

    fun yearName(year: Int): String = "${CAN[Math.floorMod(year + 6, 10)]} ${CHI[Math.floorMod(year + 8, 12)]}"

    /** Số ngày Julius của một ngày dương lịch. */
    fun julianDay(d: Ymd): Int {
        val a = (14 - d.month) / 12
        val y = d.year + 4800 - a
        val m = d.month + 12 * a - 3
        var jd = d.day + (153 * m + 2) / 5 + 365 * y + y / 4 - y / 100 + y / 400 - 32045
        if (jd < 2299161) jd = d.day + (153 * m + 2) / 5 + 365 * y + y / 4 - 32083
        return jd
    }

    fun fromJulianDay(jd: Int): Ymd {
        val b: Int
        val c: Int
        if (jd > 2299160) {
            val a = jd + 32044
            b = (4 * a + 3) / 146097
            c = a - b * 146097 / 4
        } else {
            b = 0
            c = jd + 32082
        }
        val d = (4 * c + 3) / 1461
        val e = c - 1461 * d / 4
        val m = (5 * e + 2) / 153
        val day = e - (153 * m + 2) / 5 + 1
        val month = m + 3 - 12 * (m / 10)
        val year = b * 100 + d - 4800 + m / 10
        return Ymd(year, month, day)
    }

    /** Thời điểm Sóc thứ k tính từ 1/1/1900 (ngày Julius, có phần lẻ). */
    private fun newMoon(k: Int): Double {
        val t = k / 1236.85
        val t2 = t * t
        val t3 = t2 * t
        var jd1 = 2415020.75933 + 29.53058868 * k + 0.0001178 * t2 - 0.000000155 * t3
        jd1 += 0.00033 * sin((166.56 + 132.87 * t - 0.009173 * t2) * DR)
        val m = 359.2242 + 29.10535608 * k - 0.0000333 * t2 - 0.00000347 * t3
        val mpr = 306.0253 + 385.81691806 * k + 0.0107306 * t2 + 0.00001236 * t3
        val f = 21.2964 + 390.67050646 * k - 0.0016528 * t2 - 0.00000239 * t3
        var c1 = (0.1734 - 0.000393 * t) * sin(m * DR) + 0.0021 * sin(2 * DR * m)
        c1 = c1 - 0.4068 * sin(mpr * DR) + 0.0161 * sin(DR * 2 * mpr)
        c1 -= 0.0004 * sin(DR * 3 * mpr)
        c1 = c1 + 0.0104 * sin(DR * 2 * f) - 0.0051 * sin(DR * (m + mpr))
        c1 = c1 - 0.0074 * sin(DR * (m - mpr)) + 0.0004 * sin(DR * (2 * f + m))
        c1 = c1 - 0.0004 * sin(DR * (2 * f - m)) - 0.0006 * sin(DR * (2 * f + mpr))
        c1 = c1 + 0.0010 * sin(DR * (2 * f - mpr)) + 0.0005 * sin(DR * (2 * mpr + m))
        val deltaT = if (t < -11) {
            0.001 + 0.000839 * t + 0.0002261 * t2 - 0.00000845 * t3 - 0.000000081 * t * t3
        } else {
            -0.000278 + 0.000265 * t + 0.000262 * t2
        }
        return jd1 + c1 - deltaT
    }

    /** Kinh độ Mặt Trời (radian, 0..2π) tại ngày Julius jdn. */
    private fun sunLongitude(jdn: Double): Double {
        val t = (jdn - 2451545.0) / 36525
        val t2 = t * t
        val m = 357.52910 + 35999.05030 * t - 0.0001559 * t2 - 0.00000048 * t * t2
        val l0 = 280.46645 + 36000.76983 * t + 0.0003032 * t2
        var dl = (1.914600 - 0.004817 * t - 0.000014 * t2) * sin(DR * m)
        dl += (0.019993 - 0.000101 * t) * sin(DR * 2 * m) + 0.000290 * sin(DR * 3 * m)
        var l = (l0 + dl) * DR
        l -= PI * 2 * floor(l / (PI * 2))
        return l
    }

    /** Cung hoàng đạo (0..11, mỗi cung 30 độ) của Mặt Trời vào đầu ngày dayNumber theo giờ Việt Nam. */
    private fun sunSector(dayNumber: Int): Int = floor(sunLongitude(dayNumber - 0.5 - TZ / 24) / PI * 6).toInt()

    /** Ngày (số Julius) có Sóc thứ k theo giờ Việt Nam: ngày mùng 1 của một tháng âm. */
    private fun newMoonDay(k: Int): Int = floor(newMoon(k) + 0.5 + TZ / 24).toInt()

    /** Ngày bắt đầu tháng 11 âm lịch (tháng có Đông chí) của năm dương yy. */
    private fun lunarMonth11(yy: Int): Int {
        val off = julianDay(Ymd(yy, 12, 31)) - 2415021
        val k = floor(off / SYNODIC_MONTH).toInt()
        val nm = newMoonDay(k)
        return if (sunSector(nm) >= 9) newMoonDay(k - 1) else nm
    }

    /** Vị trí tháng nhuận (tính từ tháng 11) trong năm âm có 13 tháng bắt đầu từ tháng 11 ngày a11. */
    private fun leapMonthOffset(a11: Int): Int {
        val k = floor((a11 - 2415021.076998695) / SYNODIC_MONTH + 0.5).toInt()
        var i = 1
        var arc = sunSector(newMoonDay(k + i))
        var last: Int
        do {
            last = arc
            i++
            arc = sunSector(newMoonDay(k + i))
        } while (arc != last && i < 14)
        return i - 1
    }

    fun toLunar(d: Ymd): LunarDate {
        val dayNumber = julianDay(d)
        val k = floor((dayNumber - 2415021.076998695) / SYNODIC_MONTH).toInt()
        var monthStart = newMoonDay(k + 1)
        if (monthStart > dayNumber) monthStart = newMoonDay(k)
        var a11 = lunarMonth11(d.year)
        var b11 = a11
        var lunarYear: Int
        if (a11 >= monthStart) {
            lunarYear = d.year
            a11 = lunarMonth11(d.year - 1)
        } else {
            lunarYear = d.year + 1
            b11 = lunarMonth11(d.year + 1)
        }
        val lunarDay = dayNumber - monthStart + 1
        val diff = floor((monthStart - a11) / 29.0).toInt()
        var leap = false
        var lunarMonth = diff + 11
        if (b11 - a11 > 365) {
            val leapDiff = leapMonthOffset(a11)
            if (diff >= leapDiff) {
                lunarMonth = diff + 10
                if (diff == leapDiff) leap = true
            }
        }
        if (lunarMonth > 12) lunarMonth -= 12
        if (lunarMonth >= 11 && diff < 4) lunarYear -= 1
        return LunarDate(lunarDay, lunarMonth, lunarYear, leap)
    }

    /** Ngày dương của một ngày âm, hoặc null nếu không có (ví dụ hỏi tháng nhuận trong năm không nhuận tháng đó). */
    fun toSolar(l: LunarDate): Ymd? {
        val a11: Int
        val b11: Int
        if (l.month < 11) {
            a11 = lunarMonth11(l.year - 1)
            b11 = lunarMonth11(l.year)
        } else {
            a11 = lunarMonth11(l.year)
            b11 = lunarMonth11(l.year + 1)
        }
        val k = floor(0.5 + (a11 - 2415021.076998695) / SYNODIC_MONTH).toInt()
        var off = l.month - 11
        if (off < 0) off += 12
        if (b11 - a11 > 365) {
            val leapOff = leapMonthOffset(a11)
            var leapMonth = leapOff - 2
            if (leapMonth < 0) leapMonth += 12
            if (l.leap && l.month != leapMonth) return null
            if (l.leap || off >= leapOff) off += 1
        } else if (l.leap) {
            return null
        }
        val monthStart = newMoonDay(k + off)
        return fromJulianDay(monthStart + l.day - 1)
    }

    /** Ngày mùng 1 Tết Nguyên đán của năm âm lịch (rơi vào năm dương cùng số). */
    fun tet(year: Int): Ymd = toSolar(LunarDate(1, 1, year))!!
}
