package com.tiffles.travelingtavern.data.webview

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LoadWatchdogTest {

    private fun TestScope.watchdog() = LoadWatchdog(scope = backgroundScope, timeoutMs = 15_000L)

    @Test
    fun `times out when the server never answers`() = runTest {
        var timeouts = 0
        val watchdog = watchdog()

        watchdog.onLoadStarted { timeouts++ }
        advanceTimeBy(14_999L)
        runCurrent()
        assertEquals(0, timeouts)

        advanceTimeBy(2L)
        runCurrent()
        assertEquals(1, timeouts)
    }

    @Test
    fun `an answer before the deadline cancels the timeout`() = runTest {
        var timeouts = 0
        val watchdog = watchdog()

        watchdog.onLoadStarted { timeouts++ }
        advanceTimeBy(10_000L)
        watchdog.onServerAnswered()
        advanceTimeBy(60_000L)
        runCurrent()

        assertEquals(0, timeouts)
    }

    @Test
    fun `a new load restarts the clock and only the latest load can time out`() = runTest {
        val fired = mutableListOf<String>()
        val watchdog = watchdog()

        watchdog.onLoadStarted { fired += "first" }
        advanceTimeBy(10_000L)
        watchdog.onLoadStarted { fired += "second" }
        advanceTimeBy(10_000L)
        runCurrent()
        assertEquals(emptyList<String>(), fired)

        advanceTimeBy(5_001L)
        runCurrent()
        assertEquals(listOf("second"), fired)
    }

    @Test
    fun `cancel stops a pending timeout`() = runTest {
        var timeouts = 0
        val watchdog = watchdog()

        watchdog.onLoadStarted { timeouts++ }
        watchdog.cancel()
        advanceTimeBy(60_000L)
        runCurrent()

        assertEquals(0, timeouts)
    }
}
