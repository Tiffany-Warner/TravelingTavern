package com.tiffles.travelingtavern.ui.chat.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tiffles.travelingtavern.R
import com.tiffles.travelingtavern.domain.model.ModelBanner
import com.tiffles.travelingtavern.ui.theme.TravelingTavernTheme

/**
 * "Not connected to a model" with Connect (A7). After a Connect that didn't help, it points to
 * SillyTavern's API Connections instead; Connect stays, for a fix made on the computer.
 */
@Composable
fun ModelConnectionBanner(
    banner: ModelBanner,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(
                    if (banner.stillNotConnected) R.string.chat_still_not_connected else R.string.chat_not_connected
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).padding(vertical = 12.dp)
            )
            TextButton(onClick = onConnect, enabled = !banner.connecting) {
                Text(stringResource(if (banner.connecting) R.string.chat_connecting else R.string.chat_connect))
            }
        }
    }
}

@Preview(name = "Not connected · light", showBackground = true)
@Preview(name = "Not connected · dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ModelConnectionBannerPreview() {
    TravelingTavernTheme {
        ModelConnectionBanner(banner = ModelBanner(), onConnect = {})
    }
}

@Preview(name = "Connecting", showBackground = true)
@Composable
private fun ModelConnectionBannerConnectingPreview() {
    TravelingTavernTheme {
        ModelConnectionBanner(banner = ModelBanner(connecting = true), onConnect = {})
    }
}

@Preview(name = "Still not connected", showBackground = true)
@Composable
private fun ModelConnectionBannerStillPreview() {
    TravelingTavernTheme {
        ModelConnectionBanner(banner = ModelBanner(stillNotConnected = true), onConnect = {})
    }
}
