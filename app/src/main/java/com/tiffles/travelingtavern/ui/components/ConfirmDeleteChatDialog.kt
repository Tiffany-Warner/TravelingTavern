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
 * Confirmation for anything that deletes a whole chat (Delete in Chats, Delete and restart).
 * An alert dialog, not a sheet, so it can't be mistaken for the Start new chat sheet: red
 * delete icon, a bold red line saying the chat is deleted on SillyTavern too, then [message],
 * and a red button (user decision, 2026-11-19). While [isWorking] the buttons
 * are disabled and the dialog can't be dismissed.
 */
@Composable
fun ConfirmDeleteChatDialog(
    title: String,
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
                // First and loudest: the chat is deleted in SillyTavern itself, not just in the app.
                Text(
                    text = stringResource(R.string.chat_delete_warning),
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

@Preview(name = "Delete chat · light", showBackground = true)
@Preview(name = "Delete chat · dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ConfirmDeleteChatDialogPreview() {
    TravelingTavernTheme {
        ConfirmDeleteChatDialog(
            title = "Delete this chat?",
            message = "Every message in it is gone for good. This can't be undone.",
            confirmLabel = "Delete",
            onConfirm = {},
            onDismiss = {}
        )
    }
}
