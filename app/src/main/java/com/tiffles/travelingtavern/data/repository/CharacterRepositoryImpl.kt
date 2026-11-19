package com.tiffles.travelingtavern.data.repository

import android.util.Log
import com.tiffles.travelingtavern.data.bridge.BridgeClient
import com.tiffles.travelingtavern.data.bridge.BridgeMessage
import com.tiffles.travelingtavern.data.bridge.BridgeRepository
import com.tiffles.travelingtavern.data.webview.WebViewHolder
import com.tiffles.travelingtavern.domain.model.CharacterSummary
import com.tiffles.travelingtavern.domain.model.ChatActivity
import com.tiffles.travelingtavern.domain.model.PageState
import com.tiffles.travelingtavern.domain.repository.CharacterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** [CharacterRepository] backed by the JS bridge inside the shared WebView. */
@Singleton
class CharacterRepositoryImpl @Inject constructor(
    private val bridge: BridgeRepository,
    private val client: BridgeClient,
    private val holder: WebViewHolder
) : CharacterRepository {

    override val characters: Flow<List<CharacterSummary>?> = bridge.characters

    override val chatActivity: Flow<List<ChatActivity>?> = bridge.chatActivity

    override val pageState: Flow<PageState> = holder.pageState

    override suspend fun refresh() {
        withTimeoutOrNull(REFRESH_TIMEOUT_MS) {
            // Subscribe before asking, so a fast answer can't slip past.
            bridge.messages
                .onSubscription {
                    client.requestCharacters(fromServer = true)
                    client.requestRecentChats()
                }
                .first { it is BridgeMessage.RecentChats || it is BridgeMessage.Error }
        }
    }

    override fun retry() = holder.retry()

    /**
     * The wait for `chatOpened` is capped at [OPEN_CHAT_TIMEOUT_MS] from the moment the chat
     * is requested (waiting for the page to load doesn't count): past that, Chat reveals the
     * page anyway rather than keep the user looking at a cover.
     */
    override suspend fun openChat(characterId: String) {
        bridge.isReady.first { it }
        val answer = withTimeoutOrNull(OPEN_CHAT_TIMEOUT_MS) {
            bridge.messages
                .onSubscription { client.selectCharacter(characterId) }
                .first { it.isChatOpened(characterId) || it is BridgeMessage.Error }
        }
        if (answer == null) {
            Log.w(TAG, "No chatOpened within $OPEN_CHAT_TIMEOUT_MS ms; showing the chat anyway")
        }
    }

    private fun BridgeMessage.isChatOpened(characterId: String) =
        this is BridgeMessage.ChatOpened && this.characterId == characterId

    internal companion object {
        private const val TAG = "CharacterRepository"
        private const val REFRESH_TIMEOUT_MS = 5_000L
        const val OPEN_CHAT_TIMEOUT_MS = 3_000L
    }
}
