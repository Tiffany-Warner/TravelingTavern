package com.tiffles.travelingtavern.ui.components

import android.content.res.Configuration
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tiffles.travelingtavern.R
import com.tiffles.travelingtavern.ui.theme.TravelingTavernTheme

/**
 * The app logo in a rounded square, drawn from the launcher icon's own layers so it always
 * matches the icon. Those layers are a 108dp canvas with the logo in the middle 72dp, so they
 * are drawn at 1.5 × [size] and cropped to the middle. Decorative: no content description.
 */
@Composable
fun AppLogo(modifier: Modifier = Modifier, size: Dp = 72.dp) {
    val canvas = size * CANVAS_TO_LOGO
    Box(
        modifier = modifier.size(size).clip(RoundedCornerShape(percent = CORNER_PERCENT)),
        contentAlignment = Alignment.Center
    ) {
        Image(painter = painterResource(R.drawable.ic_launcher_background), contentDescription = null, modifier = Modifier.requiredSize(canvas))
        Image(painter = painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.requiredSize(canvas))
    }
}

/** Adaptive icon canvas (108dp) over the logo area it shows (72dp). */
private const val CANVAS_TO_LOGO = 1.5f

/** Matches the corners of the source logo (docs/brand/travelingtavern_logo_wine.svg, 224 of 1024). */
private const val CORNER_PERCENT = 22

@Preview(name = "Logo · light", showBackground = true)
@Preview(name = "Logo · dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun AppLogoPreview() {
    TravelingTavernTheme { AppLogo() }
}
