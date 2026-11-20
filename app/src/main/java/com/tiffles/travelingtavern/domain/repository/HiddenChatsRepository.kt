package com.tiffles.travelingtavern.domain.repository

import com.tiffles.travelingtavern.domain.model.ChatRef
import kotlinx.coroutines.flow.Flow

/**
 * Chats hidden from the Chats tab. App-only and display-only: nothing changes in SillyTavern,
 * and nothing here affects generation.
 */
interface HiddenChatsRepository {

    val hiddenChats: Flow<Set<ChatRef>>

    suspend fun hide(chat: ChatRef)

    suspend fun unhide(chat: ChatRef)

    /** Shows every hidden chat again. */
    suspend fun showAll()
}
