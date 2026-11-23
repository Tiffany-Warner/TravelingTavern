package com.tiffles.travelingtavern.domain.repository

import com.tiffles.travelingtavern.domain.model.DesktopProtection
import com.tiffles.travelingtavern.domain.model.LeaseStatus
import com.tiffles.travelingtavern.domain.model.ModelBanner
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

    /** The Chat screen's "Not connected to a model" banner (A7), or null when it isn't shown. */
    val modelBanner: StateFlow<ModelBanner?>

    /**
     * SillyTavern couldn't start a reply (A7). Not replayed: collect it only while the Chat screen
     * is on view, so a notice that arrives anywhere else is dropped.
     */
    val replyNotStarted: Flow<Unit>

    /**
     * Connect on the banner: presses SillyTavern's connect button for the selected API again and
     * waits for its attempt. Still not connected → [modelBanner] points to API Connections.
     */
    suspend fun connectModel()

    /** Claims the lease for this phone (`__ttLease.useHere()`); [status] shows claiming at once. */
    suspend fun useHere()

    /**
     * Take over anyway (`__ttLease.takeOverAnyway()`), after the warning; [status] shows claiming
     * at once, as for [useHere].
     */
    suspend fun takeOverAnyway()

    /** Refresh: `__ttLease.refresh()`, or a plain page reload if the page declines or doesn't answer. */
    suspend fun refresh()

    /** Turn off protection in this page (`__ttLease.turnOffProtection()`), after the warning. */
    suspend fun turnOffProtection()

    /** Turn protection back on (`__ttLease.turnProtectionOn()`); no warning, it's the safe direction. */
    suspend fun turnProtectionOn()

    /** Loads SillyTavern without the lease, for this app launch only (no document-start support). */
    suspend fun continueWithoutProtection()

    /** The Desktop protection row (A8), from the current load's `leaseInfo`; null while not known yet. */
    val desktopProtection: Flow<DesktopProtection?>

    /** The one-time "Keep your devices from overwriting each other" dialog should show now (A8). */
    val extensionPromptDue: Flow<Boolean>

    /** The one-time dialog was shown: it never shows again. */
    suspend fun onExtensionPromptShown()
}
