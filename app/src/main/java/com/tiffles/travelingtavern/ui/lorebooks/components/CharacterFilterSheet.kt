package com.tiffles.travelingtavern.ui.lorebooks.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tiffles.travelingtavern.R
import com.tiffles.travelingtavern.domain.model.CharacterSummary
import com.tiffles.travelingtavern.ui.components.CharacterAvatar
import com.tiffles.travelingtavern.ui.theme.TravelingTavernTheme

/** The Character chip's sheet: characters that have at least one lorebook. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CharacterFilterSheet(
    characters: List<CharacterSummary>,
    onPick: (CharacterSummary) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        CharacterFilterList(characters = characters, onPick = onPick)
    }
}

@Composable
private fun CharacterFilterList(characters: List<CharacterSummary>, onPick: (CharacterSummary) -> Unit) {
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        Text(
            text = stringResource(R.string.lorebooks_pick_character),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        LazyColumn {
            items(characters, key = { it.id }) { character ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clickable { onPick(character) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CharacterAvatar(imageUrl = character.avatarUrl, name = character.name, size = 40.dp)
                    Text(character.name, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun CharacterFilterListPreview() {
    TravelingTavernTheme {
        CharacterFilterList(
            characters = listOf("Bob", "Seraphina").map { CharacterSummary("$it.png", it, "", "", null) },
            onPick = {}
        )
    }
}
