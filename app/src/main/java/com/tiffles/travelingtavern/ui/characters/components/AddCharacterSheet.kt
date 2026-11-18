package com.tiffles.travelingtavern.ui.characters.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tiffles.travelingtavern.R
import com.tiffles.travelingtavern.ui.theme.TravelingTavernTheme

/**
 * Create vs. Import, opened from the Characters FAB. Both arrive later (creator in Phase 5),
 * so for now each says "Coming soon" and does nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCharacterSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        AddCharacterOptions(modifier = Modifier.padding(bottom = 16.dp))
    }
}

@Composable
private fun AddCharacterOptions(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        ComingSoonOption(label = stringResource(R.string.characters_create))
        ComingSoonOption(label = stringResource(R.string.characters_import))
    }
}

@Composable
private fun ComingSoonOption(label: String) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = { Text(stringResource(R.string.coming_soon)) },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            headlineColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

@Preview(showBackground = true)
@Composable
private fun AddCharacterOptionsPreview() {
    TravelingTavernTheme {
        AddCharacterOptions()
    }
}
