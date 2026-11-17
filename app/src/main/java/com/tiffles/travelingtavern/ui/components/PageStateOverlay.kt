package com.tiffles.travelingtavern.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tiffles.travelingtavern.R
import com.tiffles.travelingtavern.domain.model.PageError
import com.tiffles.travelingtavern.domain.model.PageState
import com.tiffles.travelingtavern.ui.theme.TravelingTavernTheme

/** Covers the WebView while the page is loading or failed; draws nothing once it is ready. */
@Composable
fun PageStateOverlay(
    pageState: PageState,
    onRetry: () -> Unit,
    onOpenServerSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (pageState) {
        PageState.Ready -> Unit
        PageState.Loading -> LoadingOverlay(modifier = modifier)
        is PageState.Error -> ErrorOverlay(
            error = pageState.error,
            onRetry = onRetry,
            onOpenServerSettings = onOpenServerSettings,
            modifier = modifier
        )
    }
}

@Composable
private fun LoadingOverlay(modifier: Modifier = Modifier) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    }
}

@Composable
private fun ErrorOverlay(
    error: PageError,
    onRetry: () -> Unit,
    onOpenServerSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = error.message(),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center
            )
            Button(onClick = onRetry) {
                Text(stringResource(R.string.webview_retry))
            }
            TextButton(onClick = onOpenServerSettings) {
                Text(stringResource(R.string.webview_server_settings))
            }
        }
    }
}

@Composable
private fun PageError.message(): String = when (this) {
    PageError.Unreachable -> stringResource(R.string.connect_result_unreachable)
    PageError.AuthFailed -> stringResource(R.string.webview_error_auth_failed)
    is PageError.HttpError -> stringResource(R.string.webview_error_http, code)
}

@Preview(name = "Loading", showBackground = true)
@Composable
private fun PageStateOverlayLoadingPreview() {
    TravelingTavernTheme {
        PageStateOverlay(pageState = PageState.Loading, onRetry = {}, onOpenServerSettings = {})
    }
}

@Preview(name = "Unreachable · light", showBackground = true)
@Preview(name = "Unreachable · dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PageStateOverlayUnreachablePreview() {
    TravelingTavernTheme {
        PageStateOverlay(
            pageState = PageState.Error(PageError.Unreachable),
            onRetry = {},
            onOpenServerSettings = {}
        )
    }
}

@Preview(name = "Login rejected", showBackground = true)
@Composable
private fun PageStateOverlayAuthFailedPreview() {
    TravelingTavernTheme {
        PageStateOverlay(
            pageState = PageState.Error(PageError.AuthFailed),
            onRetry = {},
            onOpenServerSettings = {}
        )
    }
}

@Preview(name = "HTTP error", showBackground = true)
@Composable
private fun PageStateOverlayHttpErrorPreview() {
    TravelingTavernTheme {
        PageStateOverlay(
            pageState = PageState.Error(PageError.HttpError(code = 502)),
            onRetry = {},
            onOpenServerSettings = {}
        )
    }
}
