package com.tiffles.travelingtavern.ui.chat.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tiffles.travelingtavern.R
import com.tiffles.travelingtavern.domain.model.Persona
import com.tiffles.travelingtavern.ui.components.CharacterAvatar
import com.tiffles.travelingtavern.ui.theme.TravelingTavernTheme

/** "Chatting as {name}" with the persona's picture; tapping it opens the Personas screen. */
@Composable
fun PersonaChip(persona: Persona, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier.widthIn(max = MAX_WIDTH)
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CharacterAvatar(
                imageUrl = persona.pictureUrl,
                name = persona.name,
                size = 24.dp,
                initialStyle = MaterialTheme.typography.labelMedium
            )
            Text(
                text = stringResource(R.string.personas_chip, persona.name),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
    }
}

private val MAX_WIDTH = 280.dp

@Preview(name = "Chip", showBackground = true)
@Preview(name = "Chip · dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PersonaChipPreview() {
    TravelingTavernTheme {
        PersonaChip(Persona("1-Mira.png", "Mira", "", "", null, true, false), onClick = {})
    }
}

@Preview(name = "Chip · long name", showBackground = true)
@Composable
private fun PersonaChipLongPreview() {
    TravelingTavernTheme {
        PersonaChip(
            Persona("2.png", "Lady Evangeline Montgomery-Whitmore of the Northern Reaches", "", "", null, true, false),
            onClick = {}
        )
    }
}
