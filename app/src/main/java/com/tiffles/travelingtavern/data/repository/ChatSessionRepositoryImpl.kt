package com.tiffles.travelingtavern.data.repository

import com.tiffles.travelingtavern.data.bridge.BridgeClient
import com.tiffles.travelingtavern.data.bridge.BridgeMessage
import com.tiffles.travelingtavern.data.bridge.BridgeRepository
import com.tiffles.travelingtavern.data.bridge.sendAndAwait
import com.tiffles.travelingtavern.domain.model.ChatActionResult
import com.tiffles.travelingtavern.domain.model.MessageAction
import com.tiffles.travelingtavern.domain.model.OpenChat
import com.tiffles.travelingtavern.domain.model.TappedMessage
import com.tiffles.travelingtavern.domain.repository.ChatSessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** [ChatSessionRepository] backed by the JS bridge inside the shared WebView. */
@Singleton
class ChatSessionRepositoryImpl @Inject constructor(
    private val bridge: BridgeRepository,
    private val client: BridgeClient
) : ChatSessionRepository {

    override val generating: Flow<Boolean> = bridge.generating

    override val messageTaps: Flow<TappedMessage> =
        bridge.messages.filterIsInstance<BridgeMessage.MessageTapped>().map { it.toDomain() }

    override val impersonations: Flow<String> =
        bridge.messages.filterIsInstance<BridgeMessage.ImpersonateReady>().map { it.text }

    override val chatChanges: Flow<OpenChat> =
        bridge.messages.filterIsInstance<BridgeMessage.ChatChanged>().map { OpenChat(it.characterId, it.chatFile) }

    override suspend fun runAction(message: TappedMessage, action: MessageAction): ChatActionResult =
        request(ACTION_TIMEOUT_MS) { requestId ->
            client.messageAction(requestId, message.messageId, message.sendDate, action.wireName())
        }

    override suspend fun send(text: String): Boolean = client.sendMessage(text)

    override suspend fun regenerateReply(): ChatActionResult =
        request(ACTION_TIMEOUT_MS) { requestId -> client.regenerateReply(requestId) }

    override suspend fun impersonate() {
        client.impersonate()
    }

    override suspend fun stop() {
        client.stopGeneration()
    }

    override suspend fun startNewChat(deleteCurrent: Boolean): ChatActionResult =
        request(NEW_CHAT_TIMEOUT_MS) { requestId -> client.newChat(requestId, deleteCurrent) }

    override suspend fun reloadChat() {
        client.reloadChat()
    }

    // The page's CSS pixels are dp: the WebView keeps its default zoom.
    override suspend fun revealMessage(messageId: Int, coveredDp: Int) {
        client.revealMessage(messageId, coveredDp)
    }

    override suspend fun endReveal() {
        client.endReveal()
    }

    /** Sends a command tagged with a new request ID and waits for its `chatResult`. */
    private suspend fun request(timeoutMs: Long, send: suspend (String) -> Boolean): ChatActionResult {
        val requestId = UUID.randomUUID().toString()
        val answer = withTimeoutOrNull(timeoutMs) {
            bridge.messages.sendAndAwait({ send(requestId) }) { it is BridgeMessage.ChatResult && it.requestId == requestId }
        } as? BridgeMessage.ChatResult
        return answer?.toDomain() ?: ChatActionResult.Failed
    }

    private fun BridgeMessage.ChatResult.toDomain(): ChatActionResult = when {
        ok -> ChatActionResult.Done(text)
        error == ERROR_STALE -> ChatActionResult.Stale
        error == ERROR_BUSY -> ChatActionResult.Busy
        else -> ChatActionResult.Failed
    }

    private fun BridgeMessage.MessageTapped.toDomain() = TappedMessage(
        messageId = messageId,
        sendDate = sendDate,
        senderName = name,
        isUser = isUser,
        isLatest = isLatest,
        isHidden = isHidden,
        canRegenerate = canRegenerate,
        canContinue = canContinue
    )

    internal companion object {
        const val ACTION_TIMEOUT_MS = 10_000L
        const val NEW_CHAT_TIMEOUT_MS = 15_000L
        private const val ERROR_STALE = "stale"
        private const val ERROR_BUSY = "busy"

        /** The action names `bridge.js` understands. */
        fun MessageAction.wireName(): String = when (this) {
            MessageAction.Regenerate -> "regenerate"
            MessageAction.Continue -> "continue"
            MessageAction.Copy -> "copy"
            MessageAction.Edit -> "edit"
            MessageAction.Branch -> "branch"
            MessageAction.Hide -> "hide"
            MessageAction.Show -> "show"
            MessageAction.Delete -> "delete"
        }
    }
}
