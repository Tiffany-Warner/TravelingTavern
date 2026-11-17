package com.tiffles.travelingtavern.domain.model

/** Load state of the SillyTavern page inside the shared WebView. */
sealed interface PageState {
    data object Loading : PageState
    data object Ready : PageState
    data class Error(val error: PageError) : PageState
}

/** Why the main page failed to load. */
sealed interface PageError {
    /** The server didn't answer — usually Tailscale is off or SillyTavern isn't running. */
    data object Unreachable : PageError

    /** The server asked for a login and the saved one was missing or rejected. */
    data object AuthFailed : PageError

    data class HttpError(val code: Int) : PageError

    /** The phone's Android System WebView lacks the messaging feature the bridge needs. */
    data object WebViewOutdated : PageError
}
