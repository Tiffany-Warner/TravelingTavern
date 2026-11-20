package com.tiffles.travelingtavern.ui.chats

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tiffles.travelingtavern.domain.model.ChatRef
import com.tiffles.travelingtavern.domain.model.PageError
import com.tiffles.travelingtavern.domain.model.PageState
import com.tiffles.travelingtavern.domain.model.RecentChat
import com.tiffles.travelingtavern.domain.model.ref
import com.tiffles.travelingtavern.domain.repository.CharacterRepository
import com.tiffles.travelingtavern.domain.repository.HiddenChatsRepository
import com.tiffles.travelingtavern.domain.usecase.GetRecentChatsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ChatsUiState {
    data object Loading : ChatsUiState
    data class Error(val error: PageError) : ChatsUiState
    data object Empty : ChatsUiState
    data class Success(val chats: List<RecentChat>) : ChatsUiState
}

/**
 * The long press options and the ⋮ menu. [optionsFor] is the chat whose options sheet is open
 * ([canDelete] false while SillyTavern generates in it); [confirmDeleteFor] the chat whose
 * delete confirmation is open, [isDeleting] while SillyTavern deletes it.
 */
data class ChatOptionsState(
    val hasHiddenChats: Boolean = false,
    val optionsFor: RecentChat? = null,
    val canDelete: Boolean = true,
    val confirmDeleteFor: RecentChat? = null,
    val isDeleting: Boolean = false
)

/** One-shot messages for the snackbar. */
sealed interface ChatsEvent {
    /** [chat] was hidden; the snackbar's Undo shows it again. */
    data class ChatHidden(val chat: ChatRef) : ChatsEvent
    data object DeleteFailed : ChatsEvent
}

@HiltViewModel
class ChatsViewModel @Inject constructor(
    getRecentChats: GetRecentChatsUseCase,
    private val repository: CharacterRepository,
    private val hiddenChats: HiddenChatsRepository
) : ViewModel() {

    private val chatList = getRecentChats().shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 1)

    val uiState: StateFlow<ChatsUiState> =
        combine(repository.pageState, chatList) { page, list ->
            when {
                page is PageState.Error -> ChatsUiState.Error(page.error)
                list == null -> ChatsUiState.Loading
                list.visible.isEmpty() -> ChatsUiState.Empty
                else -> ChatsUiState.Success(list.visible)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ChatsUiState.Loading)

    private val dialogs = MutableStateFlow(ChatOptionsState())

    val optionsState: StateFlow<ChatOptionsState> =
        combine(dialogs, chatList, repository.generatingChat) { state, list, generating ->
            state.copy(
                hasHiddenChats = (list?.hiddenCount ?: 0) > 0,
                canDelete = state.optionsFor?.ref.let { it != null && it != generating }
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ChatOptionsState())

    private val _events = MutableSharedFlow<ChatsEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<ChatsEvent> = _events.asSharedFlow()

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

    /** Long press: opens the options sheet (only for chats SillyTavern reported a file for). */
    fun onChatLongPress(chat: RecentChat) {
        if (chat.ref == null) return
        dialogs.update { it.copy(optionsFor = chat) }
    }

    fun onDismissOptions() = dialogs.update { it.copy(optionsFor = null) }

    fun onHide() {
        val chat = dialogs.value.optionsFor?.ref ?: return
        dialogs.update { it.copy(optionsFor = null) }
        viewModelScope.launch {
            hiddenChats.hide(chat)
            _events.emit(ChatsEvent.ChatHidden(chat))
        }
    }

    fun onUndoHide(chat: ChatRef) {
        viewModelScope.launch { hiddenChats.unhide(chat) }
    }

    /** Delete in the options sheet: asks first. */
    fun onDelete() {
        val chat = dialogs.value.optionsFor ?: return
        if (!optionsState.value.canDelete) return
        dialogs.update { it.copy(optionsFor = null, confirmDeleteFor = chat) }
    }

    fun onDismissDelete() {
        if (dialogs.value.isDeleting) return
        dialogs.update { it.copy(confirmDeleteFor = null) }
    }

    /** The confirmation's Delete: SillyTavern deletes the chat; on failure the row stays and a snackbar says so. */
    fun onConfirmDelete() {
        val state = dialogs.value
        val chat = state.confirmDeleteFor?.ref ?: return
        if (state.isDeleting) return
        dialogs.update { it.copy(isDeleting = true) }
        viewModelScope.launch {
            val deleted = repository.deleteChat(chat)
            if (deleted) hiddenChats.unhide(chat) else _events.emit(ChatsEvent.DeleteFailed)
            dialogs.update { it.copy(confirmDeleteFor = null, isDeleting = false) }
        }
    }

    /** ⋮ → Show hidden chats. */
    fun onShowHiddenChats() {
        viewModelScope.launch { hiddenChats.showAll() }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
