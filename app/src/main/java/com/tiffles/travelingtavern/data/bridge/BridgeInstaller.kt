package com.tiffles.travelingtavern.data.bridge

import android.content.Context
import android.util.Log
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.tiffles.travelingtavern.domain.repository.SpeechInputRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Connects the WebView to [BridgeRepository]: registers the `TravelingTavern` message
 * listener for the saved server's origin only, and injects the bridge scripts and the clean
 * theme after each page load. Main thread only.
 */
@Singleton
class BridgeInstaller @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: BridgeRepository,
    private val pageMode: PageModeStore,
    private val speech: SpeechInputRepository
) {
    private var registeredOrigin: String? = null

    /** Bridge scripts, then the clean-theme stylesheet (inert until `tt-clean` is set on the body). */
    private val scripts: String by lazy(LazyThreadSafetyMode.NONE) {
        val bridge = SCRIPT_ASSETS.joinToString("\n") { readAsset(it) }
        bridge + "\n" + BridgeScript.installStyle(CLEAN_THEME_STYLE_ID, readAsset(CLEAN_THEME_ASSET))
    }

    val isSupported: Boolean
        get() = WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)

    /**
     * Allows messages from [origin] (e.g. `http://100.64.0.2:8000`) and nowhere else. Takes
     * effect on the next page load, so call it before loading the server.
     */
    fun registerListener(view: WebView, origin: String) {
        if (!isSupported || origin == registeredOrigin) return
        if (registeredOrigin != null) WebViewCompat.removeWebMessageListener(view, LISTENER_NAME)
        try {
            WebViewCompat.addWebMessageListener(view, LISTENER_NAME, setOf(origin)) { _, message, _, isMainFrame, _ ->
                // Frames inside the page (e.g. extension iframes) never speak for SillyTavern.
                val data = message.data
                if (isMainFrame && data != null) repository.onRawMessage(data)
            }
            registeredOrigin = origin
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Server origin was rejected as a listener rule", e)
            registeredOrigin = null
        }
    }

    /**
     * Runs st-selectors.js and bridge.js, installs the clean theme, applies the current
     * clean-mode setting and adds the mic button if voice input is available. Safe to run
     * twice: the scripts guard themselves, the style tag is replaced and the mic button is
     * added only once.
     */
    fun injectScripts(view: WebView) {
        if (!isSupported) return
        val applyMode = BridgeScript.call("setClean", JsonPrimitive(pageMode.cleanMode))
        val applyVoice = BridgeScript.call("setVoiceInputAvailable", JsonPrimitive(speech.isAvailable()))
        view.evaluateJavascript(scripts + "\n" + applyMode + "\n" + applyVoice, null)
    }

    fun onPageStarted() = repository.onPageStarted()

    private fun readAsset(path: String): String =
        appContext.assets.open(path).bufferedReader().use { it.readText() }

    private companion object {
        const val TAG = "BridgeInstaller"
        const val LISTENER_NAME = "TravelingTavern"
        val SCRIPT_ASSETS = listOf("web/st-selectors.js", "web/bridge.js")
        const val CLEAN_THEME_ASSET = "web/clean-theme.css"
        const val CLEAN_THEME_STYLE_ID = "tt-clean-theme"
    }
}
