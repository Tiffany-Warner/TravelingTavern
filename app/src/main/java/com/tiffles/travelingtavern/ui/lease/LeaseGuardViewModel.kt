package com.tiffles.travelingtavern.ui.lease

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tiffles.travelingtavern.domain.model.LeaseAction
import com.tiffles.travelingtavern.domain.model.LeaseStatus
import com.tiffles.travelingtavern.domain.model.ParkReason
import com.tiffles.travelingtavern.domain.repository.LeaseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One row of the parked state table (A3): its title and line. The buttons come with [LeaseGuardUiState.actions]. */
enum class ScrimState {
    InUse,
    Claiming,
    WaitingForReply,
    LostRace,
    Unreadable,
    Damaged,
    Error,
    UnknownState,
    UpdateApp,
    ReloadNeeded,
    RestartNeeded,
    UpdateWebView
}

/**
 * [scrim] null: nothing covers the app. [actions]: the scrim's buttons, primary first (not used by
 * [ScrimState.UpdateWebView], which has its own). [warning]: the warning step for a tapped action
 * that goes ahead at the user's own risk, shown in place of the scrim's content. [unprotected]: the
 * "Not protected" banner shows, scrim or not. [protectionOff]: the "Protection is off" banner with
 * Turn protection back on. [waiting]: a command waits for SillyTavern's first lease report (A4).
 */
data class LeaseGuardUiState(
    val scrim: ScrimState? = null,
    val actions: List<LeaseAction> = emptyList(),
    val warning: LeaseAction? = null,
    val unprotected: Boolean = false,
    val protectionOff: Boolean = false,
    val waiting: Boolean = false
)

/** A warning step opened while [status] showed; a new status closes it (as the desktop overlay does, E9). */
private data class OpenWarning(val action: LeaseAction, val status: LeaseStatus)

/** Drives the parked scrim, its warning step and the two banners over the whole app shell. */
@HiltViewModel
class LeaseGuardViewModel @Inject constructor(
    private val repository: LeaseRepository
) : ViewModel() {

    private val openWarning = MutableStateFlow<OpenWarning?>(null)

    init {
        // A new status closes the warning for good (A3), so A → B → A doesn't bring it back.
        viewModelScope.launch {
            repository.status.collect { status ->
                if (openWarning.value?.status != status) openWarning.value = null
            }
        }
    }

    val uiState: StateFlow<LeaseGuardUiState> =
        combine(repository.status, repository.unprotected, repository.commandWaiting, openWarning) { status, unprotected, waiting, warning ->
            leaseGuardUiState(status, unprotected, waiting, warning?.takeIf { it.status == status }?.action)
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = leaseGuardUiState(
                repository.status.value,
                repository.unprotected.value,
                repository.commandWaiting.value,
                warning = null
            )
        )

    /** One-shot: show "SillyTavern is still loading, try again in a moment." */
    val stillLoading: Flow<Unit> = repository.stillLoading

    /** A scrim or banner button: runs it, or first opens its warning step. */
    fun onAction(action: LeaseAction) {
        if (action.needsWarning) {
            openWarning.value = OpenWarning(action, repository.status.value)
        } else {
            run(action)
        }
    }

    /** The warning step's confirm button: runs the action it was opened for. */
    fun confirmWarning() {
        val warning = openWarning.value ?: return
        openWarning.value = null
        if (warning.status == repository.status.value) run(warning.action)
    }

    /** The warning step's Cancel: back to the scrim. */
    fun cancelWarning() {
        openWarning.value = null
    }

    /** No document start: Continue without protection, as decided for A2 (no warning step). */
    fun continueWithoutProtection() {
        viewModelScope.launch { repository.continueWithoutProtection() }
    }

    private fun run(action: LeaseAction) {
        viewModelScope.launch {
            when (action) {
                LeaseAction.UseHere -> repository.useHere()
                LeaseAction.Refresh -> repository.refresh()
                LeaseAction.TakeOverAnyway -> repository.takeOverAnyway()
                LeaseAction.TurnOffProtection -> repository.turnOffProtection()
                LeaseAction.TurnProtectionOn -> repository.turnProtectionOn()
                LeaseAction.ContinueWithoutProtection -> repository.continueWithoutProtection()
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

internal fun leaseGuardUiState(status: LeaseStatus, unprotected: Boolean, waiting: Boolean, warning: LeaseAction?) =
    LeaseGuardUiState(
        scrim = status.toScrimState(),
        actions = status.scrimActions(),
        warning = warning,
        // Off says it all; the "Not protected" line would only repeat it.
        unprotected = unprotected && status != LeaseStatus.Off,
        protectionOff = status == LeaseStatus.Off,
        waiting = waiting
    )

/** Holder, not yet reported, unprotected and protection off: no scrim (A3). */
internal fun LeaseStatus.toScrimState(): ScrimState? = when (this) {
    LeaseStatus.NotReported, is LeaseStatus.Holder, LeaseStatus.Unprotected, LeaseStatus.Off -> null
    is LeaseStatus.VersionMismatch -> ScrimState.RestartNeeded
    LeaseStatus.NoDocumentStart -> ScrimState.UpdateWebView
    is LeaseStatus.Parked -> reason.toScrimState()
}

private fun LeaseStatus.scrimActions(): List<LeaseAction> = when (this) {
    is LeaseStatus.Parked -> actions
    is LeaseStatus.VersionMismatch -> actions
    else -> emptyList()
}

private fun ParkReason.toScrimState(): ScrimState = when (this) {
    ParkReason.Held, ParkReason.Lost -> ScrimState.InUse
    ParkReason.Claiming -> ScrimState.Claiming
    ParkReason.WaitingForReply -> ScrimState.WaitingForReply
    ParkReason.LostRace -> ScrimState.LostRace
    ParkReason.Unreadable -> ScrimState.Unreadable
    ParkReason.Damaged -> ScrimState.Damaged
    ParkReason.Error -> ScrimState.Error
    is ParkReason.Unknown -> ScrimState.UnknownState
    ParkReason.UnknownFormat -> ScrimState.UpdateApp
    ParkReason.ReloadPending -> ScrimState.ReloadNeeded
}
