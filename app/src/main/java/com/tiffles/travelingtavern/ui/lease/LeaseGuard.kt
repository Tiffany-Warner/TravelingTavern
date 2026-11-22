package com.tiffles.travelingtavern.ui.lease

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.util.Log
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.net.toUri
import androidx.core.view.WindowCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tiffles.travelingtavern.R
import com.tiffles.travelingtavern.ui.lease.components.LeaseScrim
import com.tiffles.travelingtavern.ui.lease.components.UnprotectedBanner
import com.tiffles.travelingtavern.ui.theme.TravelingTavernTheme

/**
 * Wraps the whole app shell (the `Scaffold`, tab bar included) with the session lease's
 * parked scrim (A3) and the "Not protected" banner (A2). [content] gets the lease's snackbar
 * host for its `Scaffold` (the pre-check's "still loading" message, A4).
 */
@Composable
fun LeaseGuard(
    modifier: Modifier = Modifier,
    viewModel: LeaseGuardViewModel = hiltViewModel(),
    content: @Composable (snackbarHost: @Composable () -> Unit) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.stillLoading.collect {
            snackbarHostState.showSnackbar(context.getString(R.string.lease_still_loading))
        }
    }
    LeaseGuardContent(
        uiState = uiState,
        onUseHere = viewModel::useHere,
        onReload = viewModel::reload,
        onUpdateWebView = { context.openWebViewStorePage() },
        onContinueWithoutProtection = viewModel::continueWithoutProtection,
        modifier = modifier,
        snackbarHostState = snackbarHostState,
        content = content
    )
}

/**
 * Stateless: [content] below, the banner above it while unprotected, a loading bar over its top
 * while a user-started command waits (A4, D25), the scrim over all of it while parked, in a
 * window of its own so it also covers open sheets and dialogs (D42). When the scrim appears it
 * hides the keyboard and clears text focus, and [content] leaves the accessibility tree, so
 * nothing behind it can be typed into or reached.
 */
@Composable
fun LeaseGuardContent(
    uiState: LeaseGuardUiState,
    onUseHere: () -> Unit,
    onReload: () -> Unit,
    onUpdateWebView: () -> Unit,
    onContinueWithoutProtection: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    content: @Composable (snackbarHost: @Composable () -> Unit) -> Unit
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
            Box(modifier = contentModifier.weight(1f)) {
                content { SnackbarHost(hostState = snackbarHostState) }
                if (uiState.waiting) {
                    // Below the status bar (the shell is edge to edge); right under the banner when it shows.
                    LinearProgressIndicator(
                        modifier = Modifier.align(Alignment.TopCenter).windowInsetsPadding(topInsets).fillMaxWidth()
                    )
                }
            }
        }
        if (scrim != null) {
            // Swallows touches in the app's own window (also before the scrim's window is shown).
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {}
            ScrimWindow {
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
}

/**
 * The scrim in a full-screen window of its own (D42). Sheets, dialogs and menus are windows above
 * the app's, so a scrim drawn in the app's window stayed under one that was open when the page
 * parked (found on the device: the New lorebook sheet stayed usable, keyboard up). A window added
 * now stacks above them and takes the focus, so the keyboard goes too. Back still navigates under
 * the scrim (D16): it is handed to the app's own back handling.
 */
@Composable
private fun ScrimWindow(content: @Composable () -> Unit) {
    val appBack = LocalOnBackPressedDispatcherOwner.current?.onBackPressedDispatcher
    Dialog(
        onDismissRequest = { appBack?.onBackPressed() },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        CoverEffect(covered = true)
        SystemBarIconsEffect()
        content()
    }
}

/** The scrim's window doesn't inherit the app's light / dark status and navigation bar icons. */
@Composable
private fun SystemBarIconsEffect() {
    val view = LocalView.current
    val lightBars = !isSystemInDarkTheme()
    LaunchedEffect(view, lightBars) {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@LaunchedEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = lightBars
            isAppearanceLightNavigationBars = lightBars
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
