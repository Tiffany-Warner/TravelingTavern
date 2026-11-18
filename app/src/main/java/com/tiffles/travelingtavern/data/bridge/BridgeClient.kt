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
    private val pageMode: PageModeStore
) {
    /** Asks the page to send a fresh `characters` message. */
    suspend fun requestCharacters() = run(BridgeScript.call("requestCharacters"))

    /** Asks the page to send a fresh `recentChats` message. */
    suspend fun requestRecentChats() = run(BridgeScript.call("requestRecentChats"))

    /** Opens a character's chat in SillyTavern by its avatar file name. */
    suspend fun selectCharacter(id: String) = run(BridgeScript.call("selectCharacter", JsonPrimitive(id)))

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

    /** Sets the page's `tt-mode-*` body class (1.7); an empty [mode] clears it. */
    suspend fun setMode(mode: String) = run(BridgeScript.call("setMode", JsonPrimitive(mode)))

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
