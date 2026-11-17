package com.tiffles.travelingtavern.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.tiffles.travelingtavern.R

/** Inter, bundled (SIL Open Font License — see `licenses/Inter-OFL.txt`). */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold)
)

/** Material 3 type scale with every style set in Inter. */
val Typography = Typography().withFontFamily(Inter)

private fun Typography.withFontFamily(family: FontFamily): Typography = copy(
    displayLarge = displayLarge.copy(fontFamily = family),
    displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family),
    headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family),
    headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family, fontWeight = FontWeight.SemiBold),
    titleMedium = titleMedium.copy(fontFamily = family, fontWeight = FontWeight.Medium),
    titleSmall = titleSmall.copy(fontFamily = family, fontWeight = FontWeight.Medium),
    bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family),
    bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family, fontWeight = FontWeight.Medium),
    labelMedium = labelMedium.copy(fontFamily = family, fontWeight = FontWeight.Medium),
    labelSmall = labelSmall.copy(fontFamily = family, fontWeight = FontWeight.Medium)
)