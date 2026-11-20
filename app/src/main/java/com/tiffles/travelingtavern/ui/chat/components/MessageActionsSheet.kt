package com.tiffles.travelingtavern.ui.chat.components

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tiffles.travelingtavern.R
import com.tiffles.travelingtavern.domain.model.MessageAction
import com.tiffles.travelingtavern.domain.model.TappedMessage
import com.tiffles.travelingtavern.ui.chat.MessageSheet
import com.tiffles.travelingtavern.ui.chat.formatSendDate
import com.tiffles.travelingtavern.ui.theme.TravelingTavernTheme

/**
 * SillyTavern's actions for a tapped message, in a bottom sheet: when and by whom, then a grid.
 * Once the sheet has settled, [onShown] gets the screen Y of its top edge (px), so the page can
 * scroll the message above it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageActionsSheet(
    sheet: MessageSheet,
    onAction: (MessageAction) -> Unit,
    onDismiss: () -> Unit,
    onShown: (sheetTopOnScreen: Float) -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var sheetTop by remember { mutableFloatStateOf(Float.NaN) }
    LaunchedEffect(sheetState.currentValue, sheetTop) {
        if (sheetState.currentValue == SheetValue.Expanded && !sheetTop.isNaN()) onShown(sheetTop)
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        MessageActionsContent(
            sheet = sheet,
            onAction = onAction,
            modifier = Modifier.onGloballyPositioned { sheetTop = it.positionOnScreen().y }
        )
    }
}

@Composable
fun MessageActionsContent(sheet: MessageSheet, onAction: (MessageAction) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        MessageHeader(message = sheet.message)
        sheet.actions.chunked(ACTIONS_PER_ROW).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { action ->
                    ActionTile(
                        action = action,
                        enabled = action in sheet.enabled,
                        onClick = { onAction(action) },
                        modifier = Modifier.weight(1f)
                    )
                }
                repeat(ACTIONS_PER_ROW - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun MessageHeader(message: TappedMessage) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(formatSendDate(message.sendDate), style = MaterialTheme.typography.titleMedium)
        if (message.senderName.isNotEmpty()) {
            Text(
                text = stringResource(R.string.chat_sent_by, message.senderName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ActionTile(action: MessageAction, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 72.dp)
            .padding(vertical = 8.dp)
            .alpha(if (enabled) 1f else DISABLED_ALPHA),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(painter = painterResource(action.iconRes()), contentDescription = null)
        Text(
            text = stringResource(action.labelRes()),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center
        )
    }
}

@StringRes
private fun MessageAction.labelRes(): Int = when (this) {
    MessageAction.Regenerate -> R.string.chat_action_regenerate
    MessageAction.Continue -> R.string.chat_action_continue
    MessageAction.Copy -> R.string.chat_action_copy
    MessageAction.Edit -> R.string.chat_action_edit
    MessageAction.Branch -> R.string.chat_action_branch
    MessageAction.Hide -> R.string.chat_action_hide
    MessageAction.Show -> R.string.chat_action_show
    MessageAction.Delete -> R.string.chat_action_delete
}

@DrawableRes
private fun MessageAction.iconRes(): Int = when (this) {
    MessageAction.Regenerate -> R.drawable.ic_refresh
    MessageAction.Continue -> R.drawable.ic_fast_forward
    MessageAction.Copy -> R.drawable.ic_content_copy
    MessageAction.Edit -> R.drawable.ic_edit
    MessageAction.Branch -> R.drawable.ic_call_split
    MessageAction.Hide -> R.drawable.ic_visibility_off
    MessageAction.Show -> R.drawable.ic_visibility
    MessageAction.Delete -> R.drawable.ic_delete
}

private const val ACTIONS_PER_ROW = 4
private const val DISABLED_ALPHA = 0.38f

@Preview(name = "Actions · latest reply", showBackground = true)
@Preview(name = "Actions · dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MessageActionsPreview() {
    val message = TappedMessage(4, "2026-11-19T15:04:00.000Z", "Seraphina", false, true, false, true, true)
    val actions = MessageAction.entries.filter { it != MessageAction.Show }
    TravelingTavernTheme {
        MessageActionsContent(sheet = MessageSheet(message, actions, actions.toSet()), onAction = {})
    }
}

@Preview(name = "Actions · generating", showBackground = true)
@Composable
private fun MessageActionsGeneratingPreview() {
    val message = TappedMessage(2, "2026-11-19T15:00:00.000Z", "You", true, false, false, false, false)
    val actions = listOf(MessageAction.Copy, MessageAction.Edit, MessageAction.Branch, MessageAction.Hide, MessageAction.Delete)
    TravelingTavernTheme {
        MessageActionsContent(sheet = MessageSheet(message, actions, setOf(MessageAction.Copy)), onAction = {})
    }
}
