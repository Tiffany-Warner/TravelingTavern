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
import com.tiffles.travelingtavern.domain.model.ModelBanner
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
        val persona: Persona? = null,
        /** "Not connected to a model" after a takeover (A7); null when not shown. */
        val modelBanner: ModelBanner? = null
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

/** What the Chat screen says when SillyTavern couldn't start a reply (A7, D34). */
sealed interface ReplyNotStartedNotice {
    /**
     * After a send (or the snackbar's Regenerate): the user's message is saved; offers Regenerate.
     * [text] is the sent text to put back in the field, null after a Regenerate.
     */
    data class MessageSaved(val text: String?) : ReplyNotStartedNotice

    /** After anything else (Write for me, a message action, a background generation): "Try again", no action. */
    data object TryAgain : ReplyNotStartedNotice
}

/** The last reply start that saved the user's message first (A7). */
private sealed interface ReplyStart {
    /** A send of [text]; the text goes back in the field once if the reply can't start. */
    data class Send(val text: String) : ReplyStart

    /** The snackbar's Regenerate, or a send whose text was already put back. */
    data object Regenerate : ReplyStart
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

    /**
     * Main thread only. Cleared when another kind of generation starts, the chat changes, or a
     * generation ends without a notice (the reply came, so a later notice belongs to something else,
     * such as an extension's background generation).
     */
    private var replyStart: ReplyStart? = null

    /** A notice arrived during the running generation; read when it ends. */
    private var noticeThisGeneration = false

    private val _replyNotStarted = MutableSharedFlow<ReplyNotStartedNotice>(extraBufferCapacity = 1)

    /** The text put back after a reply that couldn't start, until a Regenerate or another send. */
    private var restoredText: String? = null

    val events: SharedFlow<ChatEvent> = _events.asSharedFlow()

    /**
     * A reply was interrupted when the app went to the background (A6). A flag rather than an
     * event, so the screen shows it once it is visible again.
     */
    val replyInterrupted: StateFlow<Boolean> = lease.replyInterrupted

    /**
     * SillyTavern couldn't start a reply (A7), told apart by what started it. Not replayed: the
     * screen collects it only while it is on view, so a notice that arrives while the user is
     * elsewhere is dropped, not shown later.
     */
    val replyNotStarted: SharedFlow<ReplyNotStartedNotice> = _replyNotStarted.asSharedFlow()

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
        combine(chatState, activePersona, lease.modelBanner) { state, persona, banner ->
            if (state is ChatUiState.Ready) state.copy(persona = persona, modelBanner = banner) else state
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ChatUiState.Loading())

    init {
        characters.setChatVisible(true)
        viewModelScope.launch { openFollowedChat() }
        viewModelScope.launch {
            chat.generating.collect { busy ->
                if (!generating.value && busy) noticeThisGeneration = false
                if (generating.value && !busy) onGenerationEnded()
                generating.value = busy
            }
        }
        viewModelScope.launch {
            lease.replyNotStarted.collect { _replyNotStarted.tryEmit(classifyReplyNotStarted()) }
        }
        viewModelScope.launch {
            chat.chatChanges.collect { open ->
                if (chatOpen.value && open.characterId == characterId && open.chatFile != null) {
                    if (open.chatFile != followedChatFile) replyStart = null
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

    /** Connect on the "Not connected to a model" banner. */
    fun onConnectModel() {
        viewModelScope.launch { lease.connectModel() }
    }

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
        restoredText = null
        // Set before sending, so a notice can't arrive before the app knows about the send.
        replyStart = ReplyStart.Send(text)
        viewModelScope.launch {
            if (!chat.send(text)) {
                replyStart = null
                // Refused by the session lease: nothing was sent, so the text comes back.
                if (input.isEmpty()) input = text
            }
        }
    }

    /**
     * Relies on the notice arriving before the generation's end: the extension's `beginGeneration()`
     * posts `leaseNotice` before it aborts the request, and `bridge.js` reports `generationState`
     * false only after SillyTavern has handled that abort (D34).
     */
    private fun onGenerationEnded() {
        if (!noticeThisGeneration) replyStart = null
        noticeThisGeneration = false
    }

    /** Which notice this is: after a send or the snackbar's Regenerate the user's message is saved. */
    private fun classifyReplyNotStarted(): ReplyNotStartedNotice {
        // A background request without Stop (no generationState) must not mark the next generation.
        if (generating.value) noticeThisGeneration = true
        return when (val start = replyStart) {
            null -> ReplyNotStartedNotice.TryAgain
            is ReplyStart.Send -> {
                replyStart = ReplyStart.Regenerate
                ReplyNotStartedNotice.MessageSaved(start.text)
            }
            ReplyStart.Regenerate -> ReplyNotStartedNotice.MessageSaved(text = null)
        }
    }

    /**
     * The screen is showing [notice]. After a send, SillyTavern has already saved the user's
     * message: its text goes back in the field (only into an empty one), so the user can edit and
     * send it again, or use the snackbar's Regenerate instead.
     */
    fun onReplyNotStartedShown(notice: ReplyNotStartedNotice) {
        val text = (notice as? ReplyNotStartedNotice.MessageSaved)?.text ?: return
        if (input.isEmpty()) {
            input = text
            restoredText = text
        }
    }

    /**
     * Regenerate on the snackbar: a reply to the saved message, without adding it again. Only from
     * the user's tap, never retried by the app. Once started, the put-back text (if unchanged) goes,
     * so it can't be sent a second time by mistake.
     */
    fun onRegenerateReply() {
        if (generating.value) return
        replyStart = ReplyStart.Regenerate
        viewModelScope.launch {
            when (chat.regenerateReply()) {
                is ChatActionResult.Done -> {
                    if (restoredText != null && input == restoredText) input = ""
                    restoredText = null
                }
                ChatActionResult.Stale -> {
                    replyStart = null
                    _events.tryEmit(ChatEvent.MessageChanged)
                    chat.reloadChat()
                }
                ChatActionResult.Busy, ChatActionResult.Failed -> _events.tryEmit(ChatEvent.ActionFailed)
            }
        }
    }

    fun onWriteForMe() {
        if (generating.value) return
        replyStart = null
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
        if (action == MessageAction.Regenerate || action == MessageAction.Continue) replyStart = null
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
