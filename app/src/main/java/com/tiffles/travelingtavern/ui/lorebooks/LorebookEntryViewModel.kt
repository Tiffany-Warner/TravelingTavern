package com.tiffles.travelingtavern.ui.lorebooks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tiffles.travelingtavern.domain.model.LorebookEntry
import com.tiffles.travelingtavern.domain.repository.LorebookException
import com.tiffles.travelingtavern.domain.repository.LorebookFailure
import com.tiffles.travelingtavern.domain.repository.LorebookRepository
import com.tiffles.travelingtavern.domain.usecase.DeleteLorebookEntryUseCase
import com.tiffles.travelingtavern.domain.usecase.GetLorebookEntriesUseCase
import com.tiffles.travelingtavern.domain.usecase.SaveLorebookEntryUseCase
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

/** The editable fields, as typed. [keywords] is comma-separated. */
data class EntryForm(
    val title: String = "",
    val keywords: String = "",
    val content: String = "",
    val enabled: Boolean = true
)

sealed interface LorebookEntryUiState {
    data object Loading : LorebookEntryUiState
    data object NotFound : LorebookEntryUiState
    data object LoadFailed : LorebookEntryUiState

    /** [tokenCount] is for the content field; null until SillyTavern has counted. */
    data class Editing(
        val form: EntryForm,
        val isNew: Boolean,
        val isWorking: Boolean = false,
        val tokenCount: Int? = null
    ) : LorebookEntryUiState
}

@OptIn(FlowPreview::class)
@HiltViewModel
class LorebookEntryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getEntries: GetLorebookEntriesUseCase,
    private val saveEntry: SaveLorebookEntryUseCase,
    private val deleteEntry: DeleteLorebookEntryUseCase,
    private val repository: LorebookRepository
) : ViewModel() {

    val lorebook: String = checkNotNull(savedStateHandle[Routes.FullScreen.LorebookEntry.ARG_NAME])
    private val uid: Int? = checkNotNull(savedStateHandle.get<Int>(Routes.FullScreen.LorebookEntry.ARG_UID))
        .takeIf { it != Routes.FullScreen.LorebookEntry.NEW_ENTRY }

    /** The keywords as loaded, kept as-is unless the user edits the keyword text. */
    private var originalKeywords: List<String> = emptyList()

    private val _uiState = MutableStateFlow<LorebookEntryUiState>(
        if (uid == null) LorebookEntryUiState.Editing(EntryForm(), isNew = true) else LorebookEntryUiState.Loading
    )
    val uiState: StateFlow<LorebookEntryUiState> = _uiState.asStateFlow()

    private val _done = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** One-shot: saved or deleted; leave the screen. */
    val done: SharedFlow<Unit> = _done.asSharedFlow()

    private val _actionFailed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val actionFailed: SharedFlow<Unit> = _actionFailed.asSharedFlow()

    init {
        if (uid != null) load(uid)
        countTokensWhileEditing()
    }

    fun retry() {
        if (uid != null) load(uid)
    }

    fun update(form: EntryForm) {
        _uiState.update { if (it is LorebookEntryUiState.Editing) it.copy(form = form) else it }
    }

    fun save() {
        val state = _uiState.value as? LorebookEntryUiState.Editing ?: return
        if (state.isWorking) return
        _uiState.value = state.copy(isWorking = true)
        viewModelScope.launch {
            saveEntry(lorebook, state.form.toEntry())
                .onSuccess { _done.emit(Unit) }
                .onFailure { fail() }
        }
    }

    fun delete() {
        val state = _uiState.value as? LorebookEntryUiState.Editing ?: return
        if (state.isWorking || uid == null) return
        _uiState.value = state.copy(isWorking = true)
        viewModelScope.launch {
            deleteEntry(lorebook, uid)
                .onSuccess { _done.emit(Unit) }
                .onFailure { fail() }
        }
    }

    private suspend fun fail() {
        _uiState.update { if (it is LorebookEntryUiState.Editing) it.copy(isWorking = false) else it }
        _actionFailed.emit(Unit)
    }

    private fun load(uid: Int) {
        _uiState.value = LorebookEntryUiState.Loading
        viewModelScope.launch {
            _uiState.value = getEntries(lorebook).fold(
                onSuccess = { entries -> entries.firstOrNull { it.uid == uid }?.let(::editing) ?: LorebookEntryUiState.NotFound },
                onFailure = {
                    if ((it as? LorebookException)?.failure == LorebookFailure.NotFound) LorebookEntryUiState.NotFound
                    else LorebookEntryUiState.LoadFailed
                }
            )
        }
    }

    private fun editing(entry: LorebookEntry): LorebookEntryUiState.Editing {
        originalKeywords = entry.keywords
        return LorebookEntryUiState.Editing(
            form = EntryForm(entry.title, entry.keywords.joinToString(KEYWORD_SEPARATOR), entry.content, entry.enabled),
            isNew = false
        )
    }

    /**
     * Keywords are re-split only if the user changed them, so a keyword that itself contains
     * a comma (e.g. a regex) survives saving other fields.
     */
    private fun EntryForm.toEntry() = LorebookEntry(
        uid = uid,
        title = title,
        keywords = if (keywords == originalKeywords.joinToString(KEYWORD_SEPARATOR)) originalKeywords
        else keywords.split(',').map { it.trim() }.filter { it.isNotEmpty() },
        content = content,
        enabled = enabled
    )

    private fun countTokensWhileEditing() {
        viewModelScope.launch {
            _uiState.filterIsInstance<LorebookEntryUiState.Editing>()
                .map { it.form.content }
                .distinctUntilChanged()
                .debounce(TOKEN_DEBOUNCE_MS)
                .collect { content ->
                    val count = if (content.isEmpty()) 0 else repository.countTokens(content)
                    _uiState.update { if (it is LorebookEntryUiState.Editing) it.copy(tokenCount = count) else it }
                }
        }
    }

    private companion object {
        const val KEYWORD_SEPARATOR = ", "
        const val TOKEN_DEBOUNCE_MS = 500L
    }
}
