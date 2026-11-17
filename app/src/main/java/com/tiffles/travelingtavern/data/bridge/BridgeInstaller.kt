package com.tiffles.travelingtavern.data.bridge

import android.content.Context
import android.util.Log
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Connects the WebView to [BridgeRepository]: registers the `TravelingTavern` message
 * listener for the saved server's origin only, and injects the bridge scripts after each
 * page load. Main thread only.
 */
@Singleton
class BridgeInstaller @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: BridgeRepository
) {
    private var registeredOrigin: String? = null

    private val scripts: String by lazy(LazyThreadSafetyMode.NONE) {
        SCRIPT_ASSETS.joinToString("\n") { path ->
            appContext.assets.open(path).bufferedReader().use { it.readText() }
        }
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

    /** Runs st-selectors.js then bridge.js in the current page; both are safe to run twice. */
    fun injectScripts(view: WebView) {
        if (!isSupported) return
        view.evaluateJavascript(scripts, null)
    }

    fun onPageStarted() = repository.onPageStarted()

    private companion object {
        const val TAG = "BridgeInstaller"
        const val LISTENER_NAME = "TravelingTavern"
        val SCRIPT_ASSETS = listOf("web/st-selectors.js", "web/bridge.js")
    }
}
