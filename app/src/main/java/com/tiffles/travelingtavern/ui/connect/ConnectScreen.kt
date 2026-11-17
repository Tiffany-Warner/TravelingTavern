package com.tiffles.travelingtavern.ui.connect

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.tiffles.travelingtavern.R
import com.tiffles.travelingtavern.ui.components.PlaceholderScreen
import com.tiffles.travelingtavern.ui.theme.TravelingTavernTheme

@Composable
fun ConnectScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(label = stringResource(R.string.screen_connect), modifier = modifier)
}

@Preview(showBackground = true)
@Composable
private fun ConnectScreenPreview() {
    TravelingTavernTheme {
        ConnectScreen()
    }
}