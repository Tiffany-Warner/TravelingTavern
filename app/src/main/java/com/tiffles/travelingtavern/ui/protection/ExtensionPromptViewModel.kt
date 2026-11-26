package com.tiffles.travelingtavern.ui.protection

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tiffles.travelingtavern.domain.model.LeaseStatus
import com.tiffles.travelingtavern.domain.repository.LeaseRepository
import com.tiffles.travelingtavern.domain.usecase.InstallExtensionUseCase
import com.tiffles.travelingtavern.domain.usecase.UpdateExtensionUseCase
import com.tiffles.travelingtavern.ui.lease.toScrimState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * [prompt]: the one-time "Keep your devices from overwriting each other" dialog shows (A8).
 * [dialog]: the install flow it started. Both null / false while the parked scrim (or another
 * covering state) is up, so nothing is drawn over it.
 */
data class ExtensionPromptUiState(
    val prompt: Boolean = false,
    val dialog: ExtensionDialog? = null
)

/** The one-time extension dialog over the whole app shell, and the install it can start (A8). */
@HiltViewModel
class ExtensionPromptViewModel @Inject constructor(
    private val repository: LeaseRepository,
    installExtension: InstallExtensionUseCase,
    updateExtension: UpdateExtensionUseCase
) : ViewModel() {

    private val actions = ExtensionActions(viewModelScope, { installExtension() }, { updateExtension() })
    private val promptOpen = MutableStateFlow(false)

    init {
        // Marked shown as soon as it opens: once ever, whatever the answer (Not now never asks again).
        viewModelScope.launch {
            repository.extensionPromptDue.collect { due ->
                if (due && !promptOpen.value) {
                    promptOpen.value = true
                    repository.onExtensionPromptShown()
                }
            }
        }
    }

    val uiState: StateFlow<ExtensionPromptUiState> =
        combine(promptOpen, actions.dialog, repository.status, repository.unprotected, ::extensionPromptUiState)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ExtensionPromptUiState())

    fun install() {
        promptOpen.value = false
        actions.runInstall()
    }

    fun notNow() {
        promptOpen.value = false
    }

    fun confirm() = actions.confirm()

    fun updateInstead() = actions.runUpdate()

    fun dismissDialog() = actions.dismiss()

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

/** The prompt only on a holder page with protection on; results whenever no scrim covers the app. */
internal fun extensionPromptUiState(
    promptOpen: Boolean,
    dialog: ExtensionDialog?,
    status: LeaseStatus,
    unprotected: Boolean
): ExtensionPromptUiState {
    val covered = status.toScrimState() != null
    return ExtensionPromptUiState(
        prompt = promptOpen && dialog == null && status is LeaseStatus.Holder && !unprotected,
        dialog = if (covered) null else dialog
    )
}
