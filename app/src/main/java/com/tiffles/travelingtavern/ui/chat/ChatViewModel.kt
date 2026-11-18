package com.tiffles.travelingtavern.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tiffles.travelingtavern.domain.model.CharacterSummary
import com.tiffles.travelingtavern.domain.model.PageError
import com.tiffles.travelingtavern.domain.model.PageState
import com.tiffles.travelingtavern.domain.repository.CharacterRepository
import com.tiffles.travelingtavern.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** 1.7 adds the selected Text / Story tab to [Ready]; 1.6 is Story only. */
sealed interface ChatUiState {
    /** SillyTavern is still opening the chat; [character] once the library is known. */
    data class Loading(val character: CharacterSummary? = null) : ChatUiState
    data class Ready(val character: CharacterSummary) : ChatUiState
    data class Error(val error: PageError) : ChatUiState
}

/** One character's chat. Opens it in SillyTavern on entry; the page is the shared WebView. */
@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: CharacterRepository
) : ViewModel() {

    val characterId: String = checkNotNull(savedStateHandle[Routes.FullScreen.Chat.ARG_CHARACTER_ID])

    private val chatOpen = MutableStateFlow(false)

    val uiState: StateFlow<ChatUiState> =
        combine(repository.pageState, repository.characters, chatOpen) { page, characters, open ->
            val character = characters?.firstOrNull { it.id == characterId }
            when {
                page is PageState.Error -> ChatUiState.Error(page.error)
                character == null || !open -> ChatUiState.Loading(character)
                else -> ChatUiState.Ready(character)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ChatUiState.Loading())

    init {
        viewModelScope.launch {
            repository.openChat(characterId)
            chatOpen.value = true
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
