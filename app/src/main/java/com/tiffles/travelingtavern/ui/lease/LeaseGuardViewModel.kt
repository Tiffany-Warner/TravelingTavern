package com.tiffles.travelingtavern.ui.lease

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tiffles.travelingtavern.domain.model.LeaseStatus
import com.tiffles.travelingtavern.domain.model.ParkReason
import com.tiffles.travelingtavern.domain.repository.LeaseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One row of the parked state table (A3). */
enum class ScrimState {
    InUse,
    Claiming,
    WaitingForReply,
    LostRace,
    Unreadable,
    UpdateApp,
    ReloadNeeded,
    RestartNeeded,
    UpdateWebView
}

/** [scrim] null: nothing covers the app. [unprotected]: the "Not protected" banner shows, scrim or not. */
data class LeaseGuardUiState(
    val scrim: ScrimState? = null,
    val unprotected: Boolean = false
)

/** Drives the parked scrim and the "Not protected" banner over the whole app shell. */
@HiltViewModel
class LeaseGuardViewModel @Inject constructor(
    private val repository: LeaseRepository
) : ViewModel() {

    val uiState: StateFlow<LeaseGuardUiState> =
        combine(repository.status, repository.unprotected, ::leaseGuardUiState).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = leaseGuardUiState(repository.status.value, repository.unprotected.value)
        )

    fun useHere() {
        viewModelScope.launch { repository.useHere() }
    }

    fun reload() {
        viewModelScope.launch { repository.reload() }
    }

    fun continueWithoutProtection() {
        viewModelScope.launch { repository.continueWithoutProtection() }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

internal fun leaseGuardUiState(status: LeaseStatus, unprotected: Boolean) =
    LeaseGuardUiState(scrim = status.toScrimState(), unprotected = unprotected)

/** Holder, not yet reported and unprotected: no scrim (A3). */
internal fun LeaseStatus.toScrimState(): ScrimState? = when (this) {
    LeaseStatus.NotReported, is LeaseStatus.Holder, LeaseStatus.Unprotected -> null
    LeaseStatus.VersionMismatch -> ScrimState.RestartNeeded
    LeaseStatus.NoDocumentStart -> ScrimState.UpdateWebView
    is LeaseStatus.Parked -> reason.toScrimState()
}

/**
 * An unknown reason gets the plain "in use" row with Use here: the page's own lease decides
 * whether it can claim, and the user is never left without a way forward.
 */
private fun ParkReason.toScrimState(): ScrimState = when (this) {
    ParkReason.Held, ParkReason.Lost, is ParkReason.Unknown -> ScrimState.InUse
    ParkReason.Claiming -> ScrimState.Claiming
    ParkReason.WaitingForReply -> ScrimState.WaitingForReply
    ParkReason.LostRace -> ScrimState.LostRace
    ParkReason.Unreadable -> ScrimState.Unreadable
    ParkReason.UnknownFormat -> ScrimState.UpdateApp
    ParkReason.ReloadPending -> ScrimState.ReloadNeeded
}
