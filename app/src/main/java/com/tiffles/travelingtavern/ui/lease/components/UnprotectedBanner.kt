package com.tiffles.travelingtavern.ui.lease.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tiffles.travelingtavern.R
import com.tiffles.travelingtavern.ui.theme.TravelingTavernTheme

/**
 * The persistent warning while SillyTavern runs without the lease (Continue without
 * protection, A2). Drawn edge to edge at the top: it pads itself by [insets] (the top system
 * bars), which the content below must then consume.
 */
@Composable
fun UnprotectedBanner(insets: WindowInsets, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = stringResource(R.string.lease_unprotected_banner),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .windowInsetsPadding(insets)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}

@Preview(name = "Not protected · light", showBackground = true)
@Preview(name = "Not protected · dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun UnprotectedBannerPreview() {
    TravelingTavernTheme {
        UnprotectedBanner(insets = WindowInsets(0))
    }
}
