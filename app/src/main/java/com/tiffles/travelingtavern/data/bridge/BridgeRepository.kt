package com.tiffles.travelingtavern.data.bridge

import android.util.Log
import com.tiffles.travelingtavern.domain.model.CharacterSummary
import com.tiffles.travelingtavern.domain.model.ChatActivity
import com.tiffles.travelingtavern.domain.model.Lorebook
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
    private val _isReady = MutableStateFlow(false)

    /** Every valid message, including ones handled here. One-shot events (chat changed, new message). */
    val messages: SharedFlow<BridgeMessage> = _messages.asSharedFlow()

    /** Null until the page first reports them; kept across reloads until replaced. */
    val characters: StateFlow<List<CharacterSummary>?> = _characters.asStateFlow()

    /** Latest activity per chat file. Null until the page first reports it. */
    val chatActivity: StateFlow<List<ChatActivity>?> = _chatActivity.asStateFlow()

    /** Every lorebook. Null until the page first reports them. */
    val lorebooks: StateFlow<List<Lorebook>?> = _lorebooks.asStateFlow()

    /** True once SillyTavern has loaded and the bridge is listening; false again on every reload. */
    val isReady: StateFlow<Boolean> = _isReady.asStateFlow()

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
    }

    private fun onMessage(message: BridgeMessage) {
        Log.d(TAG, "Bridge message: ${message.logLabel()}")
        when (message) {
            BridgeMessage.Ready -> _isReady.value = true
            is BridgeMessage.Characters -> _characters.value = message.items.map { it.toDomain() }
            is BridgeMessage.RecentChats -> _chatActivity.value = message.items.map { it.toDomain() }
            is BridgeMessage.Lorebooks -> _lorebooks.value = message.items.map { it.toDomain() }
            is BridgeMessage.Error -> Log.w(TAG, "Bridge reported an error")
            is BridgeMessage.Unknown -> Log.d(TAG, "Ignored unknown bridge message type: ${message.type}")
            is BridgeMessage.ChatChanged, is BridgeMessage.ChatOpened, is BridgeMessage.MessageReceived,
            is BridgeMessage.LorebookEntries, is BridgeMessage.LorebookResult, is BridgeMessage.TokenCount -> Unit
        }
        _messages.tryEmit(message)
    }

    /** Type (and size for lists) only — never names or message content. */
    private fun BridgeMessage.logLabel(): String = when (this) {
        is BridgeMessage.Characters -> "characters (${items.size})"
        is BridgeMessage.RecentChats -> "recentChats (${items.size})"
        is BridgeMessage.Lorebooks -> "lorebooks (${items.size})"
        is BridgeMessage.LorebookEntries -> "lorebookEntries (${entries.size})"
        is BridgeMessage.LorebookResult -> if (ok) "lorebookResult ok" else "lorebookResult ${error.orEmpty()}"
        else -> this::class.simpleName.orEmpty()
    }

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
        lastMessage = lastMessage,
        lastMessageAt = lastMessageAt?.let(Instant::ofEpochMilli)
    )

    private companion object {
        const val TAG = "BridgeRepository"
        const val MESSAGE_BUFFER = 16
    }
}
