package com.tiffles.travelingtavern.domain.repository

import com.tiffles.travelingtavern.domain.model.LeaseStatus
import kotlinx.coroutines.flow.StateFlow

/** The session lease as the parked scrim sees it, and the scrim's actions (A3). */
interface LeaseRepository {

    /** Whether this phone's page may write, or why not. */
    val status: StateFlow<LeaseStatus>

    /** Running without the app's lease (Continue without protection): the warning banner shows. */
    val unprotected: StateFlow<Boolean>

    /** Claims the lease for this phone (`__ttLease.useHere()`); [status] shows claiming at once. */
    suspend fun useHere()

    /** Reloads the page to finish a takeover (`reloadPending`). */
    suspend fun reload()

    /** Loads SillyTavern without the lease, for this app launch only (no document-start support). */
    suspend fun continueWithoutProtection()
}
