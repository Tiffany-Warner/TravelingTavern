package com.tiffles.travelingtavern.domain.repository

import com.tiffles.travelingtavern.domain.model.CharacterSummary
import com.tiffles.travelingtavern.domain.model.ChatActivity
import com.tiffles.travelingtavern.domain.model.PageState
import kotlinx.coroutines.flow.Flow

/**
 * The user's characters and chats as SillyTavern reports them. Today the source is the JS
 * bridge; a TravelingTavern-Bridge endpoint can replace it without touching the UI.
 */
interface CharacterRepository {

    /** The whole library, or null until SillyTavern has reported it this session. */
    val characters: Flow<List<CharacterSummary>?>

    /** Latest activity per chat file, or null until SillyTavern has reported it. */
    val chatActivity: Flow<List<ChatActivity>?>

    /** Whether the SillyTavern page is loading, ready or failed (server unreachable, login, …). */
    val pageState: Flow<PageState>

    /** Asks SillyTavern for fresh lists. Returns once they arrived, or after a timeout. */
    suspend fun refresh()

    /** Reloads the SillyTavern page after an error. */
    fun retry()

    /**
     * Opens [characterId]'s chat in SillyTavern (waiting for the page to be ready first):
     * [chatFile] (without `.jsonl`) if it exists, else the character's current chat. Returns
     * once SillyTavern has drawn it and it has stopped scrolling, with the chat file that
     * opened — or null if it failed or timed out. A chat marked stale is re-read first.
     */
    suspend fun openChat(characterId: String, chatFile: String? = null): String?

    /**
     * Whether the Chat screen is showing. When the app comes back from the background the
     * open chat is re-read if it is, and marked stale (re-read on the next open) if not.
     */
    fun setChatVisible(visible: Boolean)
}
