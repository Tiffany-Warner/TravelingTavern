package com.tiffles.travelingtavern.domain.model

import java.net.URI
import java.net.URISyntaxException

/**
 * Normalizes what the user typed into `scheme://host[:port]` with no path or trailing
 * slash. A bare host or IP gets `http://`. Returns null when the input isn't a usable
 * http(s) address.
 */
fun normalizeServerUrl(input: String): String? {
    val trimmed = input.trim()
    if (trimmed.isEmpty()) return null
    val withScheme = if ("://" in trimmed) trimmed else "http://$trimmed"

    val uri = try {
        URI(withScheme)
    } catch (_: URISyntaxException) {
        return null
    }
    val scheme = uri.scheme?.lowercase()
    val host = uri.host ?: return null
    if (scheme != "http" && scheme != "https") return null

    val port = if (uri.port == -1) "" else ":${uri.port}"
    return "$scheme://${host.lowercase()}$port"
}
