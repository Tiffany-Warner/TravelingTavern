package com.tiffles.travelingtavern.domain.usecase

import com.tiffles.travelingtavern.domain.model.CharacterSummary
import com.tiffles.travelingtavern.domain.model.ChatActivity
import com.tiffles.travelingtavern.domain.model.RecentChat
import com.tiffles.travelingtavern.domain.repository.CharacterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import javax.inject.Inject

/**
 * The Chats tab's rows: one per chat (a character with two chats has two rows), sorted by
 * last message (newest first). Null until SillyTavern has reported both lists.
 */
class GetRecentChatsUseCase @Inject constructor(
    private val repository: CharacterRepository
) {
    operator fun invoke(): Flow<List<RecentChat>?> =
        combine(repository.characters, repository.chatActivity, ::recentChats)
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
