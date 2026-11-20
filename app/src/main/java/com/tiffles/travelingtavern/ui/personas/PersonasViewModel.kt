package com.tiffles.travelingtavern.ui.personas

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tiffles.travelingtavern.domain.model.PageError
import com.tiffles.travelingtavern.domain.model.PageState
import com.tiffles.travelingtavern.domain.model.Persona
import com.tiffles.travelingtavern.domain.repository.PersonaRepository
import com.tiffles.travelingtavern.domain.usecase.GetPersonasUseCase
import com.tiffles.travelingtavern.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface PersonasUiState {
    data object Loading : PersonasUiState
    data class Error(val error: PageError) : PersonasUiState

    /** SillyTavern has no personas at all. */
    data object Empty : PersonasUiState

    /**
     * [personas] in list order (active first). [inChat]: opened from a chat, so picking also
     * locks it to that chat. [actionsFor] has its ⋯ menu open;
     * [confirmDelete] its delete confirmation.
     */
    data class Success(
        val personas: List<Persona>,
        val inChat: Boolean,
        val isWorking: Boolean = false,
        val actionsFor: Persona? = null,
        val confirmDelete: Persona? = null
    ) : PersonasUiState
}

/** One-shot things for the screen to do. */
sealed interface PersonasEvent {
    /** A persona was picked in a chat: back to the chat. */
    data object Close : PersonasEvent
    data object ActionFailed : PersonasEvent
}

/** What the screen holds that doesn't come from SillyTavern. */
private data class LocalState(
    val isWorking: Boolean = false,
    val actionsFor: String? = null,
    val confirmDelete: String? = null
)

/**
 * The Personas list. Tap a persona to use it: opened from a chat, it is also locked to that
 * chat and the screen closes; opened elsewhere, it only becomes the active persona.
 */
@HiltViewModel
class PersonasViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    getPersonas: GetPersonasUseCase,
    private val repository: PersonaRepository
) : ViewModel() {

    private val inChat: Boolean = savedStateHandle[Routes.FullScreen.Personas.ARG_IN_CHAT] ?: false
    private val local = MutableStateFlow(LocalState())
    private val _events = MutableSharedFlow<PersonasEvent>(extraBufferCapacity = 2)

    val events: SharedFlow<PersonasEvent> = _events.asSharedFlow()

    val uiState: StateFlow<PersonasUiState> =
        combine(repository.pageState, getPersonas(), local) { page, personas, state ->
            when {
                page is PageState.Error -> PersonasUiState.Error(page.error)
                personas == null -> PersonasUiState.Loading
                personas.isEmpty() -> PersonasUiState.Empty
                else -> PersonasUiState.Success(
                    personas = personas,
                    inChat = inChat,
                    isWorking = state.isWorking,
                    actionsFor = personas.firstOrNull { it.avatarId == state.actionsFor },
                    confirmDelete = personas.firstOrNull { it.avatarId == state.confirmDelete }
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), PersonasUiState.Loading)

    init {
        // Pictures may have changed on the desktop; SillyTavern gives no way to tell.
        viewModelScope.launch { repository.refresh() }
    }

    fun retry() = repository.retry()

    fun pick(persona: Persona) {
        runAction(closeOnSuccess = inChat) { repository.select(persona.avatarId, lockToChat = inChat) }
    }

    fun openActions(persona: Persona) {
        local.update { it.copy(actionsFor = persona.avatarId) }
    }

    fun dismissActions() {
        local.update { it.copy(actionsFor = null) }
    }

    fun toggleDefault(persona: Persona) {
        dismissActions()
        runAction { repository.setDefault(persona.avatarId, isDefault = !persona.isDefault) }
    }

    fun askDelete(persona: Persona) {
        local.update { it.copy(actionsFor = null, confirmDelete = persona.avatarId) }
    }

    fun dismissDelete() {
        local.update { it.copy(confirmDelete = null) }
    }

    fun confirmDelete() {
        val avatarId = local.value.confirmDelete ?: return
        runAction(onDone = { dismissDelete() }) { repository.delete(avatarId) }
    }

    /** Runs one action at a time; on failure tells the screen. */
    private fun runAction(
        closeOnSuccess: Boolean = false,
        onDone: () -> Unit = {},
        action: suspend () -> Result<Unit>
    ) {
        if (local.value.isWorking) return
        local.update { it.copy(isWorking = true) }
        viewModelScope.launch {
            val result = action()
            local.update { it.copy(isWorking = false) }
            onDone()
            when {
                result.isFailure -> _events.emit(PersonasEvent.ActionFailed)
                closeOnSuccess -> _events.emit(PersonasEvent.Close)
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
