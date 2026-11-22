package com.tiffles.travelingtavern.ui.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tiffles.travelingtavern.domain.model.CharacterSummary
import com.tiffles.travelingtavern.domain.model.ChatActionResult
import com.tiffles.travelingtavern.domain.model.MessageAction
import com.tiffles.travelingtavern.domain.model.PageError
import com.tiffles.travelingtavern.domain.model.PageState
import com.tiffles.travelingtavern.domain.model.Persona
import com.tiffles.travelingtavern.domain.model.TappedMessage
import com.tiffles.travelingtavern.domain.repository.CharacterRepository
import com.tiffles.travelingtavern.domain.repository.ChatSessionRepository
import com.tiffles.travelingtavern.domain.repository.LeaseRepository
import com.tiffles.travelingtavern.domain.repository.PersonaRepository
import com.tiffles.travelingtavern.domain.usecase.GetMessageActionsUseCase
import com.tiffles.travelingtavern.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ChatUiState {
    /** SillyTavern is still opening the chat; [character] once the library is known. */
    data class Loading(val character: CharacterSummary? = null) : ChatUiState

    data class Ready(
        val character: CharacterSummary,
        val generating: Boolean = false,
        val sheet: MessageSheet? = null,
        val confirmation: ChatConfirmation? = null,
        /** The persona the user is chatting as, for the input bar's chip; null until known. */
        val persona: Persona? = null
    ) : ChatUiState {
        /** Send needs text in the message field and no reply generating. */
        fun canSend(input: String): Boolean = input.isNotBlank() && !generating

        /** Start new chat and Restart chat wait until a reply has finished. */
        val menuEnabled: Boolean get() = !generating
    }

    data class Error(val error: PageError) : ChatUiState
}

/** The message actions sheet for [message]; [enabled] is false for actions that can't run now. */
data class MessageSheet(
    val message: TappedMessage,
    val actions: List<MessageAction>,
    val enabled: Set<MessageAction>
)

/** A confirmation sheet the Chat screen is showing. */
sealed interface ChatConfirmation {
    data object StartNewChat : ChatConfirmation
    data object RestartChat : ChatConfirmation
    data class DeleteMessage(val message: TappedMessage) : ChatConfirmation
}

/** One-shot things for the screen to do. */
sealed interface ChatEvent {
    data class CopyText(val text: String) : ChatEvent
    data object MessageChanged : ChatEvent
    data object ActionFailed : ChatEvent
}

/** Things the screen holds that don't come from SillyTavern. */
private data class LocalState(
    val tapped: TappedMessage? = null,
    val confirmation: ChatConfirmation? = null
)

/**
 * One character's chat. Opens it in SillyTavern on entry (the page is the shared WebView),
 * then follows the chat the page has open, so Branch / Start new chat / Restart chat and a
 * restore after process death all land on the right chat.
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val characters: CharacterRepository,
    private val chat: ChatSessionRepository,
    private val getMessageActions: GetMessageActionsUseCase,
    personas: PersonaRepository,
    private val lease: LeaseRepository
) : ViewModel() {

    val characterId: String = checkNotNull(savedStateHandle[Routes.FullScreen.Chat.ARG_CHARACTER_ID])

    /** The chat file this screen shows: the route's at first, then whatever the page switched to. */
    var followedChatFile: String?
        get() = savedStateHandle[KEY_FOLLOWED_CHAT_FILE]
            ?: savedStateHandle[Routes.FullScreen.Chat.ARG_CHAT_FILE]
        private set(value) {
            savedStateHandle[KEY_FOLLOWED_CHAT_FILE] = value
        }

    /**
     * The message field's text. Compose state rather than part of [uiState]: a text field must get
     * each edit back synchronously, and a round trip through the StateFlow lags a frame, so the
     * keyboard's next edit lands on stale text (dropped letters, a jumping cursor). Read-only outside.
     */
    var input by mutableStateOf("")
        private set

    private val chatOpen = MutableStateFlow(false)
    private val generating = MutableStateFlow(false)
    private val local = MutableStateFlow(LocalState())
    private val _events = MutableSharedFlow<ChatEvent>(extraBufferCapacity = 4)

    val events: SharedFlow<ChatEvent> = _events.asSharedFlow()

    /**
     * A reply was interrupted when the app went to the background (A6). A flag rather than an
     * event, so the screen shows it once it is visible again.
     */
    val replyInterrupted: StateFlow<Boolean> = lease.replyInterrupted

    private val activePersona = personas.personas
        .map { list -> list?.firstOrNull { it.isActive } }
        .distinctUntilChanged()

    private val chatState =
        combine(characters.pageState, characters.characters, chatOpen, generating, local) { page, all, open, busy, state ->
            val character = all?.firstOrNull { it.id == characterId }
            when {
                page is PageState.Error -> ChatUiState.Error(page.error)
                character == null || !open -> ChatUiState.Loading(character)
                else -> ChatUiState.Ready(
                    character = character,
                    generating = busy,
                    sheet = state.tapped?.let { sheetFor(it, busy) },
                    confirmation = state.confirmation
                )
            }
        }

    val uiState: StateFlow<ChatUiState> =
        combine(chatState, activePersona) { state, persona ->
            if (state is ChatUiState.Ready) state.copy(persona = persona) else state
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ChatUiState.Loading())

    init {
        characters.setChatVisible(true)
        viewModelScope.launch { openFollowedChat() }
        viewModelScope.launch { chat.generating.collect { generating.value = it } }
        viewModelScope.launch {
            chat.chatChanges.collect { open ->
                if (chatOpen.value && open.characterId == characterId && open.chatFile != null) {
                    followedChatFile = open.chatFile
                }
            }
        }
        viewModelScope.launch {
            chat.messageTaps.collect { message ->
                if (chatOpen.value) local.update { it.copy(tapped = message) }
            }
        }
        viewModelScope.launch {
            chat.impersonations.collect { text -> input = text }
        }
    }

    /** The screen showed the interrupted-reply notice. */
    fun onReplyInterruptedShown() = lease.onReplyInterruptedShown()

    private suspend fun openFollowedChat() {
        val opened = characters.openChat(characterId, followedChatFile)
        if (opened != null) followedChatFile = opened
        chatOpen.value = true
    }

    private fun sheetFor(message: TappedMessage, busy: Boolean): MessageSheet {
        val actions = getMessageActions(message)
        return MessageSheet(message, actions, actions.filter { getMessageActions.isEnabled(it, busy) }.toSet())
    }

    // -- Input bar

    fun onInputChange(text: String) {
        input = text
    }

    fun onSend() {
        val text = input
        if (text.isBlank() || generating.value) return
        input = ""
        viewModelScope.launch {
            // Refused by the session lease: nothing was sent, so the text comes back.
            if (!chat.send(text) && input.isEmpty()) input = text
        }
    }

    fun onWriteForMe() {
        if (generating.value) return
        viewModelScope.launch { chat.impersonate() }
    }

    fun onStop() {
        viewModelScope.launch { chat.stop() }
    }

    // -- Message actions

    /** The sheet has settled and covers the bottom [coveredDp] of the page: show the tapped message above it. */
    fun onSheetShown(coveredDp: Int) {
        val message = local.value.tapped ?: return
        viewModelScope.launch { chat.revealMessage(message.messageId, coveredDp) }
    }

    fun onSheetDismissed() {
        local.update { it.copy(tapped = null) }
        viewModelScope.launch { chat.endReveal() }
    }

    fun onMessageAction(action: MessageAction) {
        val message = local.value.tapped ?: return
        if (!getMessageActions.isEnabled(action, generating.value)) return
        if (action == MessageAction.Delete) {
            onSheetDismissed()
            local.update { it.copy(confirmation = ChatConfirmation.DeleteMessage(message)) }
            return
        }
        onSheetDismissed()
        runAction(message, action)
    }

    private fun runAction(message: TappedMessage, action: MessageAction) {
        viewModelScope.launch {
            when (val result = chat.runAction(message, action)) {
                is ChatActionResult.Done -> result.text?.let { _events.tryEmit(ChatEvent.CopyText(it)) }
                ChatActionResult.Stale -> {
                    _events.tryEmit(ChatEvent.MessageChanged)
                    chat.reloadChat()
                }
                ChatActionResult.Busy, ChatActionResult.Failed -> _events.tryEmit(ChatEvent.ActionFailed)
            }
        }
    }

    // -- ⋮ menu and confirmations

    fun onStartNewChat() = askToConfirm(ChatConfirmation.StartNewChat)

    fun onRestartChat() = askToConfirm(ChatConfirmation.RestartChat)

    private fun askToConfirm(confirmation: ChatConfirmation) {
        if (generating.value) return
        local.update { it.copy(confirmation = confirmation) }
    }

    fun onConfirmationDismissed() = local.update { it.copy(confirmation = null) }

    fun onConfirm() {
        val confirmation = local.value.confirmation ?: return
        local.update { it.copy(confirmation = null) }
        when (confirmation) {
            is ChatConfirmation.DeleteMessage -> runAction(confirmation.message, MessageAction.Delete)
            ChatConfirmation.StartNewChat -> startNewChat(deleteCurrent = false)
            ChatConfirmation.RestartChat -> startNewChat(deleteCurrent = true)
        }
    }

    private fun startNewChat(deleteCurrent: Boolean) {
        viewModelScope.launch {
            if (chat.startNewChat(deleteCurrent) !is ChatActionResult.Done) _events.tryEmit(ChatEvent.ActionFailed)
        }
    }

    override fun onCleared() {
        characters.setChatVisible(false)
    }

    internal companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
        const val KEY_FOLLOWED_CHAT_FILE = "followedChatFile"
    }
}
