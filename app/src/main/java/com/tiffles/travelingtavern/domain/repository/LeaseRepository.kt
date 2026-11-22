package com.tiffles.travelingtavern.domain.repository

import com.tiffles.travelingtavern.domain.model.LeaseStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** The session lease as the parked scrim sees it, and the scrim's actions (A3). */
interface LeaseRepository {

    /** Whether this phone's page may write, or why not. */
    val status: StateFlow<LeaseStatus>

    /** Running without the app's lease (Continue without protection): the warning banner shows. */
    val unprotected: StateFlow<Boolean>

    /** A command is waiting for the page's first lease report (A4): show a loading state. */
    val commandWaiting: StateFlow<Boolean>

    /** A command was refused because SillyTavern never reported its lease state in time (A4). */
    val stillLoading: Flow<Unit>

    /** A reply was interrupted when the app went to the background (A6), not yet shown in Chat. */
    val replyInterrupted: StateFlow<Boolean>

    /** The Chat screen showed the interrupted-reply notice. Main thread. */
    fun onReplyInterruptedShown()

    /** Claims the lease for this phone (`__ttLease.useHere()`); [status] shows claiming at once. */
    suspend fun useHere()

    /** Reloads the page to finish a takeover (`reloadPending`). */
    suspend fun reload()

    /** Loads SillyTavern without the lease, for this app launch only (no document-start support). */
    suspend fun continueWithoutProtection()
}
