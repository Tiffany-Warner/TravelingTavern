package com.tiffles.travelingtavern.domain.model

/**
 * The saved SillyTavern server. [baseUrl] is normalized (scheme + host + optional port,
 * no trailing slash). The password itself is never exposed — only [hasPassword].
 */
data class ServerConfig(
    val baseUrl: String,
    val username: String?,
    val hasPassword: Boolean
)
