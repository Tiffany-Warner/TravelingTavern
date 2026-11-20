package com.tiffles.travelingtavern.data.bridge

import android.util.Log
import com.tiffles.travelingtavern.domain.model.CharacterSummary
import com.tiffles.travelingtavern.domain.model.ChatActivity
import com.tiffles.travelingtavern.domain.model.ChatRef
import com.tiffles.travelingtavern.domain.model.Lorebook
import com.tiffles.travelingtavern.domain.model.Persona
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Everything the page has told the app through the bridge. Fed by [BridgeInstaller]. */
@Singleton
class BridgeRepository @Inject constructor() {

    private val _messages = MutableSharedFlow<BridgeMessage>(extraBufferCapacity = MESSAGE_BUFFER)
    private val _characters = MutableStateFlow<List<CharacterSummary>?>(null)
    private val _chatActivity = MutableStateFlow<List<ChatActivity>?>(null)
    private val _lorebooks = MutableStateFlow<List<Lorebook>?>(null)
    private val _personas = MutableStateFlow<List<Persona>?>(null)
    private val _isReady = MutableStateFlow(false)
    private val _generating = MutableStateFlow(false)
    private val _openChat = MutableStateFlow<ChatRef?>(null)

    /** Every valid message, including ones handled here. One-shot events (chat changed, new message). */
    val messages: SharedFlow<BridgeMessage> = _messages.asSharedFlow()

    /** Null until the page first reports them; kept across reloads until replaced. */
    val characters: StateFlow<List<CharacterSummary>?> = _characters.asStateFlow()

    /** Latest activity per chat file. Null until the page first reports it. */
    val chatActivity: StateFlow<List<ChatActivity>?> = _chatActivity.asStateFlow()

    /** Every lorebook. Null until the page first reports them. */
    val lorebooks: StateFlow<List<Lorebook>?> = _lorebooks.asStateFlow()

    /** Every persona, unsorted. Null until the page first reports them. */
    val personas: StateFlow<List<Persona>?> = _personas.asStateFlow()

    /** True once SillyTavern has loaded and the bridge is listening; false again on every reload. */
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

    /** True while SillyTavern is generating (Stop is shown); false again on every reload. */
    val generating: StateFlow<Boolean> = _generating.asStateFlow()

    /** The chat open in the page (null for none, a group chat, or not known yet); null again on every reload. */
    val openChat: StateFlow<ChatRef?> = _openChat.asStateFlow()

    /** Raw JSON from the page. Invalid input is logged and dropped. */
    fun onRawMessage(raw: String) {
        val message = BridgeMessageParser.parse(raw)
        if (message == null) {
            Log.w(TAG, "Dropped a malformed bridge message")
            return
        }
        onMessage(message)
    }

    /** The page is reloading: nothing it said before still holds. */
    fun onPageStarted() {
        _isReady.value = false
        _generating.value = false
        _openChat.value = null
    }

    private fun onMessage(message: BridgeMessage) {
        Log.d(TAG, "Bridge message: ${message.logLabel()}")
        when (message) {
            BridgeMessage.Ready -> _isReady.value = true
            is BridgeMessage.Characters -> _characters.value = message.items.map { it.toDomain() }
            is BridgeMessage.RecentChats -> _chatActivity.value = message.items.map { it.toDomain() }
            is BridgeMessage.Lorebooks -> _lorebooks.value = message.items.map { it.toDomain() }
            is BridgeMessage.Personas -> _personas.value = message.items.map { it.toDomain() }
            is BridgeMessage.GenerationState -> _generating.value = message.running
            is BridgeMessage.ChatChanged -> _openChat.value = chatRef(message.characterId, message.chatFile)
            is BridgeMessage.ChatOpened -> _openChat.value = chatRef(message.characterId, message.chatFile)
            is BridgeMessage.Error -> Log.w(TAG, "Bridge reported an error")
            is BridgeMessage.Unknown -> Log.d(TAG, "Ignored unknown bridge message type: ${message.type}")
            is BridgeMessage.MessageReceived,
            is BridgeMessage.LorebookEntries, is BridgeMessage.LorebookResult, is BridgeMessage.TokenCount,
            is BridgeMessage.PersonaResult,
            is BridgeMessage.MessageTapped, is BridgeMessage.ChatResult, is BridgeMessage.ImpersonateReady -> Unit
        }
        _messages.tryEmit(message)
    }

    /** Type (and size for lists) only — never names or message content. */
    private fun BridgeMessage.logLabel(): String = when (this) {
        is BridgeMessage.Characters -> "characters (${items.size})"
        is BridgeMessage.RecentChats -> "recentChats (${items.size})"
        is BridgeMessage.Lorebooks -> "lorebooks (${items.size})"
        is BridgeMessage.LorebookEntries -> "lorebookEntries (${entries.size})"
        is BridgeMessage.Personas -> "personas (${items.size})"
        is BridgeMessage.PersonaResult -> if (ok) "personaResult ok" else "personaResult ${error.orEmpty()}"
        is BridgeMessage.LorebookResult -> if (ok) "lorebookResult ok" else "lorebookResult ${error.orEmpty()}"
        is BridgeMessage.ChatResult -> if (ok) "chatResult ok" else "chatResult ${error.orEmpty()}"
        is BridgeMessage.GenerationState -> "generationState $running"
        else -> this::class.simpleName.orEmpty()
    }

    private fun chatRef(characterId: String?, chatFile: String?): ChatRef? =
        if (characterId != null && chatFile != null) ChatRef(characterId, chatFile) else null

    private fun LorebookDto.toDomain() = Lorebook(
        name = name,
        entryCount = entryCount,
        isGlobal = global,
        characterIds = characterIds,
        personaNames = personaNames
    )

    private fun CharacterDto.toDomain() = CharacterSummary(
        id = id,
        name = name,
        avatarUrl = avatarUrl,
        portraitUrl = fullAvatarUrl ?: avatarUrl,
        lastChatAt = lastChatAt?.let(Instant::ofEpochMilli)
    )

    private fun RecentChatDto.toDomain() = ChatActivity(
        characterId = characterId,
        chatFile = chatFile,
        lastMessage = lastMessage,
        lastMessageAt = lastMessageAt?.let(Instant::ofEpochMilli)
    )

    private companion object {
        const val TAG = "BridgeRepository"
        const val MESSAGE_BUFFER = 16
    }
}
