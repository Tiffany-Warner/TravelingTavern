package com.tiffles.travelingtavern.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.tiffles.travelingtavern.R
import com.tiffles.travelingtavern.ui.theme.TravelingTavernTheme

/**
 * Confirmation for deleting something on SillyTavern itself (a whole chat, a persona). An alert
 * dialog with a red delete icon, [warning] as a bold red first line, then [message], and a red
 * button (user decision, 2026-11-19). While [isWorking] the buttons are disabled and the dialog
 * can't be dismissed.
 */
@Composable
fun ConfirmDeleteDialog(
    title: String,
    warning: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isWorking: Boolean = false
) {
    AlertDialog(
        onDismissRequest = { if (!isWorking) onDismiss() },
        properties = DialogProperties(dismissOnBackPress = !isWorking, dismissOnClickOutside = !isWorking),
        icon = {
            Icon(
                painter = painterResource(R.drawable.ic_delete),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // First and loudest: it's deleted in SillyTavern itself, not just in the app.
                Text(
                    text = warning,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.error
                )
                Text(message)
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isWorking,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isWorking) { Text(stringResource(R.string.lorebook_cancel)) }
        }
    )
}

@Preview(name = "Delete · light", showBackground = true)
@Preview(name = "Delete · dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ConfirmDeleteDialogPreview() {
    TravelingTavernTheme {
        ConfirmDeleteDialog(
            title = "Delete Mira?",
            warning = "This also deletes the persona on your SillyTavern.",
            message = "Chats keep their messages, but this persona is gone.",
            confirmLabel = "Delete",
            onConfirm = {},
            onDismiss = {}
        )
    }
}
