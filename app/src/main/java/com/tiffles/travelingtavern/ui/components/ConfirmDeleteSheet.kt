package com.tiffles.travelingtavern.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tiffles.travelingtavern.R
import com.tiffles.travelingtavern.ui.theme.TravelingTavernTheme

/**
 * A bottom sheet asking to confirm a deletion. [details] are extra lines under [message].
 * [confirmLabel] replaces "Delete"; [destructive] false uses the accent instead of the error
 * color, for a confirmation that deletes nothing (e.g. Start new chat).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfirmDeleteSheet(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    details: List<String> = emptyList(),
    isWorking: Boolean = false,
    confirmLabel: String? = null,
    destructive: Boolean = true
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        ConfirmDeleteContent(
            title = title,
            message = message,
            details = details,
            isWorking = isWorking,
            confirmLabel = confirmLabel ?: stringResource(R.string.lorebook_delete_action),
            destructive = destructive,
            onConfirm = onConfirm,
            onDismiss = onDismiss
        )
    }
}

@Composable
private fun ConfirmDeleteContent(
    title: String,
    message: String,
    details: List<String>,
    isWorking: Boolean,
    confirmLabel: String,
    destructive: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        details.forEach { line ->
            Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
        ) {
            TextButton(onClick = onDismiss, enabled = !isWorking) { Text(stringResource(R.string.lorebook_cancel)) }
            Button(
                onClick = onConfirm,
                enabled = !isWorking,
                colors = if (destructive) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                } else {
                    ButtonDefaults.buttonColors()
                }
            ) {
                Text(confirmLabel)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ConfirmDeleteContentPreview() {
    TravelingTavernTheme {
        ConfirmDeleteContent(
            title = "Delete Eldoria?",
            message = "This can't be undone.",
            details = listOf("Seraphina will keep a link to this name. Clear it in Advanced if you plan to reuse the name."),
            isWorking = false,
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {},
            onDismiss = {}
        )
    }
}
