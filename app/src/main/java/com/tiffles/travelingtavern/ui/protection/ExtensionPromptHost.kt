package com.tiffles.travelingtavern.ui.protection

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tiffles.travelingtavern.ui.protection.components.ExtensionDialogs
import com.tiffles.travelingtavern.ui.protection.components.ExtensionPromptDialog
import com.tiffles.travelingtavern.ui.theme.TravelingTavernTheme

/** Draws the one-time extension dialog and its install flow over the app shell (A8). Nothing else. */
@Composable
fun ExtensionPromptHost(viewModel: ExtensionPromptViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ExtensionPromptContent(
        uiState = uiState,
        onInstall = viewModel::install,
        onNotNow = viewModel::notNow,
        onConfirm = viewModel::confirm,
        onUpdateInstead = viewModel::updateInstead,
        onDismissDialog = viewModel::dismissDialog
    )
}

/** Stateless: the prompt, or the install flow's dialog, or nothing. */
@Composable
fun ExtensionPromptContent(
    uiState: ExtensionPromptUiState,
    onInstall: () -> Unit,
    onNotNow: () -> Unit,
    onConfirm: () -> Unit,
    onUpdateInstead: () -> Unit,
    onDismissDialog: () -> Unit
) {
    val dialog = uiState.dialog
    when {
        uiState.prompt -> ExtensionPromptDialog(onInstall = onInstall, onNotNow = onNotNow)
        dialog != null -> ExtensionDialogs(dialog, onConfirm = onConfirm, onUpdateInstead = onUpdateInstead, onDismiss = onDismissDialog)
    }
}

@Preview(showBackground = true)
@Composable
private fun ExtensionPromptContentPreview() {
    TravelingTavernTheme {
        ExtensionPromptContent(ExtensionPromptUiState(prompt = true), {}, {}, {}, {}, {})
    }
}
