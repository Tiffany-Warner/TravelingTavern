package com.tiffles.travelingtavern.ui.protection.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.tiffles.travelingtavern.R
import com.tiffles.travelingtavern.domain.model.ExtensionOutcome
import com.tiffles.travelingtavern.domain.model.ProjectLinks
import com.tiffles.travelingtavern.ui.protection.ExtensionDialog
import com.tiffles.travelingtavern.ui.theme.TravelingTavernTheme

/**
 * The extension install / update dialogs (A8), one per [dialog] state. Stateless: [onConfirm]
 * runs what a confirmation asked about, [onUpdateInstead] is "Already installed"'s Update,
 * [onDismiss] closes (ignored while working).
 */
@Composable
fun ExtensionDialogs(
    dialog: ExtensionDialog,
    onConfirm: () -> Unit,
    onUpdateInstead: () -> Unit,
    onDismiss: () -> Unit
) {
    when (dialog) {
        ExtensionDialog.ConfirmInstall -> ConfirmDialog(
            title = stringResource(R.string.protection_confirm_install_title),
            text = stringResource(R.string.protection_confirm_install_text),
            confirmLabel = stringResource(R.string.protection_install),
            onConfirm = onConfirm,
            onDismiss = onDismiss
        )
        ExtensionDialog.ConfirmUpdate -> ConfirmDialog(
            title = stringResource(R.string.protection_confirm_update_title),
            text = stringResource(R.string.protection_confirm_update_text),
            confirmLabel = stringResource(R.string.protection_update),
            onConfirm = onConfirm,
            onDismiss = onDismiss
        )
        is ExtensionDialog.Working -> WorkingDialog(update = dialog.update)
        is ExtensionDialog.Done -> ResultDialog(outcome = dialog.outcome, onUpdateInstead = onUpdateInstead, onDismiss = onDismiss)
    }
}

@Composable
private fun ConfirmDialog(title: String, text: String, confirmLabel: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { Button(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.lorebook_cancel)) } }
    )
}

/** While SillyTavern clones or pulls from GitHub: no buttons, can't be dismissed. */
@Composable
private fun WorkingDialog(update: Boolean) {
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        text = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                Text(stringResource(if (update) R.string.protection_updating else R.string.protection_installing))
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun ResultDialog(outcome: ExtensionOutcome, onUpdateInstead: () -> Unit, onDismiss: () -> Unit) {
    when (outcome) {
        ExtensionOutcome.Installed ->
            MessageDialog(title = R.string.protection_installed_title, text = R.string.protection_reload_computer, onDismiss = onDismiss)
        ExtensionOutcome.Updated ->
            MessageDialog(title = R.string.protection_updated_title, text = R.string.protection_reload_computer, onDismiss = onDismiss)
        ExtensionOutcome.UpToDate ->
            MessageDialog(title = null, text = R.string.protection_up_to_date, onDismiss = onDismiss)
        ExtensionOutcome.UpdateFailed ->
            MessageDialog(title = R.string.protection_update_failed_title, text = R.string.protection_update_failed_text, onDismiss = onDismiss)
        ExtensionOutcome.InstallFailed -> InstallFailedDialog(onDismiss)
        ExtensionOutcome.AlreadyInstalled -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(stringResource(R.string.protection_already_installed_title)) },
            text = { Text(stringResource(R.string.protection_already_installed_text)) },
            confirmButton = { Button(onClick = onUpdateInstead) { Text(stringResource(R.string.protection_update)) } },
            dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.lorebook_cancel)) } }
        )
        // Never shown: a refused call closes the dialog (the pre-check's own handling applies).
        ExtensionOutcome.Refused -> Unit
    }
}

/** A result with an OK button. [title] and [text] are string resources; [title] null shows none. */
@Composable
private fun MessageDialog(@StringRes title: Int?, @StringRes text: Int, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = title?.let { { Text(stringResource(it)) } },
        text = { Text(stringResource(text)) },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.protection_ok)) } }
    )
}

/** The repo address as selectable text, for SillyTavern's Install extension on the computer (A8). */
@Composable
private fun InstallFailedDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.protection_install_failed_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.protection_install_failed_text))
                SelectionContainer {
                    // Plain text color: it can be selected and copied, not tapped, so it mustn't look like a link.
                    Text(
                        text = ProjectLinks.EXTENSION_REPO_URL,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.protection_ok)) } }
    )
}

@Preview(name = "Confirm install", showBackground = true)
@Composable
private fun ConfirmInstallPreview() {
    TravelingTavernTheme { ExtensionDialogs(ExtensionDialog.ConfirmInstall, {}, {}, {}) }
}

@Preview(name = "Working", showBackground = true)
@Composable
private fun WorkingPreview() {
    TravelingTavernTheme { ExtensionDialogs(ExtensionDialog.Working(update = false), {}, {}, {}) }
}

@Preview(name = "Install failed", showBackground = true)
@Composable
private fun InstallFailedPreview() {
    TravelingTavernTheme { ExtensionDialogs(ExtensionDialog.Done(ExtensionOutcome.InstallFailed), {}, {}, {}) }
}

@Preview(name = "Update failed", showBackground = true)
@Composable
private fun UpdateFailedPreview() {
    TravelingTavernTheme { ExtensionDialogs(ExtensionDialog.Done(ExtensionOutcome.UpdateFailed), {}, {}, {}) }
}
