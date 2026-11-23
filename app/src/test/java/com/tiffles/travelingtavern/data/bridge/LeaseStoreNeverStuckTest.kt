package com.tiffles.travelingtavern.data.bridge

import android.util.Log
import com.tiffles.travelingtavern.domain.model.LeaseAction.ContinueWithoutProtection
import com.tiffles.travelingtavern.domain.model.LeaseAction.Refresh
import com.tiffles.travelingtavern.domain.model.LeaseAction.TurnOffProtection
import com.tiffles.travelingtavern.domain.model.LeaseAction.UseHere
import com.tiffles.travelingtavern.domain.model.LeaseStatus
import com.tiffles.travelingtavern.domain.model.ParkReason
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** The store's off state and the app's own ways out (A3: never stuck; D45, D47). */
class LeaseStoreNeverStuckTest {

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

    private fun state(state: String, reason: String? = null, actions: List<String>? = emptyList()) =
        BridgeMessage.LeaseState(
            state = state,
            reason = reason,
            pageId = if (state == "off") null else "t1.l1",
            epoch = 3L,
            busy = false,
            connected = false,
            origin = "http://192.0.2.1:8000",
            actions = actions
        )

    private fun info(leaseVersion: Int) = BridgeMessage.LeaseInfo(
        leaseVersion = leaseVersion,
        extensionVersion = null,
        extensionLeaseVersion = null,
        stVersion = "1.19.1",
        untestedServer = false
    )

    @Test
    fun `off wins over a version mismatch`() {
        store.onLeaseInfo(info(leaseVersion = 2))
        store.onLeaseState(state("off", actions = listOf("turnProtectionOn")))

        assertEquals(LeaseStatus.Off, store.status.value)
    }

    @Test
    fun `restart needed offers Refresh, the page's actions, and the app's Continue when the page can't turn off`() {
        store.onLeaseInfo(info(leaseVersion = 2))
        assertEquals(LeaseStatus.VersionMismatch(listOf(Refresh, ContinueWithoutProtection)), store.status.value)

        store.onLeaseState(state("parked", "held", listOf("useHere")))
        assertEquals(LeaseStatus.VersionMismatch(listOf(Refresh, UseHere, ContinueWithoutProtection)), store.status.value)

        store.onLeaseState(state("parked", "error", listOf("refresh", "turnOffProtection")))
        assertEquals(LeaseStatus.VersionMismatch(listOf(Refresh, TurnOffProtection)), store.status.value)
    }

    @Test
    fun `unprotected, a version mismatch doesn't block and the page's own state decides`() {
        store.onDocumentStartUnsupported()
        store.onContinueWithoutProtection()
        store.onLeaseInfo(info(leaseVersion = 2))
        store.onLeaseState(state("parked", "held", listOf("useHere")))

        assertEquals(LeaseStatus.Parked(ParkReason.Held, listOf(UseHere)), store.status.value)
    }

    @Test
    fun `an unknown state gets the app's Continue unless the page offers Turn off protection`() {
        store.onLeaseState(state("parked", "napping", listOf("useHere")))
        assertEquals(
            LeaseStatus.Parked(ParkReason.Unknown("napping"), listOf(Refresh, UseHere, ContinueWithoutProtection)),
            store.status.value
        )

        store.onLeaseState(state("parked", "napping", listOf("turnOffProtection")))
        assertEquals(LeaseStatus.Parked(ParkReason.Unknown("napping"), listOf(Refresh, TurnOffProtection)), store.status.value)
    }

    @Test
    fun `already unprotected, an unknown state doesn't offer Continue again`() {
        store.onDocumentStartUnsupported()
        store.onContinueWithoutProtection()
        store.onLeaseState(state("parked", "napping"))

        assertEquals(LeaseStatus.Parked(ParkReason.Unknown("napping"), listOf(Refresh)), store.status.value)
    }

    @Test
    fun `a failed pre-check shows its way out until the page answers`() {
        store.onLeaseState(state("holder"))
        store.onPreCheckFailed(ParkReason.Lost)
        assertEquals(LeaseStatus.Parked(ParkReason.Lost, listOf(UseHere, Refresh)), store.status.value)

        store.onPreCheckFailed(ParkReason.Unreadable)
        assertEquals(LeaseStatus.Parked(ParkReason.Unreadable, listOf(Refresh)), store.status.value)

        store.onLeaseState(state("parked", "damaged", listOf("takeOverAnyway", "refresh", "turnOffProtection")))
        assertEquals(ParkReason.Damaged, store.parkReason())
    }

    @Test
    fun `a confirmed parked-page reload counts as a takeover reload until the next page load`() {
        store.onLeaseState(state("parked", "damaged"))
        assertFalse(store.isTakeoverReload)

        store.onParkedReloadRequested()
        assertTrue(store.isTakeoverReload)

        store.onPageStarted()
        assertFalse(store.isTakeoverReload)
    }

    @Test
    fun `Take over anyway shows claiming at once, like Use here`() {
        store.onLeaseState(state("parked", "damaged", listOf("takeOverAnyway")))

        store.onUseHere()

        assertEquals(LeaseStatus.Parked(ParkReason.Claiming), store.status.value)
    }
}
