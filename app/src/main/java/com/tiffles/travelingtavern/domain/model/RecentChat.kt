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

/** The row's chat as SillyTavern identifies it, or null if SillyTavern didn't report the file. */
val RecentChat.ref: ChatRef?
    get() = chatFile?.let { ChatRef(character.id, it) }

/**
 * One SillyTavern chat, by SillyTavern's own keys: the character's avatar file ([characterId])
 * and the chat file name without `.jsonl` ([chatFile]).
 */
data class ChatRef(val characterId: String, val chatFile: String)

/**
 * The Chats tab's list: [visible] rows, and how many of SillyTavern's chats are hidden in the
 * app ([hiddenCount]; hidden entries for chats that no longer exist don't count).
 */
data class ChatList(val visible: List<RecentChat>, val hiddenCount: Int)
