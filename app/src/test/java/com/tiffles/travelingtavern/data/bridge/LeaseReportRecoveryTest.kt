package com.tiffles.travelingtavern.data.bridge

import android.util.Log
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** A lease report lost before the bridge was ready is asked for again (D48). */
@OptIn(ExperimentalCoroutinesApi::class)
class LeaseReportRecoveryTest {

    private val ready = MutableStateFlow(false)
    private var reported = false
    private var foreground = true
    private var asks = 0

    @Before
    fun mockLog() {
        mockkStatic(Log::class)
        every { Log.i(any(), any()) } returns 0
    }

    @After
    fun unmockLog() {
        unmockkStatic(Log::class)
    }

    private fun TestScope.start() {
        LeaseReportRecovery(ready, hasReport = { reported }, inForeground = { foreground }, askAgain = { asks++ }, scope = backgroundScope).start()
        runCurrent()
    }

    @Test
    fun `ready without a report asks the page again`() = runTest {
        start()
        ready.value = true
        runCurrent()

        assertEquals(1, asks)
    }

    @Test
    fun `ready with a report asks nothing`() = runTest {
        start()
        reported = true
        ready.value = true
        runCurrent()

        assertEquals(0, asks)
    }

    @Test
    fun `in the background nothing is asked`() = runTest {
        start()
        foreground = false
        ready.value = true
        runCurrent()

        assertEquals(0, asks)
    }

    @Test
    fun `each page load is checked once`() = runTest {
        start()
        ready.value = true
        runCurrent()
        ready.value = false
        runCurrent()
        ready.value = true
        runCurrent()

        assertEquals(2, asks)
    }
}
