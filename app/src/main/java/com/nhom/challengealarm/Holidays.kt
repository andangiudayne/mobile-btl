package com.nhom.challengealarm

import java.util.Calendar
import java.util.concurrent.ConcurrentHashMap

/** Một ngày nghỉ: tên lễ, có phải ngày âm lịch không (để hiện thêm ngày âm), có phải ngày nghỉ bù không. */
data class DayOff(val date: Ymd, val name: String, val lunar: LunarDate? = null, val makeUp: Boolean = false)

/** Kỳ nghỉ người dùng tự thêm (nghỉ học, nghỉ phép, du lịch...), tính cả hai đầu. */
data class Vacation(val start: Ymd, val end: Ymd, val name: String) {
    operator fun contains(d: Ymd): Boolean = d >= start && d <= end

    /** "20/12 – 2/1". */
    val rangeText: String get() = if (start == end) start.shortText else "${start.shortText} – ${end.shortText}"
}

/**
 * Ngày nghỉ lễ của Việt Nam theo Bộ luật Lao động 2019 (Điều 112), tính cho từng năm dương lịch:
 *   - Tết Dương lịch: 1/1.
 *   - Tết Nguyên đán: 5 ngày, từ ngày cuối năm âm (29 hoặc 30 Tết) đến mùng 4 (theo lịch âm, mỗi năm một khác).
 *   - Giỗ Tổ Hùng Vương: 10/3 âm lịch.
 *   - Ngày Chiến thắng 30/4 và Quốc tế Lao động 1/5.
 *   - Quốc khánh: 2/9 và một ngày liền kề (chọn ngày để nối với cuối tuần).
 *   - Ngày lễ một ngày trùng thứ Bảy/Chủ nhật thì được nghỉ bù vào ngày thường kế tiếp.
 * Lịch chính thức mỗi năm do Chính phủ công bố có thể dời vài ngày (hoán đổi ngày làm việc); người dùng thêm
 * "Kỳ nghỉ của tôi" để bù. Hàm thuần, kết quả mỗi năm được nhớ lại để tính giờ chuông cho nhanh.
 */
object VnHolidays {
    private val cache = ConcurrentHashMap<Int, List<DayOff>>()

    fun forYear(year: Int): List<DayOff> = cache.getOrPut(year) { compute(year) }

    private fun compute(year: Int): List<DayOff> {
        val days = mutableListOf<DayOff>()

        // Ngày lễ một ngày (có nghỉ bù nếu trùng cuối tuần).
        val single = mutableListOf(
            DayOff(Ymd(year, 1, 1), "Tết Dương lịch"),
            LunarDate(10, 3, year).let { l -> DayOff(LunarCalendar.toSolar(l)!!, "Giỗ Tổ Hùng Vương", l) },
            DayOff(Ymd(year, 4, 30), "Ngày Chiến thắng"),
            DayOff(Ymd(year, 5, 1), "Quốc tế Lao động"),
        )
        val national = Ymd(year, 9, 2)
        single += DayOff(national, "Quốc khánh")
        single += DayOff(national.plusDays(nationalDayExtraOffset(national)), "Quốc khánh")

        // Tết Nguyên đán: ngày cuối năm cũ + mùng 1 đến mùng 4.
        val tet = LunarCalendar.tet(year)
        for (i in -1..3) {
            val d = tet.plusDays(i)
            days += DayOff(d, if (i < 0) "Tết Nguyên đán (giao thừa)" else "Tết Nguyên đán", LunarCalendar.toLunar(d))
        }
        days += single

        // Nghỉ bù: lễ một ngày rơi vào cuối tuần thì nghỉ ngày thường kế tiếp chưa phải ngày nghỉ.
        val taken = days.map { it.date }.toMutableSet()
        single.filter { it.date.isWeekend }.sortedBy { it.date }.forEach { h ->
            var d = h.date.plusDays(1)
            while (d.isWeekend || d in taken) d = d.plusDays(1)
            taken += d
            days += DayOff(d, "Nghỉ bù ${h.name}", makeUp = true)
        }
        return days.distinctBy { it.date }.sortedBy { it.date }
    }

    /**
     * Ngày nghỉ Quốc khánh thứ hai là ngày liền trước hay liền sau 2/9: chọn để nối với cuối tuần
     * (2/9 thứ Hai thì nghỉ thêm thứ Ba, thứ Ba thì thêm thứ Hai, thứ Năm thì thêm thứ Sáu...).
     */
    private fun nationalDayExtraOffset(d: Ymd): Int = when (d.dayOfWeek) {
        Calendar.MONDAY, Calendar.THURSDAY, Calendar.SUNDAY -> 1
        else -> -1
    }

    /** Ngày lễ (nếu có) của một ngày. */
    fun on(d: Ymd): DayOff? = forYear(d.year).firstOrNull { it.date == d }

    /** Các ngày lễ từ from (tính cả) trong days ngày tới. */
    fun upcoming(from: Ymd, days: Int): List<DayOff> {
        val to = from.plusDays(days)
        return (from.year..to.year).flatMap { forYear(it) }.filter { it.date >= from && it.date <= to }
    }
}

/**
 * Ngày nghỉ dùng khi tính giờ chuông: ngày lễ Việt Nam cộng các kỳ nghỉ người dùng tự thêm.
 * vacations được nạp từ máy (DaysOffStore) khi app hoặc receiver chạy; test thì truyền thẳng vào.
 */
object DaysOff {
    @Volatile var vacations: List<Vacation> = emptyList()

    /** Lý do nghỉ của một ngày (tên lễ hoặc tên kỳ nghỉ), null nếu là ngày thường. */
    fun reason(d: Ymd, vacations: List<Vacation> = this.vacations): String? =
        VnHolidays.on(d)?.name ?: vacations.firstOrNull { d in it }?.name

    fun isOff(d: Ymd, vacations: List<Vacation> = this.vacations): Boolean = reason(d, vacations) != null

    /** Ngày nghỉ gần nhất từ from (tính cả) trong days ngày tới: lễ hoặc ngày đầu của kỳ nghỉ riêng. */
    fun next(from: Ymd, days: Int = 120, vacations: List<Vacation> = this.vacations): Pair<Ymd, String>? {
        var d = from
        repeat(days + 1) {
            reason(d, vacations)?.let { return d to it }
            d = d.plusDays(1)
        }
        return null
    }

    /** Lưu kỳ nghỉ thành chuỗi: "20261220-20270102|Nghỉ học;..." (tên không chứa '|' hay ';'). */
    fun encode(list: List<Vacation>): String =
        list.joinToString(";") { "${it.start.key}-${it.end.key}|${it.name.replace('|', ' ').replace(';', ' ')}" }

    fun decode(text: String): List<Vacation> = text.split(';').mapNotNull { part ->
        runCatching {
            val (range, name) = part.split('|', limit = 2)
            val (a, b) = range.split('-').map { it.trim().toInt() }
            val start = Ymd.fromKey(a)
            val end = Ymd.fromKey(b)
            Vacation(minOf(start, end), maxOf(start, end), name.ifBlank { "Kỳ nghỉ" })
        }.getOrNull()
    }

    /** Thêm kỳ nghỉ, bỏ các kỳ đã kết thúc trước today, sắp theo ngày bắt đầu. */
    fun add(list: List<Vacation>, v: Vacation, today: Ymd): List<Vacation> =
        (list + v).filter { it.end >= today }.distinct().sortedBy { it.start }
}

/** "T4" cho thứ của một ngày (theo DAY_SHORT: T2...CN). */
val Ymd.weekdayShort: String get() = DAY_SHORT[(dayOfWeek + 5) % 7]

/** Số ngày từ a đến b (b sau a thì dương). */
fun daysBetween(a: Ymd, b: Ymd): Int = LunarCalendar.julianDay(b) - LunarCalendar.julianDay(a)

/** Một dòng trong danh sách ngày lễ: các ngày liền nhau cùng tên gộp lại (Tết 5 ngày, Quốc khánh 2 ngày). */
data class HolidayGroup(val name: String, val start: Ymd, val end: Ymd, val note: String? = null) {
    val days: Int get() = daysBetween(start, end) + 1

    /** "T2 16/2 – T6 20/2" hoặc "T5 30/4". */
    val rangeText: String
        get() = if (start == end) "${start.weekdayShort} ${start.shortText}"
        else "${start.weekdayShort} ${start.shortText} – ${end.weekdayShort} ${end.shortText}"
}

fun groupHolidays(list: List<DayOff>): List<HolidayGroup> {
    val out = mutableListOf<HolidayGroup>()
    for (d in list.sortedBy { it.date }) {
        val base = d.name.substringBefore(" (")
        val last = out.lastOrNull()
        if (last != null && last.name == base && last.end.plusDays(1) == d.date) {
            out[out.lastIndex] = last.copy(end = d.date)
        } else {
            out += HolidayGroup(base, d.date, d.date)
        }
    }
    // Ghi chú âm lịch: ngày mùng 1 Tết, Giỗ Tổ 10/3 âm lịch.
    return out.map { g ->
        val lunarDays = list.filter { it.date >= g.start && it.date <= g.end && it.lunar != null }
        val tet = lunarDays.firstOrNull { it.lunar!!.day == 1 && it.lunar.month == 1 }
        when {
            tet != null -> g.copy(note = "Mùng 1 Tết ${tet.lunar!!.yearName}: ${tet.date.weekdayShort} ${tet.date.shortText}")
            lunarDays.isNotEmpty() -> g.copy(note = "Ngày ${lunarDays.first().lunar!!.text} âm lịch")
            else -> g
        }
    }
}
