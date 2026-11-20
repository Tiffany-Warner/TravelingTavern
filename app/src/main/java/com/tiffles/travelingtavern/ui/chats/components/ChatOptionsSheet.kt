package com.tiffles.travelingtavern.ui.chats.components

import android.content.res.Configuration
import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tiffles.travelingtavern.R
import com.tiffles.travelingtavern.domain.model.CharacterSummary
import com.tiffles.travelingtavern.domain.model.RecentChat
import com.tiffles.travelingtavern.ui.components.CharacterAvatar
import com.tiffles.travelingtavern.ui.components.label
import com.tiffles.travelingtavern.ui.components.relativeTime
import com.tiffles.travelingtavern.ui.theme.TravelingTavernTheme
import java.time.Instant
import java.time.ZoneId

/** Long press options for a Chats row: Hide (app only) and Delete (in SillyTavern). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatOptionsSheet(
    chat: RecentChat,
    now: Instant,
    zone: ZoneId,
    canDelete: Boolean,
    onHide: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        ChatOptionsContent(chat = chat, now = now, zone = zone, canDelete = canDelete, onHide = onHide, onDelete = onDelete)
    }
}

@Composable
fun ChatOptionsContent(
    chat: RecentChat,
    now: Instant,
    zone: ZoneId,
    canDelete: Boolean,
    onHide: () -> Unit,
    onDelete: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
        ChatHeader(chat = chat, now = now, zone = zone)
        OptionRow(
            iconRes = R.drawable.ic_visibility_off,
            label = stringResource(R.string.chats_option_hide),
            color = MaterialTheme.colorScheme.onSurface,
            enabled = true,
            onClick = onHide
        )
        OptionRow(
            iconRes = R.drawable.ic_delete,
            label = stringResource(R.string.chats_option_delete),
            color = MaterialTheme.colorScheme.error,
            enabled = canDelete,
            onClick = onDelete
        )
    }
}

@Composable
private fun ChatHeader(chat: RecentChat, now: Instant, zone: ZoneId) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CharacterAvatar(imageUrl = chat.character.avatarUrl, name = chat.character.name)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = chat.character.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            chat.lastMessageAt?.let { at ->
                Text(
                    text = relativeTime(at, now, zone).label(),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun OptionRow(@DrawableRes iconRes: Int, label: String, color: Color, enabled: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 8.dp)
            .alpha(if (enabled) 1f else DISABLED_ALPHA),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painter = painterResource(iconRes), contentDescription = null, tint = color)
        Text(text = label, style = MaterialTheme.typography.bodyLarge, color = color)
    }
}

private const val DISABLED_ALPHA = 0.38f

private val previewNow: Instant = Instant.parse("2026-11-19T18:00:00Z")

private val previewChat = RecentChat(
    character = CharacterSummary("Seraphina.png", "Seraphina", "", "", null),
    lastMessage = "Welcome back.",
    lastMessageAt = previewNow.minusSeconds(300),
    chatFile = "Seraphina - 2026-11-19@17h55m00s"
)

@Preview(name = "Options · light", showBackground = true)
@Preview(name = "Options · dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatOptionsPreview() {
    TravelingTavernTheme {
        ChatOptionsContent(previewChat, previewNow, ZoneId.of("UTC"), canDelete = true, onHide = {}, onDelete = {})
    }
}

@Preview(name = "Options · generating", showBackground = true)
@Composable
private fun ChatOptionsGeneratingPreview() {
    TravelingTavernTheme {
        ChatOptionsContent(previewChat, previewNow, ZoneId.of("UTC"), canDelete = false, onHide = {}, onDelete = {})
    }
}
