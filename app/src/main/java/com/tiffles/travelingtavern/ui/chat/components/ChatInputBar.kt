package com.tiffles.travelingtavern.ui.chat.components

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tiffles.travelingtavern.R
import com.tiffles.travelingtavern.domain.model.Persona
import com.tiffles.travelingtavern.ui.theme.TravelingTavernTheme

/**
 * The Chat screen's message bar: one rounded container holding the field, with Write for me and
 * Send pinned to its bottom-right corner, and the [persona] chip below it (once known). The field
 * grows upward to [MAX_FIELD_LINES] lines and the buttons stay on its last line. While a reply is
 * generating, Stop replaces Send and Write for me is disabled. There is no mic: voice typing is
 * the keyboard's.
 */
@Composable
fun ChatInputBar(
    text: String,
    generating: Boolean,
    canSend: Boolean,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onWriteForMe: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
    persona: Persona? = null,
    onPersonaClick: () -> Unit = {}
) {
    Surface(modifier = modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            MessageContainer(text, generating, canSend, onTextChange, onSend, onWriteForMe, onStop)
            persona?.let { PersonaChip(persona = it, onClick = onPersonaClick) }
        }
    }
}

/** The rounded container: the field plus Write for me and Send / Stop. */
@Composable
private fun MessageContainer(
    text: String,
    generating: Boolean,
    canSend: Boolean,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onWriteForMe: () -> Unit,
    onStop: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        verticalAlignment = Alignment.Bottom
    ) {
        MessageField(text = text, onTextChange = onTextChange, modifier = Modifier.weight(1f))
        Row(
            modifier = Modifier.padding(end = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WriteForMeButton(enabled = !generating, onClick = onWriteForMe)
            if (generating) StopButton(onClick = onStop) else SendButton(enabled = canSend, onClick = onSend)
        }
    }
}

/** The text field itself, without its own container, so it sits inside the bar's. */
@Composable
private fun MessageField(text: String, onTextChange: (String) -> Unit, modifier: Modifier = Modifier) {
    TextField(
        value = text,
        onValueChange = onTextChange,
        modifier = modifier,
        placeholder = { Text(stringResource(R.string.chat_input_placeholder)) },
        maxLines = MAX_FIELD_LINES,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            disabledContainerColor = Color.Transparent,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent
        )
    )
}

@Composable
private fun WriteForMeButton(enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(
            painter = painterResource(R.drawable.ic_auto_fix_high),
            contentDescription = stringResource(R.string.chat_write_for_me)
        )
    }
}

@Composable
private fun SendButton(enabled: Boolean, onClick: () -> Unit) {
    FilledIconButton(onClick = onClick, enabled = enabled) {
        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.chat_send))
    }
}

@Composable
private fun StopButton(onClick: () -> Unit) {
    FilledIconButton(onClick = onClick) {
        Icon(painter = painterResource(R.drawable.ic_stop), contentDescription = stringResource(R.string.chat_stop))
    }
}

private const val MAX_FIELD_LINES = 5

@Preview(name = "Input bar · empty", showBackground = true)
@Preview(name = "Input bar · dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatInputBarEmptyPreview() {
    TravelingTavernTheme {
        ChatInputBar(text = "", generating = false, canSend = false, onTextChange = {}, onSend = {}, onWriteForMe = {}, onStop = {})
    }
}

@Preview(name = "Input bar · text and persona", showBackground = true)
@Composable
private fun ChatInputBarTextPreview() {
    TravelingTavernTheme {
        ChatInputBar(
            text = "Good morning!",
            generating = false,
            canSend = true,
            onTextChange = {},
            onSend = {},
            onWriteForMe = {},
            onStop = {},
            persona = Persona("1-Mira.png", "Mira", "", "", null, isActive = true, isDefault = false)
        )
    }
}

@Preview(name = "Input bar · several lines", showBackground = true)
@Composable
private fun ChatInputBarMultilinePreview() {
    TravelingTavernTheme {
        ChatInputBar(
            text = "Good morning!\nDid you sleep well?\nI was thinking we could go to the market today.",
            generating = false,
            canSend = true,
            onTextChange = {},
            onSend = {},
            onWriteForMe = {},
            onStop = {}
        )
    }
}

@Preview(name = "Input bar · generating", showBackground = true)
@Composable
private fun ChatInputBarGeneratingPreview() {
    TravelingTavernTheme {
        ChatInputBar(text = "", generating = true, canSend = false, onTextChange = {}, onSend = {}, onWriteForMe = {}, onStop = {})
    }
}
