package com.tiffles.travelingtavern.domain

import com.tiffles.travelingtavern.domain.model.ChatRef
import com.tiffles.travelingtavern.domain.repository.HiddenChatsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** In-memory [HiddenChatsRepository] for tests. */
class FakeHiddenChatsRepository : HiddenChatsRepository {
    override val hiddenChats = MutableStateFlow<Set<ChatRef>>(emptySet())

    override suspend fun hide(chat: ChatRef) = hiddenChats.update { it + chat }

    override suspend fun unhide(chat: ChatRef) = hiddenChats.update { it - chat }

    override suspend fun showAll() {
        hiddenChats.value = emptySet()
    }
}
