package com.tiffles.travelingtavern.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tiffles.travelingtavern.data.webview.WebViewHost

/**
 * The app's one SillyTavern WebView, hosted once by the app shell behind every screen and
 * attached for as long as the Activity lives, so the JS bridge keeps running while the
 * native tabs cover it. It is never destroyed or reloaded here.
 *
 * Only while [revealed] (the Chat screen) does it show loading/error overlays, follow the
 * keyboard, take focus and appear to accessibility services. Screens drawn over it must be
 * opaque and consume touches.
 */
@Composable
fun SillyTavernWebView(
    revealed: Boolean,
    onOpenServerSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SillyTavernWebViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val sized = if (revealed) modifier.fillMaxSize().imePadding() else modifier.fillMaxSize()

    Box(modifier = sized) {
        AttachedWebView(viewModel = viewModel, revealed = revealed)
        if (revealed) {
            PageStateOverlay(
                pageState = uiState.pageState,
                onRetry = viewModel::retry,
                onOpenServerSettings = onOpenServerSettings
            )
        }
    }
}

/**
 * Back navigation inside the page. Call it from the screen that reveals the WebView: a
 * handler registered there takes precedence over the NavHost's own.
 */
@Composable
fun WebViewBackHandler(viewModel: SillyTavernWebViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(enabled = uiState.canGoBack) { viewModel.goBack() }
}

@Composable
private fun AttachedWebView(
    viewModel: SillyTavernWebViewModel,
    revealed: Boolean,
    modifier: Modifier = Modifier
) {
    val activity = LocalActivity.current ?: return
    val host = rememberWebViewHost(viewModel)

    DisposableEffect(activity, host) {
        viewModel.attach(activity, host)
        onDispose { viewModel.detach(activity, host) }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { viewModel.flushCookies() }

    AndroidView(
        factory = { viewModel.webView().apply { removeFromParent() } },
        // AndroidView doesn't clip; without this the WebView can paint over the status bar.
        modifier = modifier.fillMaxSize().clipToBounds(),
        update = { it.setRevealed(revealed) },
        // Leave the WebView alive and parentless so the next Activity can attach it.
        onRelease = { it.removeFromParent() }
    )
}

/** Wires the file picker and permission dialog to this screen's Activity. */
@Composable
private fun rememberWebViewHost(viewModel: SillyTavernWebViewModel): WebViewHost {
    val fileChooser = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result -> viewModel.onFileChooserResult(result.resultCode, result.data) }
    val permissionDialog = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted -> viewModel.onPermissionsResult(granted) }

    return remember(fileChooser, permissionDialog) {
        object : WebViewHost {
            override fun launchFileChooser(intent: Intent): Boolean = try {
                fileChooser.launch(intent)
                true
            } catch (_: ActivityNotFoundException) {
                false
            }

            override fun requestPermissions(permissions: Array<String>) = permissionDialog.launch(permissions)
        }
    }
}

/**
 * While covered, the page stays visible to the system (so its JavaScript keeps running at
 * full speed) but can't be focused or reached by accessibility services, and gives up the
 * keyboard if it had it.
 */
private fun WebView.setRevealed(revealed: Boolean) {
    importantForAccessibility = if (revealed) {
        View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
    } else {
        View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    }
    isFocusable = revealed
    isFocusableInTouchMode = revealed
    if (!revealed && hasFocus()) {
        clearFocus()
        val keyboard = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        keyboard?.hideSoftInputFromWindow(windowToken, 0)
    }
}

private fun View.removeFromParent() {
    (parent as? ViewGroup)?.removeView(this)
}
