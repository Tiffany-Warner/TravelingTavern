package com.tiffles.travelingtavern.ui.lease

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tiffles.travelingtavern.ui.lease.components.LeaseScrim
import com.tiffles.travelingtavern.ui.lease.components.UnprotectedBanner
import com.tiffles.travelingtavern.ui.theme.TravelingTavernTheme

/**
 * Wraps the whole app shell (the `Scaffold`, tab bar included) with the session lease's
 * parked scrim (A3) and the "Not protected" banner (A2).
 */
@Composable
fun LeaseGuard(
    modifier: Modifier = Modifier,
    viewModel: LeaseGuardViewModel = hiltViewModel(),
    content: @Composable () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LeaseGuardContent(
        uiState = uiState,
        onUseHere = viewModel::useHere,
        onReload = viewModel::reload,
        onUpdateWebView = { context.openWebViewStorePage() },
        onContinueWithoutProtection = viewModel::continueWithoutProtection,
        modifier = modifier,
        content = content
    )
}

/**
 * Stateless: [content] below, the banner above it while unprotected, the scrim over all of
 * it while parked. When the scrim appears it hides the keyboard and clears text focus, and
 * [content] leaves the accessibility tree, so nothing behind it can be typed into or reached.
 */
@Composable
fun LeaseGuardContent(
    uiState: LeaseGuardUiState,
    onUseHere: () -> Unit,
    onReload: () -> Unit,
    onUpdateWebView: () -> Unit,
    onContinueWithoutProtection: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val scrim = uiState.scrim
    CoverEffect(covered = scrim != null)
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (scrim != null) Modifier.clearAndSetSemantics {} else Modifier)
        ) {
            val topInsets = WindowInsets.statusBars.union(WindowInsets.displayCutout.only(WindowInsetsSides.Top))
            if (uiState.unprotected) UnprotectedBanner(insets = topInsets)
            // The banner already pads for the status bar; the shell below mustn't count it again.
            val contentModifier = if (uiState.unprotected) Modifier.consumeWindowInsets(topInsets) else Modifier
            Box(modifier = contentModifier.weight(1f)) { content() }
        }
        if (scrim != null) {
            LeaseScrim(
                state = scrim,
                onUseHere = onUseHere,
                onReload = onReload,
                onUpdateWebView = onUpdateWebView,
                onContinueWithoutProtection = onContinueWithoutProtection
            )
        }
    }
}

/** Nothing can be typed behind the scrim: the keyboard goes away and text focus is cleared. */
@Composable
private fun CoverEffect(covered: Boolean) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    LaunchedEffect(covered) {
        if (!covered) return@LaunchedEffect
        keyboard?.hide()
        focusManager.clearFocus(force = true)
    }
}

/** The Play Store page for Android System WebView, in the store app or else the browser. */
private fun Context.openWebViewStorePage() {
    val targets = listOf(
        "market://details?id=$WEBVIEW_PACKAGE",
        "https://play.google.com/store/apps/details?id=$WEBVIEW_PACKAGE"
    )
    for (target in targets) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, target.toUri()))
            return
        } catch (_: ActivityNotFoundException) {
            // Try the next one.
        }
    }
    Log.w(TAG, "No app can open the WebView's store page")
}

private const val WEBVIEW_PACKAGE = "com.google.android.webview"
private const val TAG = "LeaseGuard"

@Preview(name = "Not protected · light", showBackground = true)
@Preview(name = "Not protected · dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LeaseGuardUnprotectedPreview() {
    TravelingTavernTheme {
        LeaseGuardContent(
            uiState = LeaseGuardUiState(unprotected = true),
            onUseHere = {}, onReload = {}, onUpdateWebView = {}, onContinueWithoutProtection = {}
        ) {
            Surface(modifier = Modifier.fillMaxSize()) { Text("App", style = MaterialTheme.typography.titleLarge) }
        }
    }
}

@Preview(name = "Parked over the app", showBackground = true)
@Composable
private fun LeaseGuardParkedPreview() {
    TravelingTavernTheme {
        LeaseGuardContent(
            uiState = LeaseGuardUiState(scrim = ScrimState.InUse),
            onUseHere = {}, onReload = {}, onUpdateWebView = {}, onContinueWithoutProtection = {}
        ) {
            Surface(modifier = Modifier.fillMaxSize()) { Text("App", style = MaterialTheme.typography.titleLarge) }
        }
    }
}
