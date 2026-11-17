package com.tiffles.travelingtavern.data.bridge

import kotlinx.serialization.Serializable

/** A message posted by `bridge.js`. Data-layer only — never handed to the UI. */
sealed interface BridgeMessage {

    /** SillyTavern finished loading and the bridge is listening. */
    data object Ready : BridgeMessage

    @Serializable
    data class Characters(val items: List<CharacterDto>) : BridgeMessage

    /** [characterId] is null when no single character is open (none, or a group chat). */
    @Serializable
    data class ChatChanged(val characterId: String? = null) : BridgeMessage

    @Serializable
    data class MessageReceived(val characterId: String? = null, val messageId: Int) : BridgeMessage

    @Serializable
    data class Error(val message: String) : BridgeMessage

    /** A type this app version doesn't know (e.g. from a newer bridge script). Ignored. */
    data class Unknown(val type: String) : BridgeMessage
}

/** A character as `bridge.js` reports it. [lastChatAt] is epoch milliseconds. */
@Serializable
data class CharacterDto(
    val id: String,
    val name: String,
    val avatarUrl: String,
    val lastChatAt: Long? = null
)
