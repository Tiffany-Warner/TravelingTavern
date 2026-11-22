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
 *
 * Every command goes through the lease pre-check ([LeaseGate], A4) and returns whether it was
 * sent: `false` means it was refused and never reached the page. Exempt, because they touch
 * nothing but the page's DOM or CSS: [revealMessage], [endReveal], [setCleanMode] and
 * [setPageTheme]. [useHere] and [onForeground] are the lease's own hooks and are never gated,
 * nor are [interruptReply] and [watchChatSave] (A6).
 *
 * Commands are [CommandOrigin.User] unless noted: [setAppState], [reloadChat], [requestPersonas]
 * and [countTokens] are always started by the app, and [requestRecentChats] takes its origin
 * (D25). An app-started command is checked the same way, but never shows the loading bar or the
 * "still loading" message.
 */
@Singleton
class BridgeClient @Inject constructor(
    private val holder: WebViewHolder,
    private val pageMode: PageModeStore,
    private val pageTheme: PageThemeStore,
    private val gate: LeaseGate
) {
    /** Asks for a fresh `characters` message; [fromServer] re-reads the list from the server first. */
    suspend fun requestCharacters(fromServer: Boolean) =
        run(BridgeScript.call("requestCharacters", JsonPrimitive(fromServer)))

    /** Asks the page to send a fresh `recentChats` message. */
    suspend fun requestRecentChats(origin: CommandOrigin) = run(BridgeScript.call("requestRecentChats"), origin)

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

    /**
     * Tells the page whether the app is in the foreground (kept in `appState`). Checked: on
     * return it runs the persona sync, which reads and can save. App-started.
     */
    suspend fun setAppState(foreground: Boolean) =
        run(BridgeScript.call("setAppState", JsonPrimitive(foreground)), CommandOrigin.App)

    /** Re-reads the open chat from the server once no generation is running. App-started. */
    suspend fun reloadChat() = run(BridgeScript.call("reloadChat"), CommandOrigin.App)

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

    /**
     * The interrupt's Stop (A6), with the app in the background: arms the page's save watch, then
     * stops the reply; `chatSaved` follows once SillyTavern's save has completed. Not gated (D30):
     * the interrupt must fit its time budget, and the chat save SillyTavern sends after the stop is
     * still checked by the page's own lease (E6).
     */
    suspend fun interruptReply() = runUnchecked(BridgeScript.call("interruptReply"))

    /**
     * Arms the page's save watch without stopping anything (a reply that finished in the
     * background, A6): `chatSaved` follows once SillyTavern's save has completed. Not gated: it
     * only adds an observer in the page, no request.
     */
    suspend fun watchChatSave() = runUnchecked(BridgeScript.call("watchChatSave"))

    /**
     * Scrolls message [messageId] above the actions sheet, which covers the bottom [coveredPx] of
     * the page. Exempt from the lease check: a scroll and a body class.
     */
    suspend fun revealMessage(messageId: Int, coveredPx: Int) =
        runUnchecked(BridgeScript.call("revealMessage", JsonPrimitive(messageId), JsonPrimitive(coveredPx)))

    /** The actions sheet closed: takes away the room [revealMessage] added. Exempt, like [revealMessage]. */
    suspend fun endReveal() = runUnchecked(BridgeScript.call("endReveal"))

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

    /**
     * Asks for a fresh `personas` message; [refreshPictures] makes every picture load again.
     * App-started (Personas' automatic refresh on opening).
     */
    suspend fun requestPersonas(refreshPictures: Boolean) =
        run(BridgeScript.call("requestPersonas", JsonPrimitive(refreshPictures)), CommandOrigin.App)

    /** Switches to persona [avatarId]; [lockToChat] also locks it to the open chat. */
    suspend fun selectPersona(requestId: String, avatarId: String, lockToChat: Boolean) =
        run(BridgeScript.call("selectPersona", JsonPrimitive(requestId), JsonPrimitive(avatarId), JsonPrimitive(lockToChat)))

    /** Asks for a `personaResult` carrying [avatarId]'s full fields. */
    suspend fun requestPersona(requestId: String, avatarId: String) =
        run(BridgeScript.call("requestPersona", JsonPrimitive(requestId), JsonPrimitive(avatarId)))

    suspend fun createPersona(requestId: String, fields: PersonaInput) =
        run(BridgeScript.call("createPersona", JsonPrimitive(requestId), fields.toJson()))

    /** Saves only the non-null [fields] of [avatarId]. */
    suspend fun savePersona(requestId: String, avatarId: String, fields: PersonaInput) =
        run(BridgeScript.call("savePersona", JsonPrimitive(requestId), JsonPrimitive(avatarId), fields.toJson()))

    suspend fun deletePersona(requestId: String, avatarId: String) =
        run(BridgeScript.call("deletePersona", JsonPrimitive(requestId), JsonPrimitive(avatarId)))

    suspend fun setDefaultPersona(requestId: String, avatarId: String, isDefault: Boolean) =
        run(BridgeScript.call("setDefaultPersona", JsonPrimitive(requestId), JsonPrimitive(avatarId), JsonPrimitive(isDefault)))

    /** App-started: counted while the user types. */
    suspend fun countTokens(requestId: String, text: String) =
        run(BridgeScript.call("countTokens", JsonPrimitive(requestId), JsonPrimitive(text)), CommandOrigin.App)

    /**
     * Turns the clean theme on or off (Advanced, 1.8). Remembered, so it is re-applied after
     * every page reload.
     */
    suspend fun setCleanMode(enabled: Boolean) = withContext(Dispatchers.Main) {
        pageMode.cleanMode = enabled
        holder.webView.evaluateJavascript(BridgeScript.call("setClean", JsonPrimitive(enabled)), null)
    }

    /**
     * Use here (A3): the page claims the lease and reloads. Never gated by the lease check:
     * it is how a parked page stops being parked.
     */
    suspend fun useHere() = withContext(Dispatchers.Main) {
        holder.webView.evaluateJavascript(BridgeScript.leaseCall("useHere"), null)
    }

    /**
     * The app is back in the foreground, or the pre-check found the lease changed: the page
     * confirms its pair and parks if it lost the lease (E11). Never gated.
     */
    suspend fun onForeground() = withContext(Dispatchers.Main) {
        holder.webView.evaluateJavascript(BridgeScript.leaseCall("onForeground"), null)
    }

    /** Runs [script] if the lease pre-check allows it; true when it was sent. */
    private suspend fun run(script: String, origin: CommandOrigin = CommandOrigin.User): Boolean =
        withContext(Dispatchers.Main) {
            when (gate.check(origin)) {
                LeaseCheck.Allowed -> {
                    holder.webView.evaluateJavascript(script, null)
                    true
                }
                LeaseCheck.Refused -> false
                LeaseCheck.RefusedPageMustCheck -> {
                    holder.webView.evaluateJavascript(BridgeScript.leaseCall("onForeground"), null)
                    false
                }
            }
        }

    /** DOM/CSS-only commands (A4 exempt): no lease check. Always true. */
    private suspend fun runUnchecked(script: String): Boolean = withContext(Dispatchers.Main) {
        holder.webView.evaluateJavascript(script, null)
        true
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

/**
 * Persona fields the app writes. Null fields are left out, so SillyTavern keeps them as they
 * are. [picture] is an image as a `data:` URL.
 */
data class PersonaInput(
    val name: String? = null,
    val title: String? = null,
    val description: String? = null,
    val picture: String? = null
) {
    fun toJson(): JsonObject = buildJsonObject {
        name?.let { put("name", JsonPrimitive(it)) }
        title?.let { put("title", JsonPrimitive(it)) }
        description?.let { put("description", JsonPrimitive(it)) }
        picture?.let { put("picture", JsonPrimitive(it)) }
    }
}
