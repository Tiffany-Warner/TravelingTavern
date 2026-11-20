package com.tiffles.travelingtavern.domain

import com.tiffles.travelingtavern.domain.model.ChatActionResult
import com.tiffles.travelingtavern.domain.model.MessageAction
import com.tiffles.travelingtavern.domain.model.OpenChat
import com.tiffles.travelingtavern.domain.model.TappedMessage
import com.tiffles.travelingtavern.domain.repository.ChatSessionRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [ChatSessionRepository] for tests: emit page events, read back the commands. */
class FakeChatSessionRepository : ChatSessionRepository {
    override val generating = MutableStateFlow(false)
    override val messageTaps = MutableSharedFlow<TappedMessage>(extraBufferCapacity = 8)
    override val impersonations = MutableSharedFlow<String>(extraBufferCapacity = 8)
    override val chatChanges = MutableSharedFlow<OpenChat>(extraBufferCapacity = 8)

    val actions = mutableListOf<Pair<TappedMessage, MessageAction>>()
    val sent = mutableListOf<String>()
    val newChats = mutableListOf<Boolean>()
    var impersonateCalls = 0
    var stopCalls = 0
    var reloadCalls = 0

    /** What [runAction] answers. */
    var actionResult: ChatActionResult = ChatActionResult.Done()

    /** What [startNewChat] answers. */
    var newChatResult: ChatActionResult = ChatActionResult.Done()

    override suspend fun runAction(message: TappedMessage, action: MessageAction): ChatActionResult {
        actions += message to action
        return actionResult
    }

    override suspend fun send(text: String) {
        sent += text
    }

    override suspend fun impersonate() {
        impersonateCalls++
    }

    override suspend fun stop() {
        stopCalls++
    }

    override suspend fun startNewChat(deleteCurrent: Boolean): ChatActionResult {
        newChats += deleteCurrent
        return newChatResult
    }

    override suspend fun reloadChat() {
        reloadCalls++
    }

    val reveals = mutableListOf<Pair<Int, Int>>()
    var endRevealCalls = 0

    override suspend fun revealMessage(messageId: Int, coveredDp: Int) {
        reveals += messageId to coveredDp
    }

    override suspend fun endReveal() {
        endRevealCalls++
    }
}

fun tappedMessage(
    id: Int = 3,
    isUser: Boolean = false,
    isLatest: Boolean = true,
    isHidden: Boolean = false,
    canRegenerate: Boolean = isLatest && !isHidden,
    canContinue: Boolean = isLatest && !isUser && !isHidden
) = TappedMessage(
    messageId = id,
    sendDate = "2026-11-19T15:0$id:00.000Z",
    senderName = if (isUser) "User" else "Tide Pods",
    isUser = isUser,
    isLatest = isLatest,
    isHidden = isHidden,
    canRegenerate = canRegenerate,
    canContinue = canContinue
)
