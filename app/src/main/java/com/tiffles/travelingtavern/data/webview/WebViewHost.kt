package com.tiffles.travelingtavern.data.webview

import android.content.Intent

/**
 * What the WebView needs from the screen it is currently attached to: things only an
 * Activity can start. Results come back through [WebViewHolder].
 */
interface WebViewHost {
    /** Opens the system file picker. Returns false when nothing can handle [intent]. */
    fun launchFileChooser(intent: Intent): Boolean

    /** Asks the user for these Android runtime permissions. */
    fun requestPermissions(permissions: Array<String>)
}
