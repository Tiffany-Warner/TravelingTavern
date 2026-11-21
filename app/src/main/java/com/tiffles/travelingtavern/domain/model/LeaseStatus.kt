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

    /** Another page holds the lease, or this page is taking over. Requests are held. */
    data class Parked(val reason: ParkReason) : LeaseStatus

    /** App only: the page runs lease code other than the app's own copy. */
    data object VersionMismatch : LeaseStatus

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
    data object UnknownFormat : ParkReason
    data object ReloadPending : ParkReason

    /** A reason (or state) this app version doesn't know; [value] is what the page sent. */
    data class Unknown(val value: String?) : ParkReason
}
