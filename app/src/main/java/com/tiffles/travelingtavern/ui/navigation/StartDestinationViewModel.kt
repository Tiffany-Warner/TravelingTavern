package com.tiffles.travelingtavern.ui.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tiffles.travelingtavern.domain.repository.ServerConfigRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Decides where the app opens: Connect when no server is saved, otherwise the Chats tab.
 * Decided once at launch — a later connection drop never bounces the user to Connect.
 */
@HiltViewModel
class StartDestinationViewModel @Inject constructor(
    repository: ServerConfigRepository
) : ViewModel() {

    /** Null until the saved config has been read. */
    val startDestination: StateFlow<String?> = flow {
        val config = repository.observe().first()
        emit(if (config == null) Routes.FullScreen.CONNECT else Routes.Tab.CHATS)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
