package com.tiffles.travelingtavern.ui.lorebooks

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tiffles.travelingtavern.R
import com.tiffles.travelingtavern.domain.model.CharacterSummary
import com.tiffles.travelingtavern.domain.model.LorebookEntry
import com.tiffles.travelingtavern.ui.components.ConfirmDeleteSheet
import com.tiffles.travelingtavern.ui.components.ConnectionBanner
import com.tiffles.travelingtavern.ui.components.EmptyState
import com.tiffles.travelingtavern.ui.theme.TravelingTavernTheme

@Composable
fun LorebookDetailScreen(
    onBack: () -> Unit,
    onOpenEntry: (lorebook: String, uid: Int?) -> Unit,
    onOpenServerSettings: () -> Unit,
    viewModel: LorebookDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val failedMessage = stringResource(R.string.lorebook_action_failed)
    var deleteSheetOpen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) { viewModel.closed.collect { onBack() } }
    LaunchedEffect(viewModel) {
        viewModel.actionFailed.collect {
            deleteSheetOpen = false
            snackbar.showSnackbar(failedMessage)
        }
    }

    LorebookDetailContent(
        name = viewModel.name,
        uiState = uiState,
        snackbar = snackbar,
        onBack = onBack,
        onGlobalChange = viewModel::setGlobal,
        onEntryClick = { onOpenEntry(viewModel.name, it) },
        onAddEntry = { onOpenEntry(viewModel.name, null) },
        onDeleteClick = { deleteSheetOpen = true },
        onRetry = viewModel::retry,
        onOpenServerSettings = onOpenServerSettings
    )

    val success = uiState as? LorebookDetailUiState.Success
    if (deleteSheetOpen && success != null) {
        ConfirmDeleteSheet(
            title = stringResource(R.string.lorebook_delete_title, success.name),
            message = stringResource(R.string.lorebook_delete_message),
            details = deleteDetails(success),
            isWorking = success.isWorking,
            onConfirm = viewModel::delete,
            onDismiss = { deleteSheetOpen = false }
        )
    }
}

/** "Seraphina, Bob will keep a link to this name…" when it's attached to anyone. */
@Composable
private fun deleteDetails(state: LorebookDetailUiState.Success): List<String> {
    val names = linkedNames(state.characters, state.personaNames)
    return if (names.isEmpty()) emptyList() else listOf(stringResource(R.string.lorebook_delete_linked, names))
}

@Composable
private fun linkedNames(characters: List<CharacterSummary>, personaNames: List<String>): String {
    val currentPersona = stringResource(R.string.lorebook_current_persona)
    return (characters.map { it.name } + personaNames.map { it.ifBlank { currentPersona } }).joinToString(", ")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LorebookDetailContent(
    name: String,
    uiState: LorebookDetailUiState,
    onBack: () -> Unit,
    onGlobalChange: (Boolean) -> Unit,
    onEntryClick: (uid: Int?) -> Unit,
    onAddEntry: () -> Unit,
    onDeleteClick: () -> Unit,
    onRetry: () -> Unit,
    onOpenServerSettings: () -> Unit,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState = remember { SnackbarHostState() }
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.chat_back))
                    }
                },
                actions = { if (uiState is LorebookDetailUiState.Success) OverflowMenu(onDeleteClick) }
            )
        },
        floatingActionButton = {
            if (uiState is LorebookDetailUiState.Success) {
                FloatingActionButton(onClick = onAddEntry) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.lorebook_add_entry))
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            DetailBody(uiState, onGlobalChange, onEntryClick, onAddEntry, onRetry, onOpenServerSettings)
        }
    }
}

@Composable
private fun OverflowMenu(onDeleteClick: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.lorebook_more_options))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.lorebook_delete)) },
                onClick = {
                    open = false
                    onDeleteClick()
                }
            )
        }
    }
}

@Composable
private fun DetailBody(
    uiState: LorebookDetailUiState,
    onGlobalChange: (Boolean) -> Unit,
    onEntryClick: (Int?) -> Unit,
    onAddEntry: () -> Unit,
    onRetry: () -> Unit,
    onOpenServerSettings: () -> Unit
) {
    when (uiState) {
        LorebookDetailUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        is LorebookDetailUiState.Error -> ConnectionBanner(
            error = uiState.error,
            onRetry = onRetry,
            onOpenServerSettings = onOpenServerSettings,
            modifier = Modifier.padding(16.dp)
        )
        LorebookDetailUiState.NotFound -> MessageText(stringResource(R.string.lorebook_not_found))
        LorebookDetailUiState.LoadFailed -> EmptyState(
            message = stringResource(R.string.lorebook_load_failed),
            actionLabel = stringResource(R.string.lorebook_retry),
            onAction = onRetry
        )
        is LorebookDetailUiState.Success -> EntryList(uiState, onGlobalChange, onEntryClick, onAddEntry)
    }
}

@Composable
private fun MessageText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(16.dp)
    )
}

@Composable
private fun EntryList(
    state: LorebookDetailUiState.Success,
    onGlobalChange: (Boolean) -> Unit,
    onEntryClick: (Int?) -> Unit,
    onAddEntry: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        // Bottom padding keeps the last entry clear of the FAB.
        contentPadding = PaddingValues(bottom = 88.dp)
    ) {
        item(key = "global") { GlobalRow(state, onGlobalChange) }
        if (state.entries.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    message = stringResource(R.string.lorebook_no_entries),
                    actionLabel = stringResource(R.string.lorebook_add_entry),
                    onAction = onAddEntry,
                    modifier = Modifier.padding(top = 32.dp)
                )
            }
        }
        items(state.entries, key = { it.uid ?: -1 }) { entry ->
            EntryRow(entry = entry, onClick = { onEntryClick(entry.uid) })
        }
    }
}

@Composable
private fun GlobalRow(state: LorebookDetailUiState.Success, onGlobalChange: (Boolean) -> Unit) {
    val linked = linkedNames(state.characters, state.personaNames)
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        headlineContent = { Text(stringResource(R.string.lorebook_global)) },
        supportingContent = if (linked.isEmpty()) null else {
            { Text(stringResource(R.string.lorebook_attached_to, linked)) }
        },
        trailingContent = {
            Switch(checked = state.isGlobal, onCheckedChange = onGlobalChange, enabled = !state.isWorking)
        }
    )
}

@Composable
private fun EntryRow(entry: LorebookEntry, onClick: () -> Unit) {
    ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = {
            Text(
                text = entry.title.ifBlank { stringResource(R.string.lorebook_untitled_entry) },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = { EntrySummary(entry) },
        trailingContent = if (entry.enabled) null else {
            { Text(stringResource(R.string.lorebook_entry_off), style = MaterialTheme.typography.labelMedium) }
        }
    )
}

@Composable
private fun EntrySummary(entry: LorebookEntry) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (entry.keywords.isNotEmpty()) KeywordChips(entry.keywords)
        if (entry.content.isNotBlank()) {
            Text(
                text = entry.content.replace(WHITESPACE, " ").trim(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Keywords as small neutral pills (labels, not buttons). */
@Composable
private fun KeywordChips(keywords: List<String>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        maxLines = 2
    ) {
        keywords.forEach { keyword ->
            Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                Text(
                    text = keyword,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
    }
}

private val WHITESPACE = Regex("\\s+")

private val previewEntries = listOf(
    LorebookEntry(0, "Eldoria", listOf("eldoria", "wood", "forest"), "A magical forest in the far west.", true),
    LorebookEntry(1, "", listOf("glade"), "The Glade is a sunlit clearing.", false)
)

@Preview(name = "Detail · light", showBackground = true)
@Preview(name = "Detail · dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LorebookDetailContentPreview() {
    TravelingTavernTheme {
        LorebookDetailContent(
            name = "Eldoria",
            uiState = LorebookDetailUiState.Success(
                name = "Eldoria",
                entries = previewEntries,
                isGlobal = false,
                characters = listOf(CharacterSummary("Seraphina.png", "Seraphina", "", "", null)),
                personaNames = emptyList(),
                isWorking = false
            ),
            onBack = {}, onGlobalChange = {}, onEntryClick = {}, onAddEntry = {}, onDeleteClick = {},
            onRetry = {}, onOpenServerSettings = {}
        )
    }
}

@Preview(name = "No entries", showBackground = true)
@Composable
private fun LorebookDetailEmptyPreview() {
    TravelingTavernTheme {
        LorebookDetailContent(
            name = "New lorebook",
            uiState = LorebookDetailUiState.Success("New lorebook", emptyList(), false, emptyList(), emptyList(), false),
            onBack = {}, onGlobalChange = {}, onEntryClick = {}, onAddEntry = {}, onDeleteClick = {},
            onRetry = {}, onOpenServerSettings = {}
        )
    }
}
