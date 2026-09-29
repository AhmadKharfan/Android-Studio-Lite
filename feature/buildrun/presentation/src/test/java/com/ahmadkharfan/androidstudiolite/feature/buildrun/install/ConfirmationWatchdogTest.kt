package com.ahmadkharfan.androidstudiolite.feature.buildrun.install

import com.ahmadkharfan.androidstudiolite.feature.buildrun.install.ConfirmationWatchdog.Action
import org.junit.Assert.assertEquals
import org.junit.Test

class ConfirmationWatchdogTest {

    private val watchdog = ConfirmationWatchdog(graceTicks = 3, maxReopens = 2)

    private fun checks(count: Int, foreground: Boolean = true, pending: Boolean = true) =
        List(count) { watchdog.check(foreground, pending) }

    @Test
    fun `a prompt that is showing keeps the install waiting`() {
        assertEquals(List(10) { Action.Wait }, checks(10, foreground = false))
    }

    @Test
    fun `a vanished prompt is reopened after the grace period`() {
        assertEquals(listOf(Action.Wait, Action.Wait, Action.Reopen), checks(3))
    }

    @Test
    fun `the prompt coming back resets the grace period`() {
        checks(2)
        watchdog.check(appResumed = false, sessionPending = true)

        assertEquals(listOf(Action.Wait, Action.Wait, Action.Reopen), checks(3))
    }

    @Test
    fun `after the allowed reopens the install gives up`() {
        val actions = checks(9)

        assertEquals(Action.Reopen, actions[2])
        assertEquals(Action.Reopen, actions[5])
        assertEquals(Action.GiveUp, actions[8])
    }

    @Test
    fun `a finished session never triggers a reopen`() {
        assertEquals(List(10) { Action.Wait }, checks(10, pending = false))
    }
}
