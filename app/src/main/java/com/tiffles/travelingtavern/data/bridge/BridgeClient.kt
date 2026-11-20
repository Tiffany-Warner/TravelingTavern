package com.tiffles.travelingtavern.data.bridge

import com.tiffles.travelingtavern.data.webview.WebViewHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The only way Kotlin calls into the SillyTavern page. Calls made before the bridge is
 * loaded do nothing; watch [BridgeRepository.isReady] when timing matters.
 */
@Singleton
class BridgeClient @Inject constructor(
    private val holder: WebViewHolder,
    private val pageMode: PageModeStore,
    private val pageTheme: PageThemeStore
) {
    /** Asks for a fresh `characters` message; [fromServer] re-reads the list from the server first. */
    suspend fun requestCharacters(fromServer: Boolean) =
        run(BridgeScript.call("requestCharacters", JsonPrimitive(fromServer)))

    /** Asks the page to send a fresh `recentChats` message. */
    suspend fun requestRecentChats() = run(BridgeScript.call("requestRecentChats"))

    /**
     * Opens character [id]'s chat (avatar file name): [chatFile] (without `.jsonl`) if it
     * exists, else the current one; re-read first when [reload] and it was already open.
     * Answered by `chatOpened` tagged [requestId].
     */
    suspend fun selectCharacter(requestId: String, id: String, chatFile: String?, reload: Boolean) =
        run(
            BridgeScript.call(
                "selectCharacter",
                JsonPrimitive(requestId),
                JsonPrimitive(id),
                chatFile?.let(::JsonPrimitive) ?: JsonNull,
                JsonPrimitive(reload)
            )
        )

    /** Tells the page whether the app is in the foreground (kept in `appState`). */
    suspend fun setAppState(foreground: Boolean) = run(BridgeScript.call("setAppState", JsonPrimitive(foreground)))

    /** Re-reads the open chat from the server once no generation is running. */
    suspend fun reloadChat() = run(BridgeScript.call("reloadChat"))

    /** Runs [action] on a message, answered by `chatResult` tagged [requestId]. */
    suspend fun messageAction(requestId: String, messageId: Int, sendDate: String, action: String) =
        run(
            BridgeScript.call(
                "messageAction",
                JsonPrimitive(requestId),
                JsonPrimitive(messageId),
                JsonPrimitive(sendDate),
                JsonPrimitive(action)
            )
        )

    /** Sends [text] through SillyTavern's own send. */
    suspend fun sendMessage(text: String) = run(BridgeScript.call("sendMessage", JsonPrimitive(text)))

    /** SillyTavern's impersonate; its text arrives as `impersonateReady`. */
    suspend fun impersonate() = run(BridgeScript.call("impersonate"))

    suspend fun stopGeneration() = run(BridgeScript.call("stopGeneration"))

    /** Scrolls message [messageId] above the actions sheet, which covers the bottom [coveredPx] of the page. */
    suspend fun revealMessage(messageId: Int, coveredPx: Int) =
        run(BridgeScript.call("revealMessage", JsonPrimitive(messageId), JsonPrimitive(coveredPx)))

    /** The actions sheet closed: takes away the room [revealMessage] added. */
    suspend fun endReveal() = run(BridgeScript.call("endReveal"))

    /** Start new chat, or Restart chat when [deleteCurrent]; answered by `chatResult`. */
    suspend fun newChat(requestId: String, deleteCurrent: Boolean) =
        run(BridgeScript.call("newChat", JsonPrimitive(requestId), JsonPrimitive(deleteCurrent)))

    /** Deletes character [id]'s chat [chatFile] (without `.jsonl`); answered by `chatResult`. */
    suspend fun deleteChat(requestId: String, id: String, chatFile: String) =
        run(BridgeScript.call("deleteChat", JsonPrimitive(requestId), JsonPrimitive(id), JsonPrimitive(chatFile)))

    /** The app's colors for the clean layout. Remembered, so it is re-applied after every reload. */
    suspend fun setPageTheme(theme: PageTheme) = withContext(Dispatchers.Main) {
        pageTheme.theme = theme
        holder.webView.evaluateJavascript(BridgeScript.setTheme(theme), null)
    }

    /** Asks for a fresh `lorebooks` message; [fromServer] re-reads everything from the server first. */
    suspend fun requestLorebooks(fromServer: Boolean) =
        run(BridgeScript.call("requestLorebooks", JsonPrimitive(fromServer)))

    /** Asks for `lorebookEntries` of [name] (or a failed `lorebookResult`), tagged [requestId]. */
    suspend fun requestLorebook(requestId: String, name: String) =
        run(BridgeScript.call("requestLorebook", JsonPrimitive(requestId), JsonPrimitive(name)))

    suspend fun createLorebook(requestId: String, name: String) =
        run(BridgeScript.call("createLorebook", JsonPrimitive(requestId), JsonPrimitive(name)))

    suspend fun deleteLorebook(requestId: String, name: String) =
        run(BridgeScript.call("deleteLorebook", JsonPrimitive(requestId), JsonPrimitive(name)))

    suspend fun setLorebookGlobal(requestId: String, name: String, enabled: Boolean) =
        run(BridgeScript.call("setLorebookGlobal", JsonPrimitive(requestId), JsonPrimitive(name), JsonPrimitive(enabled)))

    /** Saves [entry] (uid null = new) into lorebook [name]. */
    suspend fun saveLorebookEntry(requestId: String, name: String, entry: LorebookEntryInput) =
        run(BridgeScript.call("saveLorebookEntry", JsonPrimitive(requestId), JsonPrimitive(name), entry.toJson()))

    suspend fun deleteLorebookEntry(requestId: String, name: String, uid: Int) =
        run(BridgeScript.call("deleteLorebookEntry", JsonPrimitive(requestId), JsonPrimitive(name), JsonPrimitive(uid)))

    suspend fun countTokens(requestId: String, text: String) =
        run(BridgeScript.call("countTokens", JsonPrimitive(requestId), JsonPrimitive(text)))

    /**
     * Turns the clean theme on or off (Advanced, 1.8). Remembered, so it is re-applied after
     * every page reload.
     */
    suspend fun setCleanMode(enabled: Boolean) = withContext(Dispatchers.Main) {
        pageMode.cleanMode = enabled
        holder.webView.evaluateJavascript(BridgeScript.call("setClean", JsonPrimitive(enabled)), null)
    }

    private suspend fun run(script: String) = withContext(Dispatchers.Main) {
        holder.webView.evaluateJavascript(script, null)
    }
}

/** The fields of an entry the app writes. [uid] null = a new entry. */
data class LorebookEntryInput(
    val uid: Int?,
    val title: String,
    val keys: List<String>,
    val content: String,
    val enabled: Boolean
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("uid", uid?.let(::JsonPrimitive) ?: JsonNull)
        put("title", JsonPrimitive(title))
        put("keys", JsonArray(keys.map(::JsonPrimitive)))
        put("content", JsonPrimitive(content))
        put("enabled", JsonPrimitive(enabled))
    }
}
