package com.tiffles.travelingtavern.ui.characters

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tiffles.travelingtavern.domain.model.CharacterSummary
import com.tiffles.travelingtavern.domain.model.PageError
import com.tiffles.travelingtavern.domain.model.PageState
import com.tiffles.travelingtavern.domain.repository.CharacterRepository
import com.tiffles.travelingtavern.domain.usecase.GetCharacterLibraryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface CharactersUiState {
    data object Loading : CharactersUiState
    data class Error(val error: PageError) : CharactersUiState

    /** The library itself is empty (not just the search results). */
    data object Empty : CharactersUiState

    /** [characters] already filtered by [query]; may be empty when nothing matches. */
    data class Success(val characters: List<CharacterSummary>, val query: String) : CharactersUiState
}

@HiltViewModel
class CharactersViewModel @Inject constructor(
    getLibrary: GetCharacterLibraryUseCase,
    private val repository: CharacterRepository
) : ViewModel() {

    private val query = MutableStateFlow("")

    /** The search text as typed, for the search field. */
    val searchQuery: StateFlow<String> = query.asStateFlow()

    val uiState: StateFlow<CharactersUiState> =
        combine(repository.pageState, getLibrary(query), query) { page, characters, text ->
            when {
                page is PageState.Error -> CharactersUiState.Error(page.error)
                characters == null -> CharactersUiState.Loading
                characters.isEmpty() && text.isBlank() -> CharactersUiState.Empty
                else -> CharactersUiState.Success(characters, text)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), CharactersUiState.Loading)

    private val _isRefreshing = MutableStateFlow(false)

    /** True while a pull-to-refresh is waiting for SillyTavern's answer. */
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    fun setQuery(text: String) {
        query.value = text
    }

    /** Re-reads the characters from the server, e.g. to pick up an avatar changed on the desktop. */
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

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
