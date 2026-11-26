package com.tiffles.travelingtavern.data.bridge

/**
 * Starts and stops the reply foreground service (A6), which keeps the app's process, and so the
 * WebView's renderer, out of the cached (frozen) state while a reply finishes in the background.
 */
interface ReplyServiceController {

    /** The service can be used here: a `shortService`, Android 14 (API 34) and later. */
    val supported: Boolean

    /**
     * Starts the service. Call only with the app in the foreground (Android refuses most
     * foreground-service starts from the background). False when it couldn't be started.
     */
    fun start(): Boolean

    /** Stops the service, or makes it stop as soon as it has started. */
    fun stop()
}
