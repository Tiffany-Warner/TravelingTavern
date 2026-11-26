package com.tiffles.travelingtavern.domain.model

/**
 * The Chat screen's "Not connected to a model" banner (A7): shown after the page's `leaseNotice`
 * `notConnected` (a takeover reload whose own connect attempt failed), until SillyTavern reports a
 * connection, the page stops holding the lease, or a new page load starts.
 *
 * [stillNotConnected]: Connect was tried from the banner and SillyTavern still isn't connected, so
 * the banner points to SillyTavern's API Connections instead. [connecting]: a Connect is running.
 */
data class ModelBanner(
    val stillNotConnected: Boolean = false,
    val connecting: Boolean = false
)
