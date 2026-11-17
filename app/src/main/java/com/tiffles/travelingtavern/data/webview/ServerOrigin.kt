package com.tiffles.travelingtavern.data.webview

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * True when [url] points at the same scheme, host and port as the saved server [baseUrl].
 * Anything that isn't a valid http(s) URL is never the same server.
 */
fun isSameServer(url: String, baseUrl: String): Boolean {
    val target = url.toHttpUrlOrNull() ?: return false
    val server = baseUrl.toHttpUrlOrNull() ?: return false
    return target.scheme == server.scheme &&
        target.host.equals(server.host, ignoreCase = true) &&
        target.port == server.port
}
