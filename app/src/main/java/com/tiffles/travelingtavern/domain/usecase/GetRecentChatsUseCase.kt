package com.tiffles.travelingtavern.domain.usecase

import com.tiffles.travelingtavern.domain.model.CharacterSummary
import com.tiffles.travelingtavern.domain.model.ChatActivity
import com.tiffles.travelingtavern.domain.model.ChatList
import com.tiffles.travelingtavern.domain.model.ChatRef
import com.tiffles.travelingtavern.domain.model.RecentChat
import com.tiffles.travelingtavern.domain.model.ref
import com.tiffles.travelingtavern.domain.repository.CharacterRepository
import com.tiffles.travelingtavern.domain.repository.HiddenChatsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import javax.inject.Inject

/**
 * The Chats tab's rows: one per chat (a character with two chats has two rows), sorted by
 * last message (newest first), without the chats hidden in the app. Null until SillyTavern
 * has reported both lists.
 */
class GetRecentChatsUseCase @Inject constructor(
    private val repository: CharacterRepository,
    private val hiddenChats: HiddenChatsRepository
) {
    operator fun invoke(): Flow<ChatList?> =
        combine(repository.characters, repository.chatActivity, hiddenChats.hiddenChats) { characters, activity, hidden ->
            recentChats(characters, activity)?.let { chats -> splitHidden(chats, hidden) }
        }
}

/**
 * Hidden entries match rows by SillyTavern's keys, so an entry for a chat that no longer exists
 * matches nothing: it neither hides a row nor counts as hidden.
 */
private fun splitHidden(chats: List<RecentChat>, hidden: Set<ChatRef>): ChatList {
    val (hiddenRows, visible) = chats.partition { chat -> chat.ref in hidden }
    return ChatList(visible = visible, hiddenCount = hiddenRows.size)
}

private fun recentChats(
    characters: List<CharacterSummary>?,
    activity: List<ChatActivity>?
): List<RecentChat>? {
    if (characters == null || activity == null) return null
    val charactersById = characters.associateBy { it.id }
    return activity
        .mapNotNull { chat ->
            // Chats for a character that's no longer in the library are skipped.
            val character = charactersById[chat.characterId] ?: return@mapNotNull null
            RecentChat(character, chat.lastMessage, chat.lastMessageAt, chat.chatFile)
        }
        .sortedWith(
            compareByDescending<RecentChat, Instant?>(nullsFirst()) { it.lastMessageAt }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.character.name }
        )
}
