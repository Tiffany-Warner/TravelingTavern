package com.tiffles.travelingtavern.ui.protection.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.tiffles.travelingtavern.R
import com.tiffles.travelingtavern.ui.theme.TravelingTavernTheme

/** The one-time "Keep your devices from overwriting each other" dialog (A8). Dismissing it counts as Not now. */
@Composable
fun ExtensionPromptDialog(onInstall: () -> Unit, onNotNow: () -> Unit) {
    AlertDialog(
        onDismissRequest = onNotNow,
        title = { Text(stringResource(R.string.protection_prompt_title)) },
        text = { Text(stringResource(R.string.protection_prompt_text)) },
        confirmButton = { Button(onClick = onInstall) { Text(stringResource(R.string.protection_install)) } },
        dismissButton = { TextButton(onClick = onNotNow) { Text(stringResource(R.string.protection_not_now)) } }
    )
}

@Preview(showBackground = true)
@Composable
private fun ExtensionPromptDialogPreview() {
    TravelingTavernTheme { ExtensionPromptDialog(onInstall = {}, onNotNow = {}) }
}
