package com.tiffles.travelingtavern.ui.components

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.tiffles.travelingtavern.R
import com.tiffles.travelingtavern.ui.theme.TravelingTavernTheme

/**
 * Confirmation for anything that deletes a whole chat (Delete in Chats, Delete and restart).
 * An alert dialog, not a sheet, so it can't be mistaken for the Start new chat sheet; its bold
 * red line says the chat is deleted on SillyTavern too (user decision, 2026-11-19).
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
    ConfirmDeleteDialog(
        title = title,
        warning = stringResource(R.string.chat_delete_warning),
        message = message,
        confirmLabel = confirmLabel,
        onConfirm = onConfirm,
        onDismiss = onDismiss,
        isWorking = isWorking
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
