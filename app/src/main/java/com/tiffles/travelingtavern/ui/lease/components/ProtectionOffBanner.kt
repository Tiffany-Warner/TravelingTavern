package com.tiffles.travelingtavern.ui.lease.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tiffles.travelingtavern.R
import com.tiffles.travelingtavern.domain.model.ProjectLinks
import com.tiffles.travelingtavern.ui.theme.TravelingTavernTheme

/**
 * Protection is off in this page (the user's Turn off protection, E4, A3): shown on every screen
 * until [onTurnProtectionOn]. "GitHub" opens the app's Issues page. Drawn edge to edge at the top
 * like [UnprotectedBanner]: it pads itself by [insets], which the content below must then consume.
 */
@Composable
fun ProtectionOffBanner(insets: WindowInsets, onTurnProtectionOn: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.windowInsetsPadding(insets).padding(start = 16.dp, end = 8.dp, top = 12.dp)) {
            Text(
                text = offText(),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(end = 8.dp)
            )
            TextButton(onClick = onTurnProtectionOn, modifier = Modifier.align(Alignment.End)) {
                Text(stringResource(R.string.lease_turn_protection_on))
            }
        }
    }
}

@Composable
private fun offText() = run {
    val link = stringResource(R.string.lease_off_banner_link)
    val full = stringResource(R.string.lease_off_banner, link)
    val start = full.indexOf(link)
    buildAnnotatedString {
        if (start < 0) {
            append(full)
            return@buildAnnotatedString
        }
        append(full.substring(0, start))
        val style = TextLinkStyles(SpanStyle(textDecoration = TextDecoration.Underline))
        withLink(LinkAnnotation.Url(ProjectLinks.APP_ISSUES_URL, style)) { append(link) }
        append(full.substring(start + link.length))
    }
}

@Preview(name = "Protection off · light", showBackground = true)
@Preview(name = "Protection off · dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ProtectionOffBannerPreview() {
    TravelingTavernTheme {
        ProtectionOffBanner(insets = WindowInsets(0), onTurnProtectionOn = {})
    }
}
