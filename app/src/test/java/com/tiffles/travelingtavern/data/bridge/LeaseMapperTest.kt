package com.tiffles.travelingtavern.data.bridge

import com.tiffles.travelingtavern.domain.model.LeaseAction
import com.tiffles.travelingtavern.domain.model.LeaseAction.Refresh
import com.tiffles.travelingtavern.domain.model.LeaseAction.TakeOverAnyway
import com.tiffles.travelingtavern.domain.model.LeaseAction.TurnOffProtection
import com.tiffles.travelingtavern.domain.model.LeaseAction.UseHere
import com.tiffles.travelingtavern.domain.model.LeaseStatus
import com.tiffles.travelingtavern.domain.model.ParkReason
import org.junit.Assert.assertEquals
import org.junit.Test

/** `leaseState` → [LeaseStatus] with its ways out (A3: never stuck; D45, D46). */
class LeaseMapperTest {

    private fun state(state: String, reason: String? = null, actions: List<String>? = emptyList()) =
        BridgeMessage.LeaseState(
            state = state,
            reason = reason,
            pageId = if (state == "off") null else "t1.l1",
            epoch = null,
            busy = false,
            connected = false,
            origin = "http://192.0.2.1:8000",
            actions = actions
        )

    @Test
    fun `rc5 reasons with their actions, in the page's order`() {
        val cases = mapOf(
            "damaged" to (ParkReason.Damaged to listOf(TakeOverAnyway, Refresh, TurnOffProtection)),
            "error" to (ParkReason.Error to listOf(Refresh, TakeOverAnyway, TurnOffProtection)),
            "unreadable" to (ParkReason.Unreadable to listOf(UseHere, Refresh, TakeOverAnyway, TurnOffProtection)),
            "unknownFormat" to (ParkReason.UnknownFormat to listOf(Refresh, TakeOverAnyway, TurnOffProtection)),
            "reloadPending" to (ParkReason.ReloadPending to listOf(Refresh)),
            "held" to (ParkReason.Held to listOf(UseHere))
        )
        cases.forEach { (reason, expected) ->
            val actions = expected.second.map { it.wire() }
            assertEquals(reason, LeaseStatus.Parked(expected.first, expected.second), state("parked", reason, actions).toLeaseStatus())
        }
    }

    @Test
    fun `off is its own status, never holder`() {
        assertEquals(LeaseStatus.Off, state("off", actions = listOf("turnProtectionOn")).toLeaseStatus())
    }

    @Test
    fun `unknown action strings are dropped, and duplicates`() {
        val status = state("parked", "error", listOf("refresh", "dance", "takeOverAnyway", "refresh")).toLeaseStatus()

        assertEquals(LeaseStatus.Parked(ParkReason.Error, listOf(Refresh, TakeOverAnyway)), status)
    }

    @Test
    fun `turn protection on never shows on a parked scrim`() {
        val status = state("parked", "held", listOf("useHere", "turnProtectionOn")).toLeaseStatus()

        assertEquals(LeaseStatus.Parked(ParkReason.Held, listOf(UseHere)), status)
    }

    @Test
    fun `claiming offers nothing, waiting for a reply offers what the page sends`() {
        assertEquals(LeaseStatus.Parked(ParkReason.Claiming), state("parked", "claiming").toLeaseStatus())
        assertEquals(LeaseStatus.Parked(ParkReason.WaitingForReply), state("parked", "waitingForReply").toLeaseStatus())
        assertEquals(
            LeaseStatus.Parked(ParkReason.WaitingForReply, listOf(TakeOverAnyway)),
            state("parked", "waitingForReply", listOf("takeOverAnyway")).toLeaseStatus()
        )
    }

    @Test
    fun `a parked page with nothing known still gets Refresh`() {
        assertEquals(LeaseStatus.Parked(ParkReason.Held, listOf(Refresh)), state("parked", "held", listOf("dance")).toLeaseStatus())
        assertEquals(LeaseStatus.Parked(ParkReason.Error, listOf(Refresh)), state("parked", "error").toLeaseStatus())
    }

    @Test
    fun `an older page without actions gets the rc4-style fallback`() {
        val expected = mapOf(
            "held" to listOf(UseHere, Refresh),
            "lost" to listOf(UseHere, Refresh),
            "lostRace" to listOf(UseHere, Refresh),
            "unreadable" to listOf(UseHere, Refresh),
            "unknownFormat" to listOf(Refresh),
            "reloadPending" to listOf(Refresh),
            "claiming" to emptyList(),
            "waitingForReply" to emptyList()
        )
        expected.forEach { (reason, actions) ->
            val status = state("parked", reason, actions = null).toLeaseStatus() as LeaseStatus.Parked
            assertEquals(reason, actions, status.actions)
        }
    }

    @Test
    fun `an unknown reason fails closed with Refresh first, then the page's known actions`() {
        assertEquals(
            LeaseStatus.Parked(ParkReason.Unknown("napping"), listOf(Refresh, TurnOffProtection)),
            state("parked", "napping", listOf("turnOffProtection", "refresh")).toLeaseStatus()
        )
        assertEquals(
            LeaseStatus.Parked(ParkReason.Unknown("napping"), listOf(Refresh)),
            state("parked", "napping", actions = null).toLeaseStatus()
        )
    }

    @Test
    fun `an unknown state fails closed with Refresh`() {
        assertEquals(
            LeaseStatus.Parked(ParkReason.Unknown("sleeping"), listOf(Refresh, UseHere)),
            state("sleeping", actions = listOf("useHere")).toLeaseStatus()
        )
    }

    @Test
    fun `the app's pre-check results always have a way out`() {
        assertEquals(listOf(UseHere, Refresh), preCheckActions(ParkReason.Lost))
        assertEquals(listOf(Refresh), preCheckActions(ParkReason.Unreadable))
        assertEquals(listOf(Refresh), preCheckActions(ParkReason.UnknownFormat))
    }

    private fun LeaseAction.wire() = name.replaceFirstChar { it.lowercase() }
}
