package com.tiffles.travelingtavern.domain.repository

import com.tiffles.travelingtavern.domain.model.ChatActionResult
import com.tiffles.travelingtavern.domain.model.MessageAction
import com.tiffles.travelingtavern.domain.model.OpenChat
import com.tiffles.travelingtavern.domain.model.TappedMessage
import kotlinx.coroutines.flow.Flow

/**
 * The open chat in SillyTavern: what happens in it and the commands the Chat screen sends.
 * Everything runs through SillyTavern's own code (the JS bridge).
 */
interface ChatSessionRepository {

    /** True while SillyTavern is generating (a reply or impersonate). */
    val generating: Flow<Boolean>

    /** Messages the user tapped in the page. */
    val messageTaps: Flow<TappedMessage>

    /** Text impersonate wrote for the user. */
    val impersonations: Flow<String>

    /** Each time the page switches chats (branch, new chat, restart, another character). */
    val chatChanges: Flow<OpenChat>

    /** Runs [action] on [message], unless it changed meanwhile ([ChatActionResult.Stale]). */
    suspend fun runAction(message: TappedMessage, action: MessageAction): ChatActionResult

    /** Sends [text] through SillyTavern's own send. */
    suspend fun send(text: String)

    suspend fun impersonate()

    suspend fun stop()

    /** Start new chat, or Restart chat (the current chat is deleted) when [deleteCurrent]. */
    suspend fun startNewChat(deleteCurrent: Boolean): ChatActionResult

    /** Re-reads the open chat from the server (after a running generation ends). */
    suspend fun reloadChat()

    /** Scrolls message [messageId] into the part of the page the actions sheet leaves visible ([coveredDp] covered). */
    suspend fun revealMessage(messageId: Int, coveredDp: Int)

    /** The actions sheet closed. */
    suspend fun endReveal()
}
