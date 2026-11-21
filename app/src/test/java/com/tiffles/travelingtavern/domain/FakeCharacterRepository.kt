package com.tiffles.travelingtavern.domain

import com.tiffles.travelingtavern.domain.model.CharacterSummary
import com.tiffles.travelingtavern.domain.model.ChatActivity
import com.tiffles.travelingtavern.domain.model.ChatRef
import com.tiffles.travelingtavern.domain.model.PageState
import com.tiffles.travelingtavern.domain.repository.CharacterRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import java.time.Instant

/** In-memory [CharacterRepository] for tests; set the flows directly. */
class FakeCharacterRepository : CharacterRepository {
    override val characters = MutableStateFlow<List<CharacterSummary>?>(null)
    override val chatActivity = MutableStateFlow<List<ChatActivity>?>(null)
    override val pageState = MutableStateFlow<PageState>(PageState.Ready)
    override val generatingChat = MutableStateFlow<ChatRef?>(null)

    var refreshCalls = 0
    var retryCalls = 0
    val openedChats = mutableListOf<String>()
    val requestedChatFiles = mutableListOf<String?>()
    var visibleNow = false
    val deletedChats = mutableListOf<ChatRef>()

    /** What [deleteChat] reports. */
    var deleteSucceeds = true

    /** When set, [deleteChat] suspends until it completes. */
    var deleteGate: CompletableDeferred<Unit>? = null

    /** What [openChat] reports as opened; by default the requested file, or this when none was requested. */
    var currentChatFile: String? = "current"

    /** When set, [refresh] suspends until it completes. */
    var refreshGate: CompletableDeferred<Unit>? = null

    /** When set, [openChat] suspends until it completes (SillyTavern still drawing the chat). */
    var openGate: CompletableDeferred<Unit>? = null

    override suspend fun refresh() {
        refreshCalls++
        refreshGate?.await()
    }

    override suspend fun deleteChat(chat: ChatRef): Boolean {
        deletedChats += chat
        deleteGate?.await()
        return deleteSucceeds
    }

    override fun retry() {
        retryCalls++
    }

    override suspend fun openChat(characterId: String, chatFile: String?): String? {
        openedChats += characterId
        requestedChatFiles += chatFile
        openGate?.await()
        return chatFile ?: currentChatFile
    }

    override fun setChatVisible(visible: Boolean) {
        visibleNow = visible
    }
}

fun character(name: String, id: String = "$name.png") = CharacterSummary(
    id = id,
    name = name,
    avatarUrl = "http://192.0.2.1:8000/thumbnail?type=avatar&file=$id",
    portraitUrl = "http://192.0.2.1:8000/characters/$id",
    lastChatAt = null
)

fun activity(characterId: String, lastMessage: String?, at: String?, chatFile: String? = null) = ChatActivity(
    characterId = characterId,
    lastMessage = lastMessage,
    lastMessageAt = at?.let(Instant::parse),
    chatFile = chatFile
)
