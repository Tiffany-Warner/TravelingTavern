package com.tiffles.travelingtavern.ui.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tiffles.travelingtavern.domain.model.PageError
import com.tiffles.travelingtavern.domain.model.PageState
import com.tiffles.travelingtavern.domain.model.RecentChat
import com.tiffles.travelingtavern.domain.repository.CharacterRepository
import com.tiffles.travelingtavern.domain.usecase.GetRecentChatsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ChatsUiState {
    data object Loading : ChatsUiState
    data class Error(val error: PageError) : ChatsUiState
    data object Empty : ChatsUiState
    data class Success(val chats: List<RecentChat>) : ChatsUiState
}

@HiltViewModel
class ChatsViewModel @Inject constructor(
    getRecentChats: GetRecentChatsUseCase,
    private val repository: CharacterRepository
) : ViewModel() {

    val uiState: StateFlow<ChatsUiState> =
        combine(repository.pageState, getRecentChats()) { page, chats ->
            when {
                page is PageState.Error -> ChatsUiState.Error(page.error)
                chats == null -> ChatsUiState.Loading
                chats.isEmpty() -> ChatsUiState.Empty
                else -> ChatsUiState.Success(chats)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ChatsUiState.Loading)

    private val _isRefreshing = MutableStateFlow(false)

    /** True while a pull-to-refresh is waiting for SillyTavern's answer. */
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

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
