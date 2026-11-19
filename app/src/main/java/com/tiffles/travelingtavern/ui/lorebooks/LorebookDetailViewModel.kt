package com.tiffles.travelingtavern.ui.lorebooks

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tiffles.travelingtavern.domain.model.CharacterSummary
import com.tiffles.travelingtavern.domain.model.LorebookEntry
import com.tiffles.travelingtavern.domain.model.PageError
import com.tiffles.travelingtavern.domain.model.PageState
import com.tiffles.travelingtavern.domain.repository.LorebookException
import com.tiffles.travelingtavern.domain.repository.LorebookFailure
import com.tiffles.travelingtavern.domain.repository.LorebookRepository
import com.tiffles.travelingtavern.domain.usecase.DeleteLorebookUseCase
import com.tiffles.travelingtavern.domain.usecase.GetLorebookEntriesUseCase
import com.tiffles.travelingtavern.domain.usecase.GetLorebooksUseCase
import com.tiffles.travelingtavern.domain.usecase.SetLorebookActiveUseCase
import com.tiffles.travelingtavern.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Loading a lorebook's entries. */
sealed interface EntriesLoad {
    data object Loading : EntriesLoad
    data class Loaded(val entries: List<LorebookEntry>) : EntriesLoad
    data class Failed(val failure: LorebookFailure) : EntriesLoad
}

sealed interface LorebookDetailUiState {
    data object Loading : LorebookDetailUiState
    data class Error(val error: PageError) : LorebookDetailUiState

    /** The lorebook was deleted or renamed elsewhere. */
    data object NotFound : LorebookDetailUiState
    data object LoadFailed : LorebookDetailUiState

    /**
     * [characters] and [personaNames] are where it's attached (an empty persona name = the
     * current persona). [isGlobal] already includes a switch the user just flipped.
     */
    data class Success(
        val name: String,
        val entries: List<LorebookEntry>,
        val isGlobal: Boolean,
        val characters: List<CharacterSummary>,
        val personaNames: List<String>,
        val isWorking: Boolean
    ) : LorebookDetailUiState
}

@HiltViewModel
class LorebookDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    getLorebooks: GetLorebooksUseCase,
    private val getEntries: GetLorebookEntriesUseCase,
    private val setActive: SetLorebookActiveUseCase,
    private val deleteLorebook: DeleteLorebookUseCase,
    private val repository: LorebookRepository
) : ViewModel() {

    val name: String = checkNotNull(savedStateHandle[Routes.FullScreen.LorebookDetail.ARG_NAME])

    private val load = MutableStateFlow<EntriesLoad>(EntriesLoad.Loading)

    /** The global switch's value while SillyTavern hasn't confirmed it yet. */
    private val pendingGlobal = MutableStateFlow<Boolean?>(null)
    private val working = MutableStateFlow(false)

    private val _closed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** One-shot: the lorebook was deleted; leave the screen. */
    val closed: SharedFlow<Unit> = _closed.asSharedFlow()

    private val _actionFailed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** One-shot: a toggle or delete didn't work. */
    val actionFailed: SharedFlow<Unit> = _actionFailed.asSharedFlow()

    private val card = combine(getLorebooks(), pendingGlobal) { cards, pending ->
        val found = cards?.firstOrNull { it.lorebook.name == name }
        // SillyTavern confirmed the flip: the list now says the same.
        if (found != null && found.lorebook.isGlobal == pending) pendingGlobal.value = null
        found
    }

    val uiState: StateFlow<LorebookDetailUiState> =
        combine(repository.pageState, load, card, pendingGlobal, working) { page, entries, found, pending, busy ->
            when {
                page is PageState.Error -> LorebookDetailUiState.Error(page.error)
                entries is EntriesLoad.Loading -> LorebookDetailUiState.Loading
                entries is EntriesLoad.Failed && entries.failure == LorebookFailure.NotFound ->
                    LorebookDetailUiState.NotFound
                entries is EntriesLoad.Failed -> LorebookDetailUiState.LoadFailed
                else -> LorebookDetailUiState.Success(
                    name = name,
                    entries = (entries as EntriesLoad.Loaded).entries,
                    isGlobal = pending ?: found?.lorebook?.isGlobal ?: false,
                    characters = found?.characters.orEmpty(),
                    personaNames = found?.lorebook?.personaNames.orEmpty(),
                    isWorking = busy
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LorebookDetailUiState.Loading)

    init {
        reload()
        viewModelScope.launch {
            repository.entriesChanged.filter { it == name }.collect { reload() }
        }
    }

    fun reload() {
        viewModelScope.launch {
            if (load.value !is EntriesLoad.Loaded) load.value = EntriesLoad.Loading
            load.value = getEntries(name).fold(
                onSuccess = { EntriesLoad.Loaded(it) },
                onFailure = { EntriesLoad.Failed((it as? LorebookException)?.failure ?: LorebookFailure.Failed) }
            )
        }
    }

    fun retry() {
        repository.retry()
        reload()
    }

    fun setGlobal(enabled: Boolean) {
        if (working.value) return
        pendingGlobal.value = enabled
        working.value = true
        viewModelScope.launch {
            setActive(name, enabled).onFailure {
                pendingGlobal.value = null
                _actionFailed.emit(Unit)
            }
            working.value = false
        }
    }

    fun delete() {
        if (working.value) return
        working.value = true
        viewModelScope.launch {
            deleteLorebook(name)
                .onSuccess { _closed.emit(Unit) }
                .onFailure { _actionFailed.emit(Unit) }
            working.value = false
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
