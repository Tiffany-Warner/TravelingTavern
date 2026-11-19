package com.tiffles.travelingtavern.data.bridge

import kotlinx.serialization.Serializable

/** A message posted by `bridge.js`. Data-layer only — never handed to the UI. */
sealed interface BridgeMessage {

    /** SillyTavern finished loading and the bridge is listening. */
    data object Ready : BridgeMessage

    @Serializable
    data class Characters(val items: List<CharacterDto>) : BridgeMessage

    /** [characterId] is null when no single character is open (none, or a group chat). */
    /** SillyTavern's recent chats, one per chat file, newest first. */
    @Serializable
    data class RecentChats(val items: List<RecentChatDto>) : BridgeMessage

    @Serializable
    data class ChatChanged(val characterId: String? = null) : BridgeMessage

    /** The chat [selectCharacter][BridgeClient.selectCharacter] asked for is drawn and settled. */
    @Serializable
    data class ChatOpened(val characterId: String) : BridgeMessage

    @Serializable
    data class MessageReceived(val characterId: String? = null, val messageId: Int) : BridgeMessage

    /** Every lorebook (World Info) with its entry count and links. */
    @Serializable
    data class Lorebooks(val items: List<LorebookDto>) : BridgeMessage

    /** The entries of one lorebook, answering `requestLorebook`. */
    @Serializable
    data class LorebookEntries(
        val requestId: String,
        val name: String,
        val entries: List<LorebookEntryDto>
    ) : BridgeMessage

    /**
     * The answer to a lorebook action. [error] is `notFound`, `exists` or `failed`; [name] is
     * set by `createLorebook`, [uid] by `saveLorebookEntry`.
     */
    @Serializable
    data class LorebookResult(
        val requestId: String,
        val ok: Boolean,
        val error: String? = null,
        val name: String? = null,
        val uid: Int? = null
    ) : BridgeMessage

    @Serializable
    data class TokenCount(val requestId: String, val count: Int) : BridgeMessage

    /**
     * The in-page mic button was tapped. [target] is `story` (SillyTavern's main chat) or
     * `text` (EchoText's input, from 1.7); anything else is ignored by the receiver.
     */
    @Serializable
    data class MicPressed(val target: String) : BridgeMessage

    @Serializable
    data class Error(val message: String) : BridgeMessage

    /** A type this app version doesn't know (e.g. from a newer bridge script). Ignored. */
    data class Unknown(val type: String) : BridgeMessage
}

/**
 * A character as `bridge.js` reports it. [avatarUrl] is the small thumbnail,
 * [fullAvatarUrl] the original image. [lastChatAt] is epoch milliseconds.
 */
@Serializable
data class CharacterDto(
    val id: String,
    val name: String,
    val avatarUrl: String,
    val fullAvatarUrl: String? = null,
    val lastChatAt: Long? = null
)

/**
 * One chat file from SillyTavern's recent chats. [lastMessage] is trimmed to 200 characters
 * and null for a chat with no messages; [lastMessageAt] is epoch milliseconds.
 */
@Serializable
data class RecentChatDto(
    val characterId: String,
    val lastMessage: String? = null,
    val lastMessageAt: Long? = null,
    val messageCount: Int = 0
)

/**
 * A lorebook as `bridge.js` reports it. [entryCount] is null if it couldn't be read;
 * [personaNames] may hold an empty name for the current persona.
 */
@Serializable
data class LorebookDto(
    val name: String,
    val entryCount: Int? = null,
    val global: Boolean = false,
    val characterIds: List<String> = emptyList(),
    val personaNames: List<String> = emptyList()
)

/** One lorebook entry: only the fields the app shows or edits. */
@Serializable
data class LorebookEntryDto(
    val uid: Int,
    val title: String = "",
    val keys: List<String> = emptyList(),
    val content: String = "",
    val enabled: Boolean = true
)
