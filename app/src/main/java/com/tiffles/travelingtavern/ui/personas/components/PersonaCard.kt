package com.tiffles.travelingtavern.ui.personas.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tiffles.travelingtavern.R
import com.tiffles.travelingtavern.domain.model.Persona
import com.tiffles.travelingtavern.ui.components.CharacterImage
import com.tiffles.travelingtavern.ui.theme.TravelingTavernTheme

/**
 * One persona in the list: its 3:4 picture, name, a two-line description and the badges that
 * apply. The active persona's card uses the accent container. Tapping the card uses the persona;
 * [menu] (the ⋯ and its dropdown) sits next to the name. No "Locked to this chat" badge: picking
 * in a chat always locks, so it would only repeat Active (user decision 2026-11-20).
 */
@Composable
fun PersonaCard(
    persona: Persona,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    menu: @Composable () -> Unit = {}
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        color = if (persona.isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PersonaPicture(persona)
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = persona.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(top = 12.dp)
                    )
                    menu()
                }
                if (persona.description.isNotBlank()) {
                    Text(
                        text = persona.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = LocalContentColor.current.copy(alpha = SECONDARY_ALPHA),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                PersonaBadges(persona = persona, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun PersonaPicture(persona: Persona) {
    Box(
        modifier = Modifier
            .width(PICTURE_WIDTH)
            .aspectRatio(PICTURE_RATIO)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        CharacterImage(
            imageUrl = persona.pictureUrl,
            name = persona.name,
            initialStyle = MaterialTheme.typography.titleLarge,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PersonaBadges(persona: Persona, modifier: Modifier = Modifier) {
    val labels = buildList {
        if (persona.isActive) add(R.string.personas_badge_active)
        if (persona.isDefault) add(R.string.personas_badge_default)
    }
    if (labels.isEmpty()) return
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        labels.forEach { Badge(stringResource(it)) }
    }
}

@Composable
private fun Badge(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

private val PICTURE_WIDTH = 72.dp
private const val PICTURE_RATIO = 3f / 4f
private const val SECONDARY_ALPHA = 0.75f

@Preview(name = "Card · active", showBackground = true)
@Preview(name = "Card · active, dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PersonaCardActivePreview() {
    TravelingTavernTheme {
        PersonaCard(
            persona = Persona("1-Mira.png", "Mira", "", "A cheerful bard who collects stories from every town she passes through.", null, true, true),
            enabled = true,
            onClick = {}
        )
    }
}

@Preview(name = "Card · plain", showBackground = true)
@Composable
private fun PersonaCardPreview() {
    TravelingTavernTheme {
        PersonaCard(
            persona = Persona("2-Rowan.png", "Rowan", "", "", null, false, false),
            enabled = true,
            onClick = {}
        )
    }
}
