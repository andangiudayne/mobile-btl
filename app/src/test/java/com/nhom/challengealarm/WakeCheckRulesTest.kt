package com.nhom.challengealarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeCheckRulesTest {

    private val min = 60_000L
    private val askAt = 1_000_000_000L

    private fun waiting() = WakeCheckState(alarmId = 1, logId = 7, askAt = askAt, deadline = 0L)
    private fun asking() = WakeCheckState(alarmId = 1, logId = 7, askAt = askAt, deadline = askAt + WakeCheckRules.ANSWER_WINDOW_MS)

    @Test
    fun checksOnlyWhenEnabledAndNotEmergency() {
        assertTrue(WakeCheckRules.shouldCheck(WakeMethod.MATH, 5, notificationsAllowed = true))
        assertTrue(WakeCheckRules.shouldCheck(WakeMethod.AWAKE, 3, notificationsAllowed = true))
        assertFalse(WakeCheckRules.shouldCheck(WakeMethod.MATH, 0, notificationsAllowed = true))
        assertFalse(WakeCheckRules.shouldCheck(WakeMethod.EMERGENCY, 5, notificationsAllowed = true))
        // Không có thông báo thì không hỏi được, nên không hẹn (tránh chuông reo lại oan khi người dùng đang thức).
        assertFalse(WakeCheckRules.shouldCheck(WakeMethod.MATH, 5, notificationsAllowed = false))
    }

    @Test
    fun stepsAfterRestart() {
        assertEquals(WakeCheckRules.Step.WAIT, WakeCheckRules.stepAt(waiting(), askAt - min))
        assertEquals(WakeCheckRules.Step.ASK, WakeCheckRules.stepAt(waiting(), askAt + min))
        assertEquals(WakeCheckRules.Step.ASK, WakeCheckRules.stepAt(asking(), askAt + 30_000L))
        assertEquals(WakeCheckRules.Step.TIMEOUT, WakeCheckRules.stepAt(asking(), askAt + 2 * min))
        assertEquals(WakeCheckRules.Step.DROP, WakeCheckRules.stepAt(asking(), askAt + 61 * min))
    }

    @Test
    fun cannotConfirmBeforeBeingAsked() {
        // Bấm "Tôi đã dậy" trước khi app hỏi thì không tính: tránh bấm sẵn rồi ngủ tiếp.
        assertFalse(WakeCheckRules.canConfirm(waiting()))
        assertFalse(WakeCheckRules.canConfirm(null))
        assertTrue(WakeCheckRules.canConfirm(asking()))
    }

    @Test
    fun optionsStartWithOff() {
        assertEquals(0, WakeCheckRules.OPTIONS.first())
        assertTrue(WakeCheckRules.OPTIONS.all { it in 0..10 })
    }
}
