package com.tiffles.travelingtavern.data.webview

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.webkit.HttpAuthHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.tiffles.travelingtavern.data.settings.ServerCredentials
import com.tiffles.travelingtavern.domain.model.PageError
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Reports main-page load state to [tracker], answers basic-auth challenges with the saved
 * login, and keeps the WebView on the saved server (other hosts open in the browser).
 */
class TavernWebViewClient(
    private val tracker: PageStateTracker,
    private val serverBaseUrl: () -> String?,
    private val credentials: () -> ServerCredentials?,
    private val onPageStarting: () -> Unit,
    private val onServerAnswered: () -> Unit,
    private val onPageLoaded: (WebView) -> Unit,
    private val onHistoryChanged: (canGoBack: Boolean) -> Unit
) : WebViewClient() {

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        tracker.onPageStarted()
        onPageStarting()
    }

    override fun onPageFinished(view: WebView, url: String) {
        tracker.onPageFinished()
        onPageLoaded(view)
    }

    /** The server's response has started rendering, so it is reachable. */
    override fun onPageCommitVisible(view: WebView, url: String) {
        onServerAnswered()
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
        onHistoryChanged(view.canGoBack())
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (request.isForMainFrame) tracker.onMainFrameError(PageError.Unreachable)
    }

    override fun onReceivedHttpError(
        view: WebView,
        request: WebResourceRequest,
        errorResponse: WebResourceResponse
    ) {
        if (!request.isForMainFrame) return
        val error = when (val code = errorResponse.statusCode) {
            HTTP_UNAUTHORIZED -> PageError.AuthFailed
            else -> PageError.HttpError(code)
        }
        tracker.onMainFrameError(error)
    }

    override fun onReceivedHttpAuthRequest(view: WebView, handler: HttpAuthHandler, host: String, realm: String) {
        val saved = credentials()
        val isServerHost = serverBaseUrl()?.toHttpUrlOrNull()?.host.equals(host, ignoreCase = true)
        // useHttpAuthUsernamePassword() turns false once a login was already tried for this
        // request, i.e. the server rejected the saved one. Never retry, never send it elsewhere.
        if (saved == null || !isServerHost || !handler.useHttpAuthUsernamePassword()) {
            handler.cancel()
            if (isServerHost) tracker.onMainFrameError(PageError.AuthFailed)
            return
        }
        handler.proceed(saved.username, saved.password)
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val baseUrl = serverBaseUrl() ?: return false
        val isExternalPage = request.isForMainFrame &&
            request.url.isWebLink() &&
            !isSameServer(url = request.url.toString(), baseUrl = baseUrl)
        if (isExternalPage) openInBrowser(view.context, request.url)
        return isExternalPage
    }

    private fun Uri.isWebLink(): Boolean = scheme == "http" || scheme == "https"

    private fun openInBrowser(context: Context, uri: Uri) {
        // NEW_TASK because the WebView's context is the application while it is detached.
        val intent = Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No app available to open an external link", e)
        }
    }

    private companion object {
        const val TAG = "TavernWebViewClient"
        const val HTTP_UNAUTHORIZED = 401
    }
}
