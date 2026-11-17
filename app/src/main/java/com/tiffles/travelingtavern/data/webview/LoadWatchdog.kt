package com.tiffles.travelingtavern.data.webview

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Gives up on a page load when the server hasn't answered within [timeoutMs]. A connection
 * that is silently dropped (e.g. Tailscale off) otherwise waits for the WebView's own
 * timeout, about two minutes.
 */
class LoadWatchdog(
    private val scope: CoroutineScope,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS
) {
    private var job: Job? = null

    /** A main-page load started; [onTimeout] runs unless [onServerAnswered] or [cancel] comes first. */
    fun onLoadStarted(onTimeout: () -> Unit) {
        job?.cancel()
        job = scope.launch {
            delay(timeoutMs)
            job = null
            onTimeout()
        }
    }

    /** The server responded, so the page is reachable — however long the rest of it takes. */
    fun onServerAnswered() = cancel()

    fun cancel() {
        job?.cancel()
        job = null
    }

    companion object {
        /** A little longer than the Connect screen's 10 s test, since a page load is more than one request. */
        const val DEFAULT_TIMEOUT_MS = 15_000L
    }
}
