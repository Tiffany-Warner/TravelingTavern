package com.tiffles.travelingtavern.ui.lorebooks.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tiffles.travelingtavern.R
import com.tiffles.travelingtavern.domain.model.CharacterSummary
import com.tiffles.travelingtavern.domain.model.Lorebook
import com.tiffles.travelingtavern.domain.model.LorebookCard
import com.tiffles.travelingtavern.domain.model.LorebookStatus
import com.tiffles.travelingtavern.ui.components.CharacterAvatar
import com.tiffles.travelingtavern.ui.theme.TravelingTavernTheme

/**
 * One lorebook in the grid: a neutral 4:3 cover, the name, the entry count and a status
 * line. The whole card is one tap target.
 */
@Composable
fun LorebookGridCard(card: LorebookCard, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        LorebookCover()
        Column(
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = card.lorebook.name,
                style = MaterialTheme.typography.titleSmall,
                // Always two lines tall, so cards in a row end at the same height.
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = entryCountLabel(card.lorebook.entryCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            LorebookStatusLine(status = card.status)
        }
    }
}

@Composable
private fun LorebookCover() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_book_outline),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(32.dp)
        )
    }
}

@Composable
fun entryCountLabel(count: Int?): String =
    if (count == null) stringResource(R.string.lorebooks_entries_unknown)
    else pluralStringResource(R.plurals.lorebooks_entries, count, count)

/** "● Global", "● (avatar) Seraphina +2", "● Persona" or a muted "Not active". Display only. */
@Composable
fun LorebookStatusLine(status: LorebookStatus, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        when (status) {
            LorebookStatus.Global -> ActiveStatus(text = stringResource(R.string.lorebooks_status_global))
            LorebookStatus.Persona -> ActiveStatus(text = stringResource(R.string.lorebooks_status_persona))
            is LorebookStatus.Characters -> ActiveStatus(
                text = if (status.others == 0) status.first.name
                else stringResource(R.string.lorebooks_status_more, status.first.name, status.others),
                character = status.first
            )
            LorebookStatus.NotActive -> Text(
                text = stringResource(R.string.lorebooks_status_not_active),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ActiveStatus(text: String, character: CharacterSummary? = null) {
    Box(
        modifier = Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary)
    )
    Spacer(Modifier.width(6.dp))
    if (character != null) {
        CharacterAvatar(
            imageUrl = character.avatarUrl,
            name = character.name,
            size = 16.dp,
            initialStyle = MaterialTheme.typography.labelSmall
        )
        Spacer(Modifier.width(4.dp))
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

internal fun previewLorebook(name: String, global: Boolean = false, characters: Int = 0, persona: Boolean = false) =
    LorebookCard(
        lorebook = Lorebook(
            name = name,
            entryCount = 12,
            isGlobal = global,
            characterIds = List(characters) { "c$it.png" },
            personaNames = if (persona) listOf("Me") else emptyList()
        ),
        characters = listOf("Seraphina", "Bob", "Oliver").take(characters).map {
            CharacterSummary(id = "$it.png", name = it, avatarUrl = "", portraitUrl = "", lastChatAt = null)
        }
    )

@Preview(name = "Cards · light", showBackground = true, widthDp = 360)
@Preview(name = "Cards · dark", showBackground = true, widthDp = 360, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LorebookGridCardPreview() {
    TravelingTavernTheme {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LorebookGridCard(previewLorebook("Eldoria", characters = 3), onClick = {}, modifier = Modifier.weight(1f))
            LorebookGridCard(previewLorebook("A very long lorebook name that wraps", global = true), onClick = {}, modifier = Modifier.weight(1f))
        }
    }
}
