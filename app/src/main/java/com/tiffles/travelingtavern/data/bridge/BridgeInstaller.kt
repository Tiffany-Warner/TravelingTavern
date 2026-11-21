package com.tiffles.travelingtavern.data.bridge

import android.content.Context
import android.util.Log
import android.webkit.WebView
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.tiffles.travelingtavern.data.lifecycle.AppForegroundTracker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.JsonPrimitive
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Connects the WebView to [BridgeRepository]: registers the `TravelingTavern` message
 * listener and the bundled session lease (at document start) for the saved server's origin
 * only, and injects the bridge scripts and the clean theme after each page load. Main thread only.
 */
@Singleton
class BridgeInstaller @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: BridgeRepository,
    private val pageMode: PageModeStore,
    private val pageTheme: PageThemeStore,
    private val foreground: AppForegroundTracker
) {
    private var registeredOrigin: String? = null
    private var leaseOrigin: String? = null
    private var leaseScript: ScriptHandler? = null

    /** Bridge scripts, then the clean-theme stylesheet (inert until `tt-clean` is set on the body). */
    private val scripts: String by lazy(LazyThreadSafetyMode.NONE) {
        val bridge = SCRIPT_ASSETS.joinToString("\n") { readAsset(it) }
        bridge + "\n" + BridgeScript.installStyle(CLEAN_THEME_STYLE_ID, readAsset(CLEAN_THEME_ASSET))
    }

    val isSupported: Boolean
        get() = WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)

    /** Without document-start scripts the lease can't run before SillyTavern's first request (A2). */
    val isDocumentStartSupported: Boolean
        get() = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)

    /**
     * Allows messages from [origin] (e.g. `http://192.0.2.1:8000`) and nowhere else. Takes
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
     * Injects the bundled `session-lease.js` (APK assets, never the HTTP cache) at document
     * start of every page from [origin], replacing the rule for an earlier origin. Like the
     * listener, it applies from the next navigation, so call it before loading the server.
     * The script itself skips frames and SillyTavern's login page (E11).
     */
    fun addLeaseScript(view: WebView, origin: String) {
        // Checked inline, not through isDocumentStartSupported, so lint's RequiresFeature sees it.
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT) || origin == leaseOrigin) return
        leaseScript?.remove()
        leaseScript = null
        leaseOrigin = null
        try {
            leaseScript = WebViewCompat.addDocumentStartJavaScript(view, readAsset(LEASE_ASSET), setOf(origin))
            leaseOrigin = origin
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Server origin was rejected as a document-start rule", e)
        }
    }

    /**
     * Runs st-selectors.js and bridge.js, installs the clean theme, and re-applies what the
     * page loses on a reload: clean mode, the app's foreground state and its theme colors.
     * Safe to run twice: the scripts guard themselves and the style tag is replaced.
     */
    fun injectScripts(view: WebView) {
        if (!isSupported) return
        val state = listOfNotNull(
            BridgeScript.call("setClean", JsonPrimitive(pageMode.cleanMode)),
            BridgeScript.call("setAppState", JsonPrimitive(foreground.isForeground.value)),
            pageTheme.theme?.let(BridgeScript::setTheme)
        )
        view.evaluateJavascript((listOf(scripts) + state).joinToString("\n"), null)
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
        const val LEASE_ASSET = "web/session-lease.js"
    }
}
