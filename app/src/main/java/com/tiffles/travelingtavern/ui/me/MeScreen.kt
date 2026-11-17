package com.tiffles.travelingtavern.ui.me

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.tiffles.travelingtavern.R
import com.tiffles.travelingtavern.ui.components.PlaceholderScreen
import com.tiffles.travelingtavern.ui.theme.TravelingTavernTheme

@Composable
fun MeScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(label = stringResource(R.string.tab_me), modifier = modifier)
}

@Preview(showBackground = true)
@Composable
private fun MeScreenPreview() {
    TravelingTavernTheme {
        MeScreen()
    }
}