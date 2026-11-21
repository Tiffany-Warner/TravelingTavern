package com.tiffles.travelingtavern.data.repository

import android.util.Log
import com.tiffles.travelingtavern.data.bridge.BridgeClient
import com.tiffles.travelingtavern.data.bridge.BridgeMessage
import com.tiffles.travelingtavern.data.bridge.BridgeRepository
import com.tiffles.travelingtavern.data.bridge.CommandOrigin
import com.tiffles.travelingtavern.data.bridge.sendAndAwait
import com.tiffles.travelingtavern.data.lifecycle.AppForegroundTracker
import com.tiffles.travelingtavern.data.webview.WebViewHolder
import com.tiffles.travelingtavern.di.ApplicationScope
import com.tiffles.travelingtavern.domain.model.CharacterSummary
import com.tiffles.travelingtavern.domain.model.ChatActivity
import com.tiffles.travelingtavern.domain.model.ChatRef
import com.tiffles.travelingtavern.domain.model.PageState
import com.tiffles.travelingtavern.domain.repository.CharacterRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** [CharacterRepository] backed by the JS bridge inside the shared WebView. */
@Singleton
class CharacterRepositoryImpl @Inject constructor(
    private val bridge: BridgeRepository,
    private val client: BridgeClient,
    private val holder: WebViewHolder,
    foreground: AppForegroundTracker,
    @ApplicationScope private val scope: CoroutineScope
) : CharacterRepository {

    @Volatile private var chatVisible = false

    /** The page's open chat may be behind (another page added messages while the app was away). */
    @Volatile private var chatStale = false

    init {
        scope.launch { foreground.isForeground.collect { client.setAppState(it) } }
        scope.launch { foreground.returns.collect { onAppReturned() } }
    }

    override val characters: Flow<List<CharacterSummary>?> = bridge.characters

    override val chatActivity: Flow<List<ChatActivity>?> = bridge.chatActivity

    override val pageState: Flow<PageState> = holder.pageState

    override val generatingChat: Flow<ChatRef?> =
        combine(bridge.generating, bridge.openChat) { generating, open -> if (generating) open else null }

    override suspend fun refresh() {
        withTimeoutOrNull(REFRESH_TIMEOUT_MS) {
            // Subscribe before asking, so a fast answer can't slip past.
            bridge.messages.sendAndAwait({ client.requestCharacters(fromServer = true) && client.requestRecentChats(CommandOrigin.User) }) {
                it is BridgeMessage.RecentChats || it is BridgeMessage.Error
            }
        }
    }

    override fun retry() = holder.retry()

    /** Capped at [DELETE_CHAT_TIMEOUT_MS] including the wait for the page, so Tailscale off fails instead of hanging. */
    override suspend fun deleteChat(chat: ChatRef): Boolean {
        val requestId = UUID.randomUUID().toString()
        val answer = withTimeoutOrNull(DELETE_CHAT_TIMEOUT_MS) {
            bridge.isReady.first { it }
            bridge.messages.sendAndAwait({ client.deleteChat(requestId, chat.characterId, chat.chatFile) }) {
                it is BridgeMessage.ChatResult && it.requestId == requestId
            }
        } as? BridgeMessage.ChatResult
        if (answer?.ok != true) {
            Log.w(TAG, "Delete chat failed: ${answer?.error ?: "timed out"}")
            return false
        }
        refresh()
        return true
    }

    /**
     * The wait for `chatOpened` is capped at [OPEN_CHAT_TIMEOUT_MS] from the moment the chat
     * is requested (waiting for the page to load doesn't count): past that, Chat reveals the
     * page anyway rather than keep the user looking at a cover.
     */
    override suspend fun openChat(characterId: String, chatFile: String?): String? {
        bridge.isReady.first { it }
        val requestId = UUID.randomUUID().toString()
        val reload = chatStale
        chatStale = false
        val answer = withTimeoutOrNull(OPEN_CHAT_TIMEOUT_MS) {
            bridge.messages.sendAndAwait({ client.selectCharacter(requestId, characterId, chatFile, reload) }) {
                it.isChatOpened(requestId) || it is BridgeMessage.Error
            }
        }
        val opened = answer as? BridgeMessage.ChatOpened
        when {
            answer == null -> Log.w(TAG, "No chatOpened within $OPEN_CHAT_TIMEOUT_MS ms; showing the chat anyway")
            chatFile != null && opened != null && opened.chatFile != chatFile ->
                Log.w(TAG, "Requested chat file not found; opened the character's current chat")
        }
        return opened?.chatFile
    }

    override fun setChatVisible(visible: Boolean) {
        chatVisible = visible
        // Leaving the chat shows the Chats list again. SillyTavern saves a reply only after its
        // MESSAGE_RECEIVED handlers finish (and has no "saved" event), so the list fetched on that
        // event can miss the newest message; asking again here catches the saved file.
        if (!visible) scope.launch { client.requestRecentChats(CommandOrigin.App) }
    }

    private suspend fun onAppReturned() {
        client.requestRecentChats(CommandOrigin.App)
        if (chatVisible) client.reloadChat() else chatStale = true
    }

    private fun BridgeMessage.isChatOpened(requestId: String) =
        this is BridgeMessage.ChatOpened && this.requestId == requestId

    internal companion object {
        private const val TAG = "CharacterRepository"
        private const val REFRESH_TIMEOUT_MS = 5_000L
        const val OPEN_CHAT_TIMEOUT_MS = 3_000L
        const val DELETE_CHAT_TIMEOUT_MS = 10_000L
    }
}
