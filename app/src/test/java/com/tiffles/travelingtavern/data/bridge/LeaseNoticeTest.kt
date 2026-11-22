package com.tiffles.travelingtavern.data.bridge

import android.util.Log
import com.tiffles.travelingtavern.domain.model.ModelBanner
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** `leaseNotice` handling (A7) in [LeaseStateStore], fed through [BridgeRepository] as in the app. */
@OptIn(ExperimentalCoroutinesApi::class)
class LeaseNoticeTest {

    private val store = LeaseStateStore(appLeaseVersion = 1)
    private val bridge = BridgeRepository(store)

    @Before
    fun mockLog() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
    }

    @After
    fun unmockLog() {
        unmockkStatic(Log::class)
    }

    private fun leaseState(state: String = "holder", connected: Boolean = false) {
        val reason = if (state == "holder") "null" else "\"lost\""
        bridge.onRawMessage(
            """{"v":1,"type":"leaseState","state":"$state","reason":$reason,"pageId":"t1.l1","epoch":3,""" +
                """"busy":false,"connected":$connected,"origin":"http://192.0.2.1:8000"}"""
        )
    }

    private fun notice(kind: String) = bridge.onRawMessage("""{"v":1,"type":"leaseNotice","kind":"$kind"}""")

    @Test
    fun `notConnected shows the banner for a holder that isn't connected`() {
        leaseState(connected = false)
        notice("notConnected")

        assertEquals(ModelBanner(), store.modelBanner.value)
    }

    @Test
    fun `no banner when the last report already says connected or parked`() {
        leaseState(connected = true)
        notice("notConnected")
        assertNull(store.modelBanner.value)

        leaseState(state = "parked")
        notice("notConnected")
        assertNull(store.modelBanner.value)
    }

    @Test
    fun `connected false alone never shows the banner`() {
        leaseState(connected = false)
        leaseState(connected = false)

        assertNull(store.modelBanner.value)
    }

    @Test
    fun `the banner stays while still not connected`() {
        leaseState(connected = false)
        notice("notConnected")
        leaseState(connected = false)

        assertEquals(ModelBanner(), store.modelBanner.value)
    }

    @Test
    fun `connected true clears the banner`() {
        leaseState(connected = false)
        notice("notConnected")
        leaseState(connected = true)

        assertNull(store.modelBanner.value)
    }

    @Test
    fun `parking clears the banner`() {
        leaseState(connected = false)
        notice("notConnected")
        leaseState(state = "parked")

        assertNull(store.modelBanner.value)
    }

    @Test
    fun `a page start clears the banner`() {
        leaseState(connected = false)
        notice("notConnected")
        bridge.onPageStarted()

        assertNull(store.modelBanner.value)
    }

    @Test
    fun `replyNotStarted reaches a collector`() = runTest {
        val seen = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { store.replyNotStarted.collect { seen += it } }

        notice("replyNotStarted")

        assertEquals(1, seen.size)
        assertNull(store.modelBanner.value)
    }

    @Test
    fun `replyNotStarted with nobody collecting is dropped, not shown later`() = runTest {
        notice("replyNotStarted")

        val seen = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { store.replyNotStarted.collect { seen += it } }

        assertTrue(seen.isEmpty())
    }

    @Test
    fun `an unknown kind is ignored and logged with the kind only`() = runTest {
        val seen = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { store.replyNotStarted.collect { seen += it } }
        leaseState(connected = false)

        notice("somethingNew")

        assertNull(store.modelBanner.value)
        assertTrue(seen.isEmpty())
        verify { Log.d("LeaseStateStore", "Ignored unknown lease notice: somethingNew") }
    }

    @Test
    fun `connect marks the banner connecting once`() {
        leaseState(connected = false)
        notice("notConnected")

        assertNotNull(store.onConnectStarted())
        assertEquals(ModelBanner(connecting = true), store.modelBanner.value)
        assertNull(store.onConnectStarted())
    }

    @Test
    fun `connect does nothing without a banner`() {
        assertNull(store.onConnectStarted())
        assertNull(store.modelBanner.value)
    }

    @Test
    fun `a connect that was tried and failed points to API Connections`() {
        leaseState(connected = false)
        notice("notConnected")
        val token = checkNotNull(store.onConnectStarted())

        store.onConnectFinished(token, tried = true, connected = false)

        assertEquals(ModelBanner(stillNotConnected = true), store.modelBanner.value)
    }

    @Test
    fun `a connect that never reached the page leaves the banner's text`() {
        leaseState(connected = false)
        notice("notConnected")
        val token = checkNotNull(store.onConnectStarted())

        store.onConnectFinished(token, tried = false, connected = false)

        assertEquals(ModelBanner(), store.modelBanner.value)
    }

    @Test
    fun `a banner cleared during connect stays cleared`() {
        leaseState(connected = false)
        notice("notConnected")
        val token = checkNotNull(store.onConnectStarted())
        leaseState(connected = true)

        store.onConnectFinished(token, tried = true, connected = true)

        assertNull(store.modelBanner.value)
    }

    @Test
    fun `a connect from before a page load leaves the new load's banner alone`() {
        leaseState(connected = false)
        notice("notConnected")
        val token = checkNotNull(store.onConnectStarted())
        bridge.onPageStarted()
        leaseState(connected = false)
        notice("notConnected")

        store.onConnectFinished(token, tried = true, connected = false)

        assertEquals(ModelBanner(), store.modelBanner.value)
    }
}
