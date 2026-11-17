package com.tiffles.travelingtavern.ui.chats

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tiffles.travelingtavern.ui.components.SillyTavernWebView

/**
 * Temporary: shows SillyTavern itself so the shared WebView can be verified (phase 1.3).
 * Phase 1.6 replaces this with the recent-chats list. No preview — the WebView host is
 * exempt; its loading and error states are previewed in PageStateOverlay.
 */
@Composable
fun ChatsScreen(onOpenServerSettings: () -> Unit, modifier: Modifier = Modifier) {
    SillyTavernWebView(onOpenServerSettings = onOpenServerSettings, modifier = modifier)
}
