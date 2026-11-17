package com.tiffles.travelingtavern.data.webview

import com.tiffles.travelingtavern.domain.model.PageError
import com.tiffles.travelingtavern.domain.model.PageState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Turns WebViewClient callbacks into a [PageState]. WebView calls `onPageFinished` even
 * after a failed load, so an error has to survive until the next load starts.
 */
class PageStateTracker {

    private val _state = MutableStateFlow<PageState>(PageState.Loading)
    val state: StateFlow<PageState> = _state.asStateFlow()

    private var failed = false

    fun onPageStarted() {
        failed = false
        _state.value = PageState.Loading
    }

    /** The first error of a load wins (a cancelled login is followed by a plain 401). */
    fun onMainFrameError(error: PageError) {
        if (failed) return
        failed = true
        _state.value = PageState.Error(error)
    }

    fun onPageFinished() {
        if (!failed) _state.value = PageState.Ready
    }
}
