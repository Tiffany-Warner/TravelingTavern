package com.tiffles.travelingtavern.ui.personas

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tiffles.travelingtavern.domain.model.Persona
import com.tiffles.travelingtavern.domain.model.PersonaChanges
import com.tiffles.travelingtavern.domain.repository.PersonaException
import com.tiffles.travelingtavern.domain.repository.PersonaFailure
import com.tiffles.travelingtavern.domain.repository.PersonaRepository
import com.tiffles.travelingtavern.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The editable fields, as typed. */
data class PersonaForm(
    val name: String = "",
    val title: String = "",
    val description: String = ""
)

sealed interface PersonaEditorUiState {
    data object Loading : PersonaEditorUiState
    data object NotFound : PersonaEditorUiState
    data object LoadFailed : PersonaEditorUiState

    /**
     * [original] is the form as loaded (empty for a new persona). [pictureUrl] is the current
     * picture, [newPictureUri] one picked on the phone and not saved yet. [tokenCount] is for the
     * description; null until SillyTavern has counted.
     */
    data class Editing(
        val form: PersonaForm,
        val original: PersonaForm,
        val isNew: Boolean,
        val pictureUrl: String? = null,
        val newPictureUri: String? = null,
        val isWorking: Boolean = false,
        val tokenCount: Int? = null
    ) : PersonaEditorUiState {
        /** Save needs a name and something changed. */
        val canSave: Boolean
            get() = form.name.isNotBlank() && !isWorking && (form != original || newPictureUri != null)
    }
}

/** Create (no avatar ID in the route) or edit a persona. */
@OptIn(FlowPreview::class)
@HiltViewModel
class PersonaEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PersonaRepository
) : ViewModel() {

    private val avatarId: String? = savedStateHandle[Routes.FullScreen.PersonaEditor.ARG_AVATAR_ID]

    private val _uiState = MutableStateFlow<PersonaEditorUiState>(
        if (avatarId == null) PersonaEditorUiState.Editing(PersonaForm(), PersonaForm(), isNew = true)
        else PersonaEditorUiState.Loading
    )
    val uiState: StateFlow<PersonaEditorUiState> = _uiState.asStateFlow()

    private val _done = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** One-shot: saved; leave the screen. */
    val done: SharedFlow<Unit> = _done.asSharedFlow()

    private val _failed = MutableSharedFlow<PersonaFailure>(extraBufferCapacity = 1)
    val failed: SharedFlow<PersonaFailure> = _failed.asSharedFlow()

    init {
        avatarId?.let(::load)
        countTokensWhileEditing()
    }

    fun retry() {
        avatarId?.let(::load)
    }

    fun update(form: PersonaForm) = updateEditing { it.copy(form = form) }

    /** A picture was picked on the phone ([uri] is a content URI). */
    fun setPicture(uri: String) = updateEditing { it.copy(newPictureUri = uri) }

    fun save() {
        val state = _uiState.value as? PersonaEditorUiState.Editing ?: return
        if (!state.canSave) return
        _uiState.value = state.copy(isWorking = true)
        viewModelScope.launch {
            val result = if (avatarId == null) repository.create(state.newPersona()).map { }
            else repository.save(avatarId, state.changes())
            result
                .onSuccess { _done.emit(Unit) }
                .onFailure { error ->
                    updateEditing { it.copy(isWorking = false) }
                    _failed.emit((error as? PersonaException)?.failure ?: PersonaFailure.Failed)
                }
        }
    }

    private fun PersonaEditorUiState.Editing.newPersona() = PersonaChanges(
        name = form.name.trim(),
        title = form.title.trim(),
        description = form.description,
        pictureUri = newPictureUri
    )

    /** Only what changed, so SillyTavern leaves every other field alone. */
    private fun PersonaEditorUiState.Editing.changes() = PersonaChanges(
        name = form.name.trim().takeIf { form.name != original.name },
        title = form.title.trim().takeIf { form.title != original.title },
        description = form.description.takeIf { it != original.description },
        pictureUri = newPictureUri
    )

    private fun load(id: String) {
        _uiState.value = PersonaEditorUiState.Loading
        viewModelScope.launch {
            _uiState.value = repository.details(id).fold(
                onSuccess = ::editing,
                onFailure = {
                    if ((it as? PersonaException)?.failure == PersonaFailure.NotFound) PersonaEditorUiState.NotFound
                    else PersonaEditorUiState.LoadFailed
                }
            )
        }
    }

    private fun editing(persona: Persona): PersonaEditorUiState.Editing {
        val form = PersonaForm(persona.name, persona.title, persona.description)
        return PersonaEditorUiState.Editing(form = form, original = form, isNew = false, pictureUrl = persona.pictureUrl)
    }

    private inline fun updateEditing(crossinline change: (PersonaEditorUiState.Editing) -> PersonaEditorUiState.Editing) {
        _uiState.update { if (it is PersonaEditorUiState.Editing) change(it) else it }
    }

    private fun countTokensWhileEditing() {
        viewModelScope.launch {
            _uiState.filterIsInstance<PersonaEditorUiState.Editing>()
                .map { it.form.description }
                .distinctUntilChanged()
                .debounce(TOKEN_DEBOUNCE_MS)
                .collect { description ->
                    val count = if (description.isEmpty()) 0 else repository.countTokens(description)
                    updateEditing { it.copy(tokenCount = count) }
                }
        }
    }

    private companion object {
        const val TOKEN_DEBOUNCE_MS = 500L
    }
}
