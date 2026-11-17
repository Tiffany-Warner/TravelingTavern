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
    private val holder: WebViewHolder
) {
    /** Asks the page to send a fresh `characters` message. */
    suspend fun requestCharacters() = run(BridgeScript.call("requestCharacters"))

    /** Opens a character in SillyTavern by its avatar file name. */
    suspend fun selectCharacter(id: String) = run(BridgeScript.call("selectCharacter", JsonPrimitive(id)))

    /** Sets the page's `tt-mode-*` body class (1.5 / 1.7); an empty [mode] clears it. */
    suspend fun setMode(mode: String) = run(BridgeScript.call("setMode", JsonPrimitive(mode)))

    private suspend fun run(script: String) = withContext(Dispatchers.Main) {
        holder.webView.evaluateJavascript(script, null)
    }
}
