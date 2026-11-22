package com.tiffles.travelingtavern.ui.me

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tiffles.travelingtavern.domain.model.DesktopProtection
import com.tiffles.travelingtavern.domain.model.LeaseStatus
import com.tiffles.travelingtavern.domain.repository.LeaseRepository
import com.tiffles.travelingtavern.domain.usecase.InstallExtensionUseCase
import com.tiffles.travelingtavern.domain.usecase.UpdateExtensionUseCase
import com.tiffles.travelingtavern.ui.lease.toScrimState
import com.tiffles.travelingtavern.ui.protection.ExtensionActions
import com.tiffles.travelingtavern.ui.protection.ExtensionDialog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * [protection]: the Desktop protection row (A8), null while not known yet. [dialog]: the install /
 * update flow, null while none runs or while the parked scrim covers the app.
 */
data class MeUiState(
    val protection: DesktopProtection? = null,
    val dialog: ExtensionDialog? = null
)

/** The Me tab: for now only the Desktop protection section (A8; 1.8 builds the rest of Me). */
@HiltViewModel
class MeViewModel @Inject constructor(
    repository: LeaseRepository,
    installExtension: InstallExtensionUseCase,
    updateExtension: UpdateExtensionUseCase
) : ViewModel() {

    private val actions = ExtensionActions(viewModelScope, { installExtension() }, { updateExtension() })

    val uiState: StateFlow<MeUiState> =
        combine(repository.desktopProtection, actions.dialog, repository.status, ::meUiState)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), MeUiState())

    fun install() = actions.askInstall()

    fun update() = actions.askUpdate()

    fun confirm() = actions.confirm()

    fun updateInstead() = actions.runUpdate()

    fun dismissDialog() = actions.dismiss()

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}

internal fun meUiState(protection: DesktopProtection?, dialog: ExtensionDialog?, status: LeaseStatus) =
    MeUiState(protection = protection, dialog = if (status.toScrimState() != null) null else dialog)
