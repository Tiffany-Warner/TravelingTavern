package com.tiffles.travelingtavern.ui.lease.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
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
import com.tiffles.travelingtavern.domain.model.LeaseAction
import com.tiffles.travelingtavern.ui.lease.ScrimState
import com.tiffles.travelingtavern.ui.theme.TravelingTavernTheme

/**
 * The parked state (A3): an opaque surface over the whole app that swallows every touch below it,
 * with one title, one line and the state's ways out ([actions], primary first). With [warning] set,
 * the warning step for that action replaces the content: its title, line, confirm and Cancel.
 */
@Composable
fun LeaseScrim(
    state: ScrimState,
    actions: List<LeaseAction>,
    warning: LeaseAction?,
    onAction: (LeaseAction) -> Unit,
    onConfirmWarning: () -> Unit,
    onCancelWarning: () -> Unit,
    onUpdateWebView: () -> Unit,
    onContinueWithoutProtection: () -> Unit,
    modifier: Modifier = Modifier
) {
    // A non-clickable Surface consumes pointer input, so nothing below it can be tapped.
    Surface(modifier = modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        // Centered when it fits, scrollable when it doesn't (four buttons on a small phone in landscape).
        Box(modifier = Modifier.safeDrawingPadding().fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (warning != null) {
                    WarningStep(action = warning, onConfirm = onConfirmWarning, onCancel = onCancelWarning)
                } else {
                    ScrimBody(state, actions, onAction, onUpdateWebView, onContinueWithoutProtection)
                }
            }
        }
    }
}

@Composable
private fun ScrimBody(
    state: ScrimState,
    actions: List<LeaseAction>,
    onAction: (LeaseAction) -> Unit,
    onUpdateWebView: () -> Unit,
    onContinueWithoutProtection: () -> Unit
) {
    ScrimText(title = stringResource(state.titleRes()), line = stringResource(state.lineRes(actions)))
    if (state == ScrimState.UpdateWebView) {
        WebViewControls(onUpdateWebView = onUpdateWebView, onContinue = onContinueWithoutProtection)
    } else {
        if (state == ScrimState.Claiming || state == ScrimState.WaitingForReply) CircularProgressIndicator()
        ActionButtons(actions = actions, onAction = onAction)
    }
}

@Composable
private fun ScrimText(title: String, line: String) {
    Text(text = title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
    Text(
        text = line,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
}

/**
 * The first action is the amber primary; Use here and Refresh after it are tonal, the ones that go
 * ahead at the user's own risk are plain text buttons.
 */
@Composable
private fun ActionButtons(actions: List<LeaseAction>, onAction: (LeaseAction) -> Unit) {
    if (actions.isEmpty()) return
    Column(
        modifier = Modifier.widthIn(max = BUTTON_COLUMN_MAX_WIDTH),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        actions.forEachIndexed { index, action ->
            val modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            val label: @Composable () -> Unit = { Text(stringResource(action.labelRes())) }
            when {
                index == 0 -> Button(onClick = { onAction(action) }, modifier = modifier) { label() }
                action.needsWarning -> TextButton(onClick = { onAction(action) }, modifier = modifier) { label() }
                else -> FilledTonalButton(onClick = { onAction(action) }, modifier = modifier) { label() }
            }
        }
    }
}

@Composable
private fun WarningStep(action: LeaseAction, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val (title, line) = action.warningRes()
    ScrimText(title = stringResource(title), line = stringResource(line))
    Column(
        modifier = Modifier.widthIn(max = BUTTON_COLUMN_MAX_WIDTH),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        Button(onClick = onConfirm, modifier = modifier) { Text(stringResource(action.labelRes())) }
        TextButton(onClick = onCancel, modifier = modifier) { Text(stringResource(R.string.lease_cancel)) }
    }
}

@Composable
private fun WebViewControls(onUpdateWebView: () -> Unit, onContinue: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onUpdateWebView) { Text(stringResource(R.string.lease_update)) }
        TextButton(onClick = onContinue) { Text(stringResource(R.string.lease_continue_unprotected)) }
    }
}

private val BUTTON_COLUMN_MAX_WIDTH = 360.dp

private fun LeaseAction.labelRes(): Int = when (this) {
    LeaseAction.UseHere -> R.string.lease_use_here
    LeaseAction.Refresh -> R.string.lease_refresh
    LeaseAction.TakeOverAnyway -> R.string.lease_take_over_anyway
    LeaseAction.TurnOffProtection -> R.string.lease_turn_off_protection
    LeaseAction.TurnProtectionOn -> R.string.lease_turn_protection_on
    LeaseAction.ContinueWithoutProtection -> R.string.lease_continue_unprotected
}

/** The warning step's title and line (E9's texts, worded for the phone). */
private fun LeaseAction.warningRes(): Pair<Int, Int> = when (this) {
    LeaseAction.TakeOverAnyway -> R.string.lease_warn_take_over_title to R.string.lease_warn_take_over_line
    LeaseAction.ContinueWithoutProtection -> R.string.lease_warn_continue_title to R.string.lease_warn_turn_off_line
    else -> R.string.lease_warn_turn_off_title to R.string.lease_warn_turn_off_line
}

private fun ScrimState.titleRes(): Int = when (this) {
    ScrimState.InUse -> R.string.lease_in_use_title
    ScrimState.Claiming -> R.string.lease_claiming_title
    ScrimState.WaitingForReply -> R.string.lease_waiting_title
    ScrimState.LostRace -> R.string.lease_lost_race_title
    ScrimState.Unreadable -> R.string.lease_unreadable_title
    ScrimState.Damaged -> R.string.lease_damaged_title
    ScrimState.Error, ScrimState.UnknownState -> R.string.lease_error_title
    ScrimState.UpdateApp, ScrimState.UpdateWebView -> R.string.lease_update_title
    ScrimState.ReloadNeeded -> R.string.lease_reload_title
    ScrimState.RestartNeeded -> R.string.lease_restart_title
}

private fun ScrimState.lineRes(actions: List<LeaseAction>): Int = when (this) {
    ScrimState.InUse -> R.string.lease_in_use_line
    ScrimState.Claiming -> R.string.lease_claiming_line
    ScrimState.WaitingForReply ->
        if (LeaseAction.TakeOverAnyway in actions) R.string.lease_waiting_line_stuck else R.string.lease_waiting_line
    ScrimState.LostRace -> R.string.lease_lost_race_line
    ScrimState.Unreadable -> R.string.lease_unreadable_line
    ScrimState.Damaged -> R.string.lease_damaged_line
    ScrimState.Error -> R.string.lease_error_line
    ScrimState.UnknownState -> R.string.lease_unknown_line
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
            actions = listOf(LeaseAction.Refresh, LeaseAction.TakeOverAnyway, LeaseAction.TurnOffProtection),
            warning = null,
            onAction = {},
            onConfirmWarning = {},
            onCancelWarning = {},
            onUpdateWebView = {},
            onContinueWithoutProtection = {}
        )
    }
}

@Preview(name = "Warning step · light", showBackground = true)
@Preview(name = "Warning step · dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LeaseScrimWarningPreview() {
    TravelingTavernTheme {
        LeaseScrim(
            state = ScrimState.Damaged,
            actions = emptyList(),
            warning = LeaseAction.TakeOverAnyway,
            onAction = {},
            onConfirmWarning = {},
            onCancelWarning = {},
            onUpdateWebView = {},
            onContinueWithoutProtection = {}
        )
    }
}
