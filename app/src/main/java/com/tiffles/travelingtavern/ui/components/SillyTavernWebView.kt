package com.tiffles.travelingtavern.ui.components

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.view.View
import android.view.ViewGroup
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
 * Shows the user's SillyTavern in the app's one shared WebView, with loading and error
 * overlays. Entering attaches the WebView and leaving detaches it — it is never destroyed
 * or reloaded by this composable.
 */
@Composable
fun SillyTavernWebView(
    onOpenServerSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SillyTavernWebViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    BackHandler(enabled = uiState.canGoBack) { viewModel.goBack() }

    Box(modifier = modifier.fillMaxSize().imePadding()) {
        AttachedWebView(viewModel = viewModel)
        PageStateOverlay(
            pageState = uiState.pageState,
            onRetry = viewModel::retry,
            onOpenServerSettings = onOpenServerSettings
        )
    }
}

@Composable
private fun AttachedWebView(viewModel: SillyTavernWebViewModel, modifier: Modifier = Modifier) {
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
        // Leave the WebView alive and parentless so the next screen can attach it.
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

private fun View.removeFromParent() {
    (parent as? ViewGroup)?.removeView(this)
}
