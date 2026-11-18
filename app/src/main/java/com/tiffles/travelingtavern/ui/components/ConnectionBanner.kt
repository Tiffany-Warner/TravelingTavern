package com.tiffles.travelingtavern.ui.components

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
import com.tiffles.travelingtavern.domain.model.PageError
import com.tiffles.travelingtavern.ui.theme.TravelingTavernTheme

/**
 * Inline banner at the top of a tab when the server can't be used, with the one action that
 * can fix it. Not shown at all while connected.
 */
@Composable
fun ConnectionBanner(
    error: PageError,
    onRetry: () -> Unit,
    onOpenServerSettings: () -> Unit,
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
                text = error.bannerText(),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).padding(vertical = 12.dp)
            )
            BannerAction(error = error, onRetry = onRetry, onOpenServerSettings = onOpenServerSettings)
        }
    }
}

@Composable
private fun BannerAction(error: PageError, onRetry: () -> Unit, onOpenServerSettings: () -> Unit) {
    when (error) {
        PageError.Unreachable, is PageError.HttpError -> TextButton(onClick = onRetry) {
            Text(stringResource(R.string.webview_retry))
        }
        PageError.AuthFailed -> TextButton(onClick = onOpenServerSettings) {
            Text(stringResource(R.string.webview_server_settings))
        }
        // Only a WebView update from the Play Store helps; nothing in the app can.
        PageError.WebViewOutdated -> Unit
    }
}

@Composable
private fun PageError.bannerText(): String = when (this) {
    PageError.Unreachable -> stringResource(R.string.banner_unreachable)
    PageError.AuthFailed -> stringResource(R.string.banner_auth_failed)
    is PageError.HttpError -> stringResource(R.string.webview_error_http, code)
    PageError.WebViewOutdated -> stringResource(R.string.webview_error_outdated)
}

@Preview(name = "Unreachable · light", showBackground = true)
@Preview(name = "Unreachable · dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ConnectionBannerUnreachablePreview() {
    TravelingTavernTheme {
        ConnectionBanner(error = PageError.Unreachable, onRetry = {}, onOpenServerSettings = {})
    }
}

@Preview(name = "Login failed", showBackground = true)
@Composable
private fun ConnectionBannerAuthPreview() {
    TravelingTavernTheme {
        ConnectionBanner(error = PageError.AuthFailed, onRetry = {}, onOpenServerSettings = {})
    }
}
