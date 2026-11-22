package com.tiffles.travelingtavern.data.webview

import android.webkit.WebView

/** Clears the WebView's HTTP cache and reloads the page (A2): never cookies or web storage. Main thread. */
fun interface PageReloader {
    fun clearCacheAndReload()
}

/**
 * Drops the HTTP cache (`includeDiskFiles`) and reloads. Nothing else: no `CookieManager`,
 * `WebStorage` or history call, so the SillyTavern session, the lease's `sessionStorage` tab id and
 * SillyTavern's `localStorage` all survive (A2).
 */
internal fun WebView.clearCacheOnlyAndReload() {
    clearCache(true)
    reload()
}
