package com.tiffles.travelingtavern.ui.lorebooks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tiffles.travelingtavern.domain.model.CharacterSummary
import com.tiffles.travelingtavern.domain.model.LorebookCard
import com.tiffles.travelingtavern.domain.model.LorebookFilter
import com.tiffles.travelingtavern.domain.model.PageError
import com.tiffles.travelingtavern.domain.model.PageState
import com.tiffles.travelingtavern.domain.repository.LorebookException
import com.tiffles.travelingtavern.domain.repository.LorebookFailure
import com.tiffles.travelingtavern.domain.repository.LorebookRepository
import com.tiffles.travelingtavern.domain.usecase.CreateLorebookUseCase
import com.tiffles.travelingtavern.domain.usecase.FilterLorebooksUseCase
import com.tiffles.travelingtavern.domain.usecase.GetLorebooksUseCase
import com.tiffles.travelingtavern.domain.usecase.LorebookNameCheck
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface LorebooksUiState {
    data object Loading : LorebooksUiState
    data class Error(val error: PageError) : LorebooksUiState

    /** There are no lorebooks at all (not just none matching). */
    data object Empty : LorebooksUiState

    /**
     * [cards] already filtered; may be empty when nothing matches. [filterCharacters] are the
     * Character chip's choices (empty = chip hidden); [selectedCharacter] is set while the
     * Character filter is on.
     */
    data class Success(
        val cards: List<LorebookCard>,
        val filter: LorebookFilter,
        val filterCharacters: List<CharacterSummary>,
        val selectedCharacter: CharacterSummary?
    ) : LorebooksUiState
}

/** The create sheet's state. [failed] is set when SillyTavern refused or didn't answer. */
data class CreateLorebookState(
    val name: String = "",
    val check: LorebookNameCheck = LorebookNameCheck.Empty,
    val isCreating: Boolean = false,
    val failed: Boolean = false
) {
    val canCreate: Boolean get() = check == LorebookNameCheck.Ok && !isCreating
}

@HiltViewModel
class LorebooksViewModel @Inject constructor(
    getLorebooks: GetLorebooksUseCase,
    private val filterLorebooks: FilterLorebooksUseCase,
    private val createLorebook: CreateLorebookUseCase,
    private val repository: LorebookRepository
) : ViewModel() {

    private val filter = MutableStateFlow<LorebookFilter>(LorebookFilter.All)
    private val query = MutableStateFlow("")
    private val allCards = getLorebooks().stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The search text as typed, for the search field. */
    val searchQuery: StateFlow<String> = query.asStateFlow()

    val uiState: StateFlow<LorebooksUiState> =
        combine(repository.pageState, allCards, filter, query) { page, cards, chip, text ->
            when {
                page is PageState.Error -> LorebooksUiState.Error(page.error)
                cards == null -> LorebooksUiState.Loading
                cards.isEmpty() -> LorebooksUiState.Empty
                else -> success(cards, chip, text)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LorebooksUiState.Loading)

    private val _isRefreshing = MutableStateFlow(false)

    /** True while a pull-to-refresh is waiting for SillyTavern's answer. */
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _createState = MutableStateFlow(CreateLorebookState())
    val createState: StateFlow<CreateLorebookState> = _createState.asStateFlow()

    private val _openLorebook = MutableSharedFlow<String>(extraBufferCapacity = 1)

    /** One-shot: a lorebook was created; open it by name. */
    val openLorebook: SharedFlow<String> = _openLorebook.asSharedFlow()

    fun setFilter(value: LorebookFilter) {
        filter.value = value
    }

    fun setQuery(text: String) {
        query.value = text
    }

    /** Back to All with no search text. */
    fun clearFilters() {
        filter.value = LorebookFilter.All
        query.value = ""
    }

    fun refresh() {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        viewModelScope.launch {
            try {
                repository.refresh()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun retry() = repository.retry()

    /** The create sheet opened: start from an empty name. */
    fun startCreate() {
        _createState.value = CreateLorebookState()
    }

    fun setCreateName(name: String) {
        _createState.update { it.copy(name = name, check = createLorebook.check(name, existingNames()), failed = false) }
    }

    fun create() {
        val state = _createState.value
        if (!state.canCreate) return
        _createState.value = state.copy(isCreating = true, failed = false)
        viewModelScope.launch {
            createLorebook(state.name)
                .onSuccess { name ->
                    _createState.value = CreateLorebookState()
                    _openLorebook.emit(name)
                }
                .onFailure { error ->
                    val taken = (error as? LorebookException)?.failure == LorebookFailure.NameTaken
                    _createState.update {
                        if (taken) it.copy(isCreating = false, check = LorebookNameCheck.Taken)
                        else it.copy(isCreating = false, failed = true)
                    }
                }
        }
    }

    private fun existingNames(): List<String> = allCards.value.orEmpty().map { it.lorebook.name }

    private fun success(cards: List<LorebookCard>, chip: LorebookFilter, text: String): LorebooksUiState.Success {
        val choices = filterLorebooks.charactersWithLorebooks(cards)
        val selected = (chip as? LorebookFilter.Character)?.let { c -> choices.firstOrNull { it.id == c.characterId } }
        // A character that no longer has a lorebook can't stay selected.
        val effective = if (chip is LorebookFilter.Character && selected == null) LorebookFilter.All else chip
        return LorebooksUiState.Success(
            cards = filterLorebooks(cards, effective, text),
            filter = effective,
            filterCharacters = choices,
            selectedCharacter = selected
        )
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
