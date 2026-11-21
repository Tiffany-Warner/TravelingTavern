package com.tiffles.travelingtavern.data.bridge

import android.util.Log
import com.tiffles.travelingtavern.domain.model.LeaseStatus
import com.tiffles.travelingtavern.domain.model.ParkReason
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LeaseStateStoreTest {

    private val store = LeaseStateStore(appLeaseVersion = 1)

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

    private fun state(state: String, reason: String? = null, busy: Boolean = false, connected: Boolean = true) =
        BridgeMessage.LeaseState(
            state = state,
            reason = reason,
            pageId = "t1.l1",
            epoch = 3L,
            busy = busy,
            connected = connected,
            origin = "http://192.0.2.1:8000"
        )

    private fun info(leaseVersion: Int) = BridgeMessage.LeaseInfo(
        leaseVersion = leaseVersion,
        extensionVersion = null,
        extensionLeaseVersion = null,
        stVersion = "1.19.1",
        untestedServer = false
    )

    @Test
    fun `not reported until the page's first leaseState`() {
        assertEquals(LeaseStatus.NotReported, store.status.value)
        assertNull(store.lastState.value)
        assertNull(store.info.value)
    }

    @Test
    fun `holder carries busy and connected`() {
        store.onLeaseState(state("holder", busy = true, connected = false))

        assertEquals(LeaseStatus.Holder(busy = true, connected = false), store.status.value)
    }

    @Test
    fun `holder ignores a reason`() {
        store.onLeaseState(state("holder", reason = "held"))

        assertEquals(LeaseStatus.Holder(busy = false, connected = true), store.status.value)
    }

    @Test
    fun `every known park reason`() {
        val expected = mapOf(
            "held" to ParkReason.Held,
            "lost" to ParkReason.Lost,
            "claiming" to ParkReason.Claiming,
            "waitingForReply" to ParkReason.WaitingForReply,
            "lostRace" to ParkReason.LostRace,
            "unreadable" to ParkReason.Unreadable,
            "unknownFormat" to ParkReason.UnknownFormat,
            "reloadPending" to ParkReason.ReloadPending
        )
        expected.forEach { (reason, parkReason) ->
            store.onLeaseState(state("parked", reason))
            assertEquals(reason, LeaseStatus.Parked(parkReason), store.status.value)
        }
    }

    @Test
    fun `an unknown or missing reason is kept`() {
        store.onLeaseState(state("parked", "napping"))
        assertEquals(LeaseStatus.Parked(ParkReason.Unknown("napping")), store.status.value)

        store.onLeaseState(state("parked", reason = null))
        assertEquals(LeaseStatus.Parked(ParkReason.Unknown(null)), store.status.value)
    }

    @Test
    fun `an unknown state counts as parked`() {
        store.onLeaseState(state("sleeping", reason = null))

        assertEquals(LeaseStatus.Parked(ParkReason.Unknown("sleeping")), store.status.value)
    }

    @Test
    fun `keeps the last leaseState and leaseInfo`() {
        val holder = state("holder")
        store.onLeaseState(holder)
        store.onLeaseInfo(info(1))

        assertEquals(holder, store.lastState.value)
        assertEquals(info(1), store.info.value)
    }

    @Test
    fun `a lease version other than the app's is a mismatch over the page's state`() {
        store.onLeaseState(state("holder"))
        store.onLeaseInfo(info(2))

        assertEquals(LeaseStatus.VersionMismatch, store.status.value)

        store.onLeaseState(state("parked", "held"))
        assertEquals(LeaseStatus.VersionMismatch, store.status.value)
    }

    @Test
    fun `a mismatch shows even before the first leaseState`() {
        store.onLeaseInfo(info(0))

        assertEquals(LeaseStatus.VersionMismatch, store.status.value)
    }

    @Test
    fun `a matching lease version leaves the page's state`() {
        store.onLeaseState(state("parked", "held"))
        store.onLeaseInfo(info(1))

        assertEquals(LeaseStatus.Parked(ParkReason.Held), store.status.value)
    }

    @Test
    fun `a page load resets what the previous load reported`() {
        store.onLeaseState(state("holder"))
        store.onLeaseInfo(info(2))

        store.onPageStarted()

        assertEquals(LeaseStatus.NotReported, store.status.value)
        assertNull(store.lastState.value)
        assertNull(store.info.value)
    }

    @Test
    fun `no document start wins over everything and survives page loads`() {
        store.onLeaseState(state("holder"))
        store.onLeaseInfo(info(2))

        store.onDocumentStartUnsupported()
        assertEquals(LeaseStatus.NoDocumentStart, store.status.value)

        store.onPageStarted()
        assertEquals(LeaseStatus.NoDocumentStart, store.status.value)
    }

    @Test
    fun `bridge repository feeds the store and resets it on a page load`() {
        val repository = BridgeRepository(store)
        repository.onRawMessage(
            """{"v":1,"type":"leaseState","state":"parked","reason":"lostRace","pageId":"t1.l1","epoch":null,""" +
                """"busy":false,"connected":true,"origin":"http://192.0.2.1:8000"}"""
        )
        repository.onRawMessage(
            """{"v":1,"type":"leaseInfo","leaseVersion":1,"extensionVersion":null,""" +
                """"extensionLeaseVersion":null,"stVersion":null,"untestedServer":true}"""
        )

        assertEquals(LeaseStatus.Parked(ParkReason.LostRace), store.status.value)
        assertEquals(1, store.info.value?.leaseVersion)

        repository.onPageStarted()
        assertEquals(LeaseStatus.NotReported, store.status.value)
    }

    @Test
    fun `a takeover reload is claiming or reloadPending, even under a version mismatch`() {
        assertFalse(store.isTakeoverReload)

        store.onLeaseState(state("parked", "claiming"))
        assertTrue(store.isTakeoverReload)

        store.onLeaseState(state("parked", "reloadPending"))
        store.onLeaseInfo(info(2))
        assertTrue(store.isTakeoverReload)
    }

    @Test
    fun `no takeover reload for other states or after a page load`() {
        listOf("held", "lost", "waitingForReply", "lostRace", "unreadable").forEach { reason ->
            store.onLeaseState(state("parked", reason))
            assertFalse(reason, store.isTakeoverReload)
        }
        store.onLeaseState(state("holder"))
        assertFalse(store.isTakeoverReload)

        store.onLeaseState(state("parked", "claiming"))
        store.onPageStarted()
        assertFalse(store.isTakeoverReload)
    }
}
