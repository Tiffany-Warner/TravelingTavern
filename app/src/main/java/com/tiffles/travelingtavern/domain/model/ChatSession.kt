package com.tiffles.travelingtavern.domain.model

/**
 * A chat as SillyTavern identifies it: the character's avatar file and the chat file name
 * (without `.jsonl`). [characterId] is null when no single character is open.
 */
data class OpenChat(val characterId: String?, val chatFile: String?)

/**
 * A message the user tapped in the chat. [messageId] is its index in the chat and [sendDate]
 * SillyTavern's `send_date`, which together detect a message that changed meanwhile.
 */
data class TappedMessage(
    val messageId: Int,
    val sendDate: String,
    val senderName: String,
    val isUser: Boolean,
    val isLatest: Boolean,
    val isHidden: Boolean,
    val canRegenerate: Boolean,
    val canContinue: Boolean
)

/** SillyTavern's own message actions, in the order the sheet shows them. */
enum class MessageAction { Regenerate, Continue, Copy, Edit, Branch, Hide, Show, Delete }

/** How a chat command went. */
sealed interface ChatActionResult {
    /** Done; [text] is set by [MessageAction.Copy]. */
    data class Done(val text: String? = null) : ChatActionResult

    /** The message changed (for example deleted on the desktop) before the action ran. */
    data object Stale : ChatActionResult

    /** A reply is generating; only Copy runs meanwhile. */
    data object Busy : ChatActionResult

    data object Failed : ChatActionResult
}
