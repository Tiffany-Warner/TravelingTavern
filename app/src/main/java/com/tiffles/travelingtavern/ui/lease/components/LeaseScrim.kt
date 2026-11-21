package com.tiffles.travelingtavern.ui.lease.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
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
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import com.tiffles.travelingtavern.R
import com.tiffles.travelingtavern.ui.lease.ScrimState
import com.tiffles.travelingtavern.ui.theme.TravelingTavernTheme

/**
 * The parked state (A3): an opaque surface over the whole app that swallows every touch
 * below it, with one title, one line and the row's control.
 */
@Composable
fun LeaseScrim(
    state: ScrimState,
    onUseHere: () -> Unit,
    onReload: () -> Unit,
    onUpdateWebView: () -> Unit,
    onContinueWithoutProtection: () -> Unit,
    modifier: Modifier = Modifier
) {
    // A non-clickable Surface consumes pointer input, so nothing below it can be tapped.
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.safeDrawingPadding().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(state.titleRes()),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(state.lineRes()),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            ScrimControl(
                state = state,
                onUseHere = onUseHere,
                onReload = onReload,
                onUpdateWebView = onUpdateWebView,
                onContinueWithoutProtection = onContinueWithoutProtection
            )
        }
    }
}

@Composable
private fun ScrimControl(
    state: ScrimState,
    onUseHere: () -> Unit,
    onReload: () -> Unit,
    onUpdateWebView: () -> Unit,
    onContinueWithoutProtection: () -> Unit
) {
    when (state) {
        ScrimState.InUse, ScrimState.LostRace, ScrimState.Unreadable -> Button(onClick = onUseHere) {
            Text(stringResource(R.string.lease_use_here))
        }
        ScrimState.Claiming, ScrimState.WaitingForReply -> CircularProgressIndicator()
        ScrimState.ReloadNeeded -> Button(onClick = onReload) {
            Text(stringResource(R.string.lease_reload))
        }
        ScrimState.UpdateWebView -> Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = onUpdateWebView) { Text(stringResource(R.string.lease_update)) }
            TextButton(onClick = onContinueWithoutProtection) {
                Text(stringResource(R.string.lease_continue_unprotected))
            }
        }
        // Nothing in the app can fix these; the user updates or restarts the app.
        ScrimState.UpdateApp, ScrimState.RestartNeeded -> Unit
    }
}

private fun ScrimState.titleRes(): Int = when (this) {
    ScrimState.InUse -> R.string.lease_in_use_title
    ScrimState.Claiming -> R.string.lease_claiming_title
    ScrimState.WaitingForReply -> R.string.lease_waiting_title
    ScrimState.LostRace -> R.string.lease_lost_race_title
    ScrimState.Unreadable -> R.string.lease_unreadable_title
    ScrimState.UpdateApp, ScrimState.UpdateWebView -> R.string.lease_update_title
    ScrimState.ReloadNeeded -> R.string.lease_reload_title
    ScrimState.RestartNeeded -> R.string.lease_restart_title
}

private fun ScrimState.lineRes(): Int = when (this) {
    ScrimState.InUse -> R.string.lease_in_use_line
    ScrimState.Claiming -> R.string.lease_claiming_line
    ScrimState.WaitingForReply -> R.string.lease_waiting_line
    ScrimState.LostRace -> R.string.lease_lost_race_line
    ScrimState.Unreadable -> R.string.lease_unreadable_line
    ScrimState.UpdateApp -> R.string.lease_update_app_line
    ScrimState.ReloadNeeded -> R.string.lease_reload_line
    ScrimState.RestartNeeded -> R.string.lease_restart_line
    ScrimState.UpdateWebView -> R.string.lease_update_webview_line
}

private class ScrimStateProvider : PreviewParameterProvider<ScrimState> {
    override val values = ScrimState.entries.asSequence()
}

@Preview(name = "Scrim · light", showBackground = true)
@Preview(name = "Scrim · dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LeaseScrimPreview(@PreviewParameter(ScrimStateProvider::class) state: ScrimState) {
    TravelingTavernTheme {
        LeaseScrim(
            state = state,
            onUseHere = {},
            onReload = {},
            onUpdateWebView = {},
            onContinueWithoutProtection = {}
        )
    }
}
