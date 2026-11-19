package com.tiffles.travelingtavern.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.tiffles.travelingtavern.ui.theme.TravelingTavernTheme

/**
 * A round character avatar. The character's initial shows while the image loads and stays
 * if the image fails; it is removed once the image is in, so it can't show through.
 */
@Composable
fun CharacterAvatar(
    imageUrl: String?,
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    initialStyle: TextStyle = MaterialTheme.typography.titleMedium
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        CharacterImage(
            imageUrl = imageUrl,
            name = name,
            initialStyle = initialStyle,
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * [imageUrl] cropped to fill, with [name]'s initial in its place until it has loaded (or
 * for good, if it fails). Decorative: callers always show the name as text too.
 */
@Composable
fun CharacterImage(
    imageUrl: String?,
    name: String,
    initialStyle: TextStyle,
    modifier: Modifier = Modifier
) {
    var loaded by remember(imageUrl) { mutableStateOf(false) }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (!loaded) CharacterInitial(name = name, style = initialStyle)
        if (!imageUrl.isNullOrEmpty()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                onSuccess = { loaded = true },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** The first letter of [name], or nothing for a blank name. */
@Composable
fun CharacterInitial(name: String, style: TextStyle, modifier: Modifier = Modifier) {
    val initial = name.trim().firstOrNull()?.uppercase() ?: return
    Text(
        text = initial,
        style = style,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
private fun CharacterAvatarFallbackPreview() {
    TravelingTavernTheme {
        CharacterAvatar(imageUrl = null, name = "Seraphina")
    }
}
