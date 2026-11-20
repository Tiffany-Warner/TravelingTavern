package com.tiffles.travelingtavern.ui.personas.components

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.tiffles.travelingtavern.R
import com.tiffles.travelingtavern.domain.model.Persona
import com.tiffles.travelingtavern.ui.theme.TravelingTavernTheme

/**
 * A persona card's ⋯ (horizontal, user decision 2026-11-20) and the dropdown it opens, anchored
 * under it like the chat's ⋮ menu:
 * Edit, Set as default / Remove default, Delete. [expanded] is hoisted so the screen's
 * ViewModel decides which card's menu is open.
 */
@Composable
fun PersonaMenu(
    persona: Persona,
    expanded: Boolean,
    enabled: Boolean,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onToggleDefault: () -> Unit,
    onDelete: () -> Unit
) {
    Box {
        IconButton(onClick = onOpen, enabled = enabled) {
            Icon(painterResource(R.drawable.ic_more_horiz), contentDescription = stringResource(R.string.personas_more_options, persona.name))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
            MenuItem(
                label = stringResource(R.string.personas_action_edit),
                icon = painterResource(R.drawable.ic_edit),
                onClick = onEdit
            )
            MenuItem(
                label = stringResource(
                    if (persona.isDefault) R.string.personas_action_remove_default else R.string.personas_action_set_default
                ),
                icon = rememberVectorPainter(Icons.Filled.Star),
                onClick = onToggleDefault
            )
            MenuItem(
                label = stringResource(R.string.personas_action_delete),
                icon = painterResource(R.drawable.ic_delete),
                onClick = onDelete,
                isDestructive = true
            )
        }
    }
}

@Composable
private fun MenuItem(label: String, icon: Painter, onClick: () -> Unit, isDestructive: Boolean = false) {
    val color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Icon(painter = icon, contentDescription = null) },
        onClick = onClick,
        colors = MenuDefaults.itemColors(textColor = color, leadingIconColor = color)
    )
}

@Preview(name = "Menu · light", showBackground = true)
@Preview(name = "Menu · dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PersonaMenuPreview() {
    TravelingTavernTheme {
        PersonaMenu(
            persona = Persona("1-Mira.png", "Mira", "", "", null, true, true),
            expanded = true,
            enabled = true,
            onOpen = {}, onDismiss = {}, onEdit = {}, onToggleDefault = {}, onDelete = {}
        )
    }
}
