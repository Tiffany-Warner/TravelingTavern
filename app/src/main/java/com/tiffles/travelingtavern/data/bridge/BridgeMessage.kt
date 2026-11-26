package com.tiffles.travelingtavern.data.bridge

import kotlinx.serialization.Serializable

/** A message posted by `bridge.js`. Data-layer only — never handed to the UI. */
sealed interface BridgeMessage {

    /** SillyTavern finished loading and the bridge is listening. */
    data object Ready : BridgeMessage

    /** After `interruptReply`: the page's chat save request has completed (any status). Once. */
    data object ChatSaved : BridgeMessage

    @Serializable
    data class Characters(val items: List<CharacterDto>) : BridgeMessage

    /** SillyTavern's recent chats, one per chat file, newest first. */
    @Serializable
    data class RecentChats(val items: List<RecentChatDto>) : BridgeMessage

    /**
     * SillyTavern switched chats. [characterId] is null when no single character is open (none,
     * or a group chat); [chatFile] is the chat file name without `.jsonl`.
     */
    @Serializable
    data class ChatChanged(val characterId: String? = null, val chatFile: String? = null) : BridgeMessage

    /**
     * The chat [selectCharacter][BridgeClient.selectCharacter] asked for ([requestId]) is drawn
     * and settled. [chatFile] is the chat that actually opened.
     */
    @Serializable
    data class ChatOpened(
        val requestId: String,
        val characterId: String,
        val chatFile: String? = null
    ) : BridgeMessage

    /** SillyTavern started ([running]) or stopped generating (replies and impersonate). */
    @Serializable
    data class GenerationState(val running: Boolean) : BridgeMessage

    /** A message was tapped in clean mode. [sendDate] identifies it for the stale check. */
    @Serializable
    data class MessageTapped(
        val messageId: Int,
        val sendDate: String,
        val name: String = "",
        val isUser: Boolean,
        val isLatest: Boolean,
        val isHidden: Boolean = false,
        val canRegenerate: Boolean = false,
        val canContinue: Boolean = false
    ) : BridgeMessage

    /**
     * The answer to a chat command ([requestId]). [error] is `stale`, `busy` or `failed`;
     * [text] is set by the copy action.
     */
    @Serializable
    data class ChatResult(
        val requestId: String,
        val ok: Boolean,
        val error: String? = null,
        val text: String? = null
    ) : BridgeMessage

    /** Impersonate finished; [text] is what it wrote for the user. */
    @Serializable
    data class ImpersonateReady(val text: String) : BridgeMessage

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

    /** Every persona, with its badges. Descriptions are cut to 300 characters for previews. */
    @Serializable
    data class Personas(val items: List<PersonaDto>) : BridgeMessage

    /**
     * The answer to a persona action. [error] is `notFound` or `failed`; [avatarId] is set by
     * `createPersona`, [persona] (with the full description) by `requestPersona`.
     */
    @Serializable
    data class PersonaResult(
        val requestId: String,
        val ok: Boolean,
        val error: String? = null,
        val avatarId: String? = null,
        val persona: PersonaDto? = null
    ) : BridgeMessage

    @Serializable
    data class TokenCount(val requestId: String, val count: Int) : BridgeMessage

    @Serializable
    data class Error(val message: String) : BridgeMessage

    /**
     * The session lease's state for this page load (extension spec E11), sent by
     * `session-lease.js` once the start decision is made and on every change. Every field is
     * required except [actions]. [state] (`holder` / `parked` / `off`) and [reason] are kept as
     * sent, so unknown values survive parsing; [toLeaseStatus] types them. [epoch] is null if this
     * load never held the lease; [pageId] is null when protection is off. [actions] (extension
     * rc.5+): the ways out offered now, primary first, unknown strings kept here and dropped when
     * typed; null from an older page, which then gets the rc.4-style fallback (D46).
     */
    @Serializable
    data class LeaseState(
        val state: String,
        val reason: String?,
        val pageId: String?,
        val epoch: Long?,
        val busy: Boolean,
        val connected: Boolean,
        val origin: String,
        val actions: List<String>? = null
    ) : BridgeMessage

    /**
     * Lease versions for this page load (E11): the running copy's [leaseVersion] and the
     * installed desktop extension's, null when none is installed. [stVersion] is null if
     * SillyTavern's version couldn't be read.
     */
    @Serializable
    data class LeaseInfo(
        val leaseVersion: Int,
        val extensionVersion: String?,
        val extensionLeaseVersion: Int?,
        val stVersion: String?,
        val untestedServer: Boolean
    ) : BridgeMessage

    /** A one-off lease event (E11): [kind] `replyNotStarted` or `notConnected`, kept as sent. */
    @Serializable
    data class LeaseNotice(val kind: String) : BridgeMessage

    /**
     * The answer to `connectModel` (A7, D2): [outcome] `pressed` (SillyTavern's connect button for
     * the selected API was pressed), `alreadyConnecting` (an attempt was running: nothing pressed),
     * `noButton` (the selected API has none), `alreadyConnected` or `noContext`, kept as sent.
     * [connected]: SillyTavern's status at the end of the wait.
     */
    @Serializable
    data class ConnectResult(val requestId: String, val outcome: String, val connected: Boolean) : BridgeMessage

    /**
     * The answer to an extension install or update (A8), tagged [requestId]. [status] is
     * SillyTavern's HTTP status (0 when no answer came back, e.g. a network error); [upToDate] is
     * set for a successful update only (`isUpToDate`: true means there was nothing to pull).
     */
    @Serializable
    data class ExtensionResult(
        val requestId: String,
        val ok: Boolean,
        val status: Int,
        val upToDate: Boolean? = null
    ) : BridgeMessage

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
 * One chat file from SillyTavern's recent chats. [chatFile] is its name without `.jsonl`.
 * [lastMessage] is trimmed to 200 characters and null for a chat with no messages;
 * [lastMessageAt] is epoch milliseconds.
 */
@Serializable
data class RecentChatDto(
    val characterId: String,
    val chatFile: String? = null,
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

/**
 * A persona as `bridge.js` reports it. [avatarId] is its ID (the picture's file name).
 */
@Serializable
data class PersonaDto(
    val avatarId: String,
    val name: String = "",
    val title: String = "",
    val description: String = "",
    val pictureUrl: String? = null,
    val isActive: Boolean = false,
    val isDefault: Boolean = false
)
