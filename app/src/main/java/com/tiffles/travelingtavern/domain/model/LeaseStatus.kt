package com.tiffles.travelingtavern.domain.model

/**
 * Whether this phone's SillyTavern page may write (session lease). Comes from the page's
 * `leaseState`, plus states only the app knows ([VersionMismatch], [NoDocumentStart], [Unprotected]).
 */
sealed interface LeaseStatus {

    /** The current page load hasn't reported yet (or is still loading). */
    data object NotReported : LeaseStatus

    /** This page holds the lease. [busy]: it is generating a reply; [connected]: to a model. */
    data class Holder(val busy: Boolean, val connected: Boolean) : LeaseStatus

    /**
     * Another page holds the lease, this page is taking over, or something went wrong. Requests are
     * held. [actions]: the ways out to show, primary first (A3: never stuck).
     */
    data class Parked(val reason: ParkReason, val actions: List<LeaseAction> = emptyList()) : LeaseStatus

    /**
     * Protection is off in this page (the user's Turn off protection, E4): the lease stands down,
     * nothing is checked or released. Lasts until Turn protection back on, or the app is closed.
     */
    data object Off : LeaseStatus

    /** App only: the page runs lease code other than the app's own copy. [actions]: the ways out. */
    data class VersionMismatch(val actions: List<LeaseAction> = listOf(LeaseAction.Refresh)) : LeaseStatus

    /** App only: the WebView can't inject scripts at document start, so SillyTavern isn't loaded. */
    data object NoDocumentStart : LeaseStatus

    /**
     * App only: no document start, the user chose Continue without protection, and no lease
     * reports from the page. SillyTavern runs without the lease for this app process; the next
     * launch asks again (A2). An extension's own lease reporting replaces this state.
     */
    data object Unprotected : LeaseStatus
}

/** Why a page is parked (the extension's `leaseState.reason`). */
sealed interface ParkReason {
    data object Held : ParkReason
    data object Lost : ParkReason
    data object Claiming : ParkReason
    data object WaitingForReply : ParkReason
    data object LostRace : ParkReason
    data object Unreadable : ParkReason

    /** A lease file answered but isn't valid (E2). */
    data object Damaged : ParkReason

    /** Lease writes kept failing while reads worked, or a lease-code error (E3). */
    data object Error : ParkReason
    data object UnknownFormat : ParkReason
    data object ReloadPending : ParkReason

    /** A reason (or state) this app version doesn't know; [value] is what the page sent. */
    data class Unknown(val value: String?) : ParkReason
}

/**
 * A way out of a parked state (`leaseState.actions`, E11), plus the app's own
 * [ContinueWithoutProtection]. [TakeOverAnyway], [TurnOffProtection] and [ContinueWithoutProtection]
 * go ahead at the user's own risk: each is a tap after a warning (A3).
 */
enum class LeaseAction(val needsWarning: Boolean = false) {
    UseHere,
    Refresh,
    TakeOverAnyway(needsWarning = true),
    TurnOffProtection(needsWarning = true),
    TurnProtectionOn,

    /** App only: reload without the app's lease for this launch (A2, D19). */
    ContinueWithoutProtection(needsWarning = true)
}
