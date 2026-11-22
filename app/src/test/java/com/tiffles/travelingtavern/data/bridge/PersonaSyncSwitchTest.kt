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

/** Changes to Existing Features / A10: the persona re-read runs while no extension is reported, and stops once one is. */
@OptIn(ExperimentalCoroutinesApi::class)
class PersonaSyncSwitchTest {

    private val ready = MutableStateFlow(false)
    private val store = LeaseStateStore(appLeaseVersion = 1)
    private var sent = 0

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    private fun TestScope.startSwitch() {
        PersonaSyncSwitch(
            bridgeReady = ready,
            extensionReported = store.extensionReported,
            sendExtensionReported = { sent++ },
            scope = backgroundScope
        ).start()
        runCurrent()
    }

    private fun info(extensionLeaseVersion: Int?) = BridgeMessage.LeaseInfo(
        leaseVersion = 1,
        extensionVersion = extensionLeaseVersion?.let { "0.2.0" },
        extensionLeaseVersion = extensionLeaseVersion,
        stVersion = "1.19.1",
        untestedServer = false
    )

    /** A page load: both the bridge and the lease report start over, as `BridgeRepository.onPageStarted` does. */
    private fun pageStarted() {
        ready.value = false
        store.onPageStarted()
    }

    @Test
    fun `before any leaseInfo the re-read stays on`() = runTest {
        startSwitch()
        ready.value = true
        runCurrent()

        assertEquals(0, sent)
    }

    @Test
    fun `no extension reported keeps the re-read on`() = runTest {
        startSwitch()
        ready.value = true
        store.onLeaseInfo(info(extensionLeaseVersion = null))
        runCurrent()

        assertEquals(0, sent)
    }

    @Test
    fun `an extension reported after the bridge is ready turns the re-read off`() = runTest {
        startSwitch()
        ready.value = true
        runCurrent()
        store.onLeaseInfo(info(extensionLeaseVersion = 1))
        runCurrent()

        assertEquals(1, sent)
    }

    @Test
    fun `an extension reported before the bridge is ready is sent once the bridge is ready`() = runTest {
        startSwitch()
        store.onLeaseInfo(info(extensionLeaseVersion = 1))
        runCurrent()
        assertEquals(0, sent)

        ready.value = true
        runCurrent()

        assertEquals(1, sent)
    }

    @Test
    fun `older and newer extensions count as reported`() = runTest {
        startSwitch()
        ready.value = true
        store.onLeaseInfo(info(extensionLeaseVersion = 0))
        runCurrent()
        pageStarted()
        runCurrent()
        ready.value = true
        store.onLeaseInfo(info(extensionLeaseVersion = 2))
        runCurrent()

        assertEquals(2, sent)
    }

    @Test
    fun `a repeated leaseInfo on the same page sends once`() = runTest {
        startSwitch()
        ready.value = true
        store.onLeaseInfo(info(extensionLeaseVersion = 1))
        runCurrent()
        store.onLeaseInfo(info(extensionLeaseVersion = 1))
        runCurrent()

        assertEquals(1, sent)
    }

    @Test
    fun `every page load is sent again`() = runTest {
        startSwitch()
        ready.value = true
        store.onLeaseInfo(info(extensionLeaseVersion = 1))
        runCurrent()

        pageStarted()
        runCurrent()
        ready.value = true
        runCurrent()
        assertEquals("not before the new load's leaseInfo", 1, sent)

        store.onLeaseInfo(info(extensionLeaseVersion = 1))
        runCurrent()
        assertEquals(2, sent)
    }

    @Test
    fun `a new load without the extension keeps the re-read on`() = runTest {
        startSwitch()
        ready.value = true
        store.onLeaseInfo(info(extensionLeaseVersion = 1))
        runCurrent()

        pageStarted()
        ready.value = true
        store.onLeaseInfo(info(extensionLeaseVersion = null))
        runCurrent()

        assertEquals(1, sent)
    }
}
