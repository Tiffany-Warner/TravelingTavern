package com.tiffles.travelingtavern.ui.components

import android.app.Activity
import android.content.Intent
import android.webkit.WebView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tiffles.travelingtavern.data.webview.WebViewHolder
import com.tiffles.travelingtavern.data.webview.WebViewHost
import com.tiffles.travelingtavern.domain.model.PageState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class SillyTavernWebUiState(
    val pageState: PageState = PageState.Loading,
    val canGoBack: Boolean = false
)

/**
 * Gives [SillyTavernWebView] access to the app's one WebView. The WebView itself lives in
 * the `@Singleton` [WebViewHolder], so it outlives this ViewModel and every screen.
 */
@HiltViewModel
class SillyTavernWebViewModel @Inject constructor(
    private val holder: WebViewHolder
) : ViewModel() {

    val uiState: StateFlow<SillyTavernWebUiState> =
        combine(holder.pageState, holder.canGoBack, ::SillyTavernWebUiState).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = SillyTavernWebUiState(holder.pageState.value, holder.canGoBack.value)
        )

    /** The shared WebView. Safe to hold: its context is swapped out on [detach]. */
    fun webView(): WebView = holder.webView

    fun attach(activity: Activity, host: WebViewHost) = holder.attach(activity, host)

    fun detach(activity: Activity, host: WebViewHost) = holder.detach(activity, host)

    fun retry() = holder.retry()

    fun goBack() = holder.goBack()

    fun flushCookies() = holder.flushCookies()

    fun onFileChooserResult(resultCode: Int, data: Intent?) = holder.onFileChooserResult(resultCode, data)

    fun onPermissionsResult(granted: Map<String, Boolean>) = holder.onPermissionsResult(granted)

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
