package com.tiffles.travelingtavern.domain.model

import java.time.Instant

/**
 * The latest activity in one SillyTavern chat file. A character can have several chats.
 * [lastMessage] is null when the chat has no messages yet.
 */
data class ChatActivity(
    val characterId: String,
    val lastMessage: String?,
    val lastMessageAt: Instant?
)

/** A row in the Chats tab: a character and the latest activity across its chats. */
data class RecentChat(
    val character: CharacterSummary,
    val lastMessage: String?,
    val lastMessageAt: Instant?
)
