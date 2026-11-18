package com.tiffles.travelingtavern.domain

import com.tiffles.travelingtavern.domain.model.CharacterSummary
import com.tiffles.travelingtavern.domain.model.ChatActivity
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

    var refreshCalls = 0
    var retryCalls = 0
    val openedChats = mutableListOf<String>()

    /** When set, [refresh] suspends until it completes. */
    var refreshGate: CompletableDeferred<Unit>? = null

    /** When set, [openChat] suspends until it completes (SillyTavern still drawing the chat). */
    var openGate: CompletableDeferred<Unit>? = null

    override suspend fun refresh() {
        refreshCalls++
        refreshGate?.await()
    }

    override fun retry() {
        retryCalls++
    }

    override suspend fun openChat(characterId: String) {
        openedChats += characterId
        openGate?.await()
    }
}

fun character(name: String, id: String = "$name.png") = CharacterSummary(
    id = id,
    name = name,
    avatarUrl = "http://100.64.0.2:8000/thumbnail?type=avatar&file=$id",
    portraitUrl = "http://100.64.0.2:8000/characters/$id",
    lastChatAt = null
)

fun activity(characterId: String, lastMessage: String?, at: String?) = ChatActivity(
    characterId = characterId,
    lastMessage = lastMessage,
    lastMessageAt = at?.let(Instant::parse)
)
