package com.nhom.challengealarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NightCheckTest {

    private val hour = 3_600_000L
    private val now = 1_800_000_000_000L

    private fun inputs(
        nextWake: Long? = now + 7 * hour,
        battery: Int? = 80,
        charging: Boolean = false,
        dnd: Boolean = false,
        exact: Boolean = true,
        notif: Boolean = true,
        usage: Boolean = true,
    ) = NightInputs(now, nextWake, battery, charging, dnd, exact, notif, sleepTrackingSupported = true, usageAccess = usage)

    private fun item(items: List<NightCheckItem>, id: String) = items.firstOrNull { it.id == id }

    @Test
    fun everythingFineIsReady() {
        val items = NightCheck.evaluate(inputs())
        assertTrue(NightCheck.ready(items))
        assertTrue(NightCheck.problems(items).isEmpty())
    }

    @Test
    fun noAlarmIsBad() {
        val items = NightCheck.evaluate(inputs(nextWake = null))
        assertEquals(CheckLevel.BAD, item(items, NightCheck.ID_ALARM)!!.level)
        assertFalse(NightCheck.ready(items))
    }

    @Test
    fun alarmDaysAwayIsWarning() {
        // Ví dụ đã bỏ qua chuông sáng mai: chuông kế tiếp còn 2 ngày.
        val items = NightCheck.evaluate(inputs(nextWake = now + 50 * hour))
        assertEquals(CheckLevel.WARN, item(items, NightCheck.ID_ALARM)!!.level)
    }

    @Test
    fun batteryLevels() {
        assertEquals(CheckLevel.BAD, item(NightCheck.evaluate(inputs(battery = 12)), NightCheck.ID_BATTERY)!!.level)
        assertEquals(CheckLevel.WARN, item(NightCheck.evaluate(inputs(battery = 30)), NightCheck.ID_BATTERY)!!.level)
        assertEquals(CheckLevel.OK, item(NightCheck.evaluate(inputs(battery = 12, charging = true)), NightCheck.ID_BATTERY)!!.level)
        assertNull(item(NightCheck.evaluate(inputs(battery = null)), NightCheck.ID_BATTERY))
    }

    @Test
    fun doNotDisturbBlockingAlarmsIsBad() {
        val dnd = item(NightCheck.evaluate(inputs(dnd = true)), NightCheck.ID_DND)!!
        assertEquals(CheckLevel.BAD, dnd.level)
        assertTrue(dnd.fixLabel != null)
    }

    @Test
    fun missingPermissionsAreListedWorstFirst() {
        val items = NightCheck.evaluate(inputs(exact = false, notif = false, usage = false, battery = 30))
        val problems = NightCheck.problems(items)
        assertEquals(CheckLevel.BAD, problems.first().level)
        assertTrue(problems.any { it.id == NightCheck.ID_NOTIF })
        assertTrue(problems.any { it.id == NightCheck.ID_USAGE })
        assertTrue(problems.any { it.id == NightCheck.ID_EXACT })
    }
}
