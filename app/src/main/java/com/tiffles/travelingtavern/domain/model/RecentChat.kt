package com.tiffles.travelingtavern.domain.model

import java.time.Instant

/**
 * The latest activity in one SillyTavern chat file. A character can have several chats.
 * [chatFile] is SillyTavern's chat file name without `.jsonl` (null if not reported);
 * [lastMessage] is null when the chat has no messages yet.
 */
data class ChatActivity(
    val characterId: String,
    val lastMessage: String?,
    val lastMessageAt: Instant?,
    val chatFile: String? = null
)

/** A row in the Chats tab: one of a character's chats ([chatFile]) and its latest message. */
data class RecentChat(
    val character: CharacterSummary,
    val lastMessage: String?,
    val lastMessageAt: Instant?,
    val chatFile: String? = null
)
