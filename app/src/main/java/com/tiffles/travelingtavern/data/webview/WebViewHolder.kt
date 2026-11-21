package com.tiffles.travelingtavern.data.webview

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.MutableContextWrapper
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import com.tiffles.travelingtavern.BuildConfig
import com.tiffles.travelingtavern.data.bridge.BridgeInstaller
import com.tiffles.travelingtavern.data.repository.ServerConfigRepositoryImpl
import com.tiffles.travelingtavern.data.settings.ServerCredentials
import com.tiffles.travelingtavern.di.ApplicationScope
import com.tiffles.travelingtavern.domain.model.PageError
import com.tiffles.travelingtavern.domain.repository.ServerPolicy
import com.tiffles.travelingtavern.domain.model.PageState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the app's single WebView. It is created once, on first use, and lives for the whole
 * process: screens attach and detach it, so switching screens or rotating never reloads
 * SillyTavern. Everything here must be called on the main thread.
 */
@Singleton
class WebViewHolder @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repository: ServerConfigRepositoryImpl,
    private val bridge: BridgeInstaller,
    private val serverPolicy: ServerPolicy,
    @ApplicationScope private val scope: CoroutineScope
) {
    // The WebView holds this wrapper, not an Activity, so no Activity can leak through it.
    private val contextWrapper = MutableContextWrapper(appContext)
    private val tracker = PageStateTracker()
    private val watchdog = LoadWatchdog(scope)

    private val _pageFinished = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private val _canGoBack = MutableStateFlow(false)

    private var serverBaseUrl: String? = null
    private var credentials: ServerCredentials? = null
    private var loadedBaseUrl: String? = null
    private var pendingLoad: View.OnLayoutChangeListener? = null

    private val chromeClient = TavernWebChromeClient(serverBaseUrl = { serverBaseUrl })
    private val webViewClient = TavernWebViewClient(
        tracker = tracker,
        serverBaseUrl = { serverBaseUrl },
        credentials = { credentials },
        onPageStarting = {
            bridge.onPageStarted()
            startWatchdog()
        },
        onServerAnswered = { watchdog.onServerAnswered() },
        onPageLoaded = { view ->
            bridge.injectScripts(view)
            _pageFinished.tryEmit(Unit)
        },
        onHistoryChanged = { _canGoBack.value = it }
    )

    val pageState: StateFlow<PageState> = tracker.state

    /** Fires each time the main page finishes loading, after the bridge scripts are injected. */
    val pageFinished: SharedFlow<Unit> = _pageFinished.asSharedFlow()

    val canGoBack: StateFlow<Boolean> = _canGoBack.asStateFlow()

    val webView: WebView by lazy(LazyThreadSafetyMode.NONE) {
        createWebView().also(::observeServerConfig)
    }

    /** Lends the WebView the Activity it is shown in (needed for dialogs, pickers, theming). */
    fun attach(activity: Activity, host: WebViewHost) {
        contextWrapper.baseContext = activity
        chromeClient.host = host
    }

    /** Guarded so a late detach from a destroyed Activity can't undo the new one's attach. */
    fun detach(activity: Activity, host: WebViewHost) {
        if (contextWrapper.baseContext === activity) contextWrapper.baseContext = appContext
        if (chromeClient.host === host) chromeClient.host = null
    }

    fun retry() {
        serverBaseUrl?.let { load(webView, it) }
    }

    fun goBack() {
        if (webView.canGoBack()) webView.goBack()
    }

    /** Call when the app goes to the background so the session survives a process kill. */
    fun flushCookies() {
        CookieManager.getInstance().flush()
    }

    fun onFileChooserResult(resultCode: Int, data: Intent?) =
        chromeClient.onFileChooserResult(resultCode, data)

    fun onPermissionsResult(granted: Map<String, Boolean>) =
        chromeClient.onPermissionsResult(granted)

    // SillyTavern is a JavaScript app; the WebView only ever shows the user's own server.
    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView {
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        CookieManager.getInstance().setAcceptCookie(true)
        return WebView(contextWrapper).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            // A blank WebView paints white — over the status bar too, in dark theme. Let the
            // app background show until SillyTavern paints its own.
            setBackgroundColor(Color.TRANSPARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = false
            webViewClient = this@WebViewHolder.webViewClient
            webChromeClient = chromeClient
        }
    }

    /**
     * Loads the saved server when the WebView is created and again only if its URL changes.
     * A settings change while the page is in an error state also retries, so fixing the
     * login on Connect recovers without a manual Retry. So does saving the *same* settings
     * again (which changes nothing in the store): with the page in an error state, Save on
     * Connect means "try this server now".
     */
    private fun observeServerConfig(view: WebView) {
        scope.launch {
            repository.observe().collect { config ->
                serverBaseUrl = config?.baseUrl
                credentials = repository.credentials()
                val baseUrl = config?.baseUrl ?: return@collect
                if (baseUrl != loadedBaseUrl || pageState.value is PageState.Error) load(view, baseUrl)
            }
        }
        scope.launch {
            repository.saves.collect {
                // Read the store, not serverBaseUrl: this can arrive before the observer above
                // has seen a changed address. load() records it, so the observer won't load twice.
                val baseUrl = repository.baseUrl() ?: return@collect
                if (pageState.value is PageState.Error) load(view, baseUrl)
            }
        }
    }

    private fun load(view: WebView, baseUrl: String) {
        loadedBaseUrl = baseUrl
        tracker.onPageStarted()
        if (!bridge.isSupported) {
            tracker.onMainFrameError(PageError.WebViewOutdated)
            return
        }
        if (!serverPolicy.allows(baseUrl)) {
            tracker.onMainFrameError(PageError.ServerNotAllowed)
            return
        }
        view.doOnNonZeroSize {
            // The listener only takes effect on the next navigation, so register it first.
            bridge.registerListener(view, origin = baseUrl)
            view.loadUrl(baseUrl)
            startWatchdog()
        }
    }

    /** Gives up on a load the server never answers, instead of waiting ~2 min for the WebView. */
    private fun startWatchdog() {
        watchdog.onLoadStarted {
            // An HTTP or network error already ended this load; nothing to give up on.
            if (pageState.value !is PageState.Loading) return@onLoadStarted
            webView.stopLoading()
            tracker.onMainFrameError(PageError.Unreachable)
        }
    }

    /**
     * Runs [action] once the view has a real size. A page that starts loading in a
     * zero-sized WebView keeps every CSS viewport unit (vh, dvh, svh) at 0 even after the
     * view grows, which collapses SillyTavern's `height: 100dvh` layout.
     */
    private fun View.doOnNonZeroSize(action: () -> Unit) {
        pendingLoad?.let(::removeOnLayoutChangeListener)
        pendingLoad = null
        if (width > 0 && height > 0) {
            action()
            return
        }
        val listener = object : View.OnLayoutChangeListener {
            override fun onLayoutChange(
                v: View, left: Int, top: Int, right: Int, bottom: Int,
                oldLeft: Int, oldTop: Int, oldRight: Int, oldBottom: Int
            ) {
                if (right - left <= 0 || bottom - top <= 0) return
                v.removeOnLayoutChangeListener(this)
                pendingLoad = null
                action()
            }
        }
        pendingLoad = listener
        addOnLayoutChangeListener(listener)
    }
}
