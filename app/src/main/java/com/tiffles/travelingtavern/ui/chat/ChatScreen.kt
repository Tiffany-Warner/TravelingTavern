package com.tiffles.travelingtavern.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.tiffles.travelingtavern.R
import com.tiffles.travelingtavern.ui.components.PlaceholderScreen
import com.tiffles.travelingtavern.ui.theme.TravelingTavernTheme

@Composable
fun ChatScreen(characterId: String, modifier: Modifier = Modifier) {
    PlaceholderScreen(
        label = stringResource(R.string.screen_chat, characterId),
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
private fun ChatScreenPreview() {
    TravelingTavernTheme {
        ChatScreen(characterId = "Seraphina.png")
    }
}