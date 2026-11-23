package com.tiffles.travelingtavern.data.bridge

import com.tiffles.travelingtavern.domain.model.LeaseAction
import com.tiffles.travelingtavern.domain.model.LeaseStatus
import com.tiffles.travelingtavern.domain.model.ParkReason

/**
 * Types the page's `leaseState`. Fails closed: any `state` other than `holder` or `off` counts as
 * parked, with an unknown state kept as [ParkReason.Unknown]. A holder's reason is ignored.
 *
 * Never stuck (A3): the page's `actions` are shown as sent (unknown strings dropped), but a parked
 * page always gets at least one way out:
 * - no `actions` at all (a page older than extension rc.5): rc.4's buttons, plus Refresh (D46)
 * - nothing known in `actions` while not claiming: Refresh, as the desktop overlay does (E9)
 * - an unknown reason or state: Refresh first, then the page's known actions (D45; the store adds
 *   the app's Continue without protection when the page doesn't offer Turn off protection)
 */
internal fun BridgeMessage.LeaseState.toLeaseStatus(): LeaseStatus = when (state) {
    HOLDER -> LeaseStatus.Holder(busy = busy, connected = connected)
    OFF -> LeaseStatus.Off
    PARKED -> parkReason(reason).let { LeaseStatus.Parked(it, parkedActions(it, actions)) }
    else -> LeaseStatus.Parked(ParkReason.Unknown(state), refreshFirst(pageActions(actions)))
}

/** The page's known actions that make sense on a parked scrim, in the page's order. */
internal fun pageActions(raw: List<String>?): List<LeaseAction> =
    raw.orEmpty().mapNotNull(::leaseAction).filter { it in PARKED_ACTIONS }.distinct()

/** Refresh first (it always works: a reload is the fallback), then [others]. */
internal fun refreshFirst(others: List<LeaseAction>): List<LeaseAction> =
    listOf(LeaseAction.Refresh) + others.filter { it != LeaseAction.Refresh }

private fun parkedActions(reason: ParkReason, raw: List<String>?): List<LeaseAction> {
    if (reason is ParkReason.Unknown) return refreshFirst(pageActions(raw))
    if (raw == null) return legacyActions(reason)
    val known = pageActions(raw)
    return if (known.isEmpty() && !reason.isClaiming()) listOf(LeaseAction.Refresh) else known
}

/** What a page without `actions` (extension rc.4 and older) offered, plus Refresh (D46). */
private fun legacyActions(reason: ParkReason): List<LeaseAction> = when (reason) {
    ParkReason.Held, ParkReason.Lost, ParkReason.LostRace, ParkReason.Unreadable ->
        listOf(LeaseAction.UseHere, LeaseAction.Refresh)
    ParkReason.Claiming, ParkReason.WaitingForReply -> emptyList()
    else -> listOf(LeaseAction.Refresh)
}

/** The buttons for the app's own pre-check result (A4) while the page hasn't answered yet (D47). */
internal fun preCheckActions(reason: ParkReason): List<LeaseAction> =
    if (reason == ParkReason.Lost) listOf(LeaseAction.UseHere, LeaseAction.Refresh) else listOf(LeaseAction.Refresh)

private fun ParkReason.isClaiming() = this == ParkReason.Claiming || this == ParkReason.WaitingForReply

private fun leaseAction(value: String): LeaseAction? = when (value) {
    "useHere" -> LeaseAction.UseHere
    "refresh" -> LeaseAction.Refresh
    "takeOverAnyway" -> LeaseAction.TakeOverAnyway
    "turnOffProtection" -> LeaseAction.TurnOffProtection
    "turnProtectionOn" -> LeaseAction.TurnProtectionOn
    else -> null
}

private fun parkReason(reason: String?): ParkReason = when (reason) {
    "held" -> ParkReason.Held
    "lost" -> ParkReason.Lost
    "claiming" -> ParkReason.Claiming
    "waitingForReply" -> ParkReason.WaitingForReply
    "lostRace" -> ParkReason.LostRace
    "unreadable" -> ParkReason.Unreadable
    "damaged" -> ParkReason.Damaged
    "error" -> ParkReason.Error
    "unknownFormat" -> ParkReason.UnknownFormat
    "reloadPending" -> ParkReason.ReloadPending
    else -> ParkReason.Unknown(reason)
}

private const val HOLDER = "holder"
private const val PARKED = "parked"
private const val OFF = "off"

/** Turn protection back on belongs to the off banner, never to a parked scrim. */
private val PARKED_ACTIONS = setOf(
    LeaseAction.UseHere,
    LeaseAction.Refresh,
    LeaseAction.TakeOverAnyway,
    LeaseAction.TurnOffProtection
)
