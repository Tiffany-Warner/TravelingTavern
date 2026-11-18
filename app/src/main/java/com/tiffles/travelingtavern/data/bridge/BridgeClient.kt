package com.tiffles.travelingtavern.data.bridge

import com.tiffles.travelingtavern.data.webview.WebViewHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonPrimitive
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

    /** Opens a character's chat in SillyTavern by its avatar file name. */
    suspend fun selectCharacter(id: String) = run(BridgeScript.call("selectCharacter", JsonPrimitive(id)))

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
