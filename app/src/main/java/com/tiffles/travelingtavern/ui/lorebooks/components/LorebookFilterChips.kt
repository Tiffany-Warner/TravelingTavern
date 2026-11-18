package com.tiffles.travelingtavern.ui.lorebooks.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tiffles.travelingtavern.R
import com.tiffles.travelingtavern.domain.model.CharacterSummary
import com.tiffles.travelingtavern.domain.model.LorebookFilter
import com.tiffles.travelingtavern.ui.theme.TravelingTavernTheme

/**
 * All · Global · Character ▾ · Unused, single-select. The Character chip is hidden when
 * [showCharacterChip] is false; while a character is selected it shows their name and ✕,
 * and tapping it clears the filter.
 */
@Composable
fun LorebookFilterChips(
    filter: LorebookFilter,
    selectedCharacter: CharacterSummary?,
    showCharacterChip: Boolean,
    onFilterChange: (LorebookFilter) -> Unit,
    onPickCharacter: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SimpleChip(R.string.lorebooks_filter_all, filter == LorebookFilter.All) { onFilterChange(LorebookFilter.All) }
        SimpleChip(R.string.lorebooks_filter_global, filter == LorebookFilter.Global) { onFilterChange(LorebookFilter.Global) }
        if (showCharacterChip) {
            CharacterChip(
                selectedCharacter = selectedCharacter.takeIf { filter is LorebookFilter.Character },
                onPick = onPickCharacter,
                onClear = { onFilterChange(LorebookFilter.All) }
            )
        }
        SimpleChip(R.string.lorebooks_filter_unused, filter == LorebookFilter.Unused) { onFilterChange(LorebookFilter.Unused) }
    }
}

@Composable
private fun SimpleChip(label: Int, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(stringResource(label)) })
}

@Composable
private fun CharacterChip(selectedCharacter: CharacterSummary?, onPick: () -> Unit, onClear: () -> Unit) {
    if (selectedCharacter == null) {
        FilterChip(
            selected = false,
            onClick = onPick,
            label = { Text(stringResource(R.string.lorebooks_filter_character)) },
            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, contentDescription = null) }
        )
    } else {
        FilterChip(
            selected = true,
            onClick = onClear,
            label = { Text(selectedCharacter.name) },
            trailingIcon = {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.lorebooks_clear_character),
                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                )
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun LorebookFilterChipsPreview() {
    TravelingTavernTheme {
        LorebookFilterChips(
            filter = LorebookFilter.Character("Seraphina.png"),
            selectedCharacter = CharacterSummary("Seraphina.png", "Seraphina", "", "", null),
            showCharacterChip = true,
            onFilterChange = {},
            onPickCharacter = {}
        )
    }
}
