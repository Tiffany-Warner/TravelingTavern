package com.tiffles.travelingtavern.ui.lorebooks

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tiffles.travelingtavern.R
import com.tiffles.travelingtavern.ui.components.ConfirmDeleteSheet
import com.tiffles.travelingtavern.ui.components.EmptyState
import com.tiffles.travelingtavern.ui.theme.TravelingTavernTheme

@Composable
fun LorebookEntryScreen(onBack: () -> Unit, viewModel: LorebookEntryViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val failedMessage = stringResource(R.string.lorebook_action_failed)
    var deleteSheetOpen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel) { viewModel.done.collect { onBack() } }
    LaunchedEffect(viewModel) {
        viewModel.actionFailed.collect {
            deleteSheetOpen = false
            snackbar.showSnackbar(failedMessage)
        }
    }

    LorebookEntryContent(
        uiState = uiState,
        snackbar = snackbar,
        onBack = onBack,
        onFormChange = viewModel::update,
        onSave = viewModel::save,
        onDeleteClick = { deleteSheetOpen = true },
        onRetry = viewModel::retry
    )

    val editing = uiState as? LorebookEntryUiState.Editing
    if (deleteSheetOpen && editing != null) {
        ConfirmDeleteSheet(
            title = stringResource(R.string.entry_delete_title),
            message = stringResource(R.string.lorebook_delete_message),
            isWorking = editing.isWorking,
            onConfirm = viewModel::delete,
            onDismiss = { deleteSheetOpen = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LorebookEntryContent(
    uiState: LorebookEntryUiState,
    onBack: () -> Unit,
    onFormChange: (EntryForm) -> Unit,
    onSave: () -> Unit,
    onDeleteClick: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState = remember { SnackbarHostState() }
) {
    val editing = uiState as? LorebookEntryUiState.Editing
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (editing?.isNew == true) R.string.entry_new_title else R.string.entry_edit_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.chat_back))
                    }
                },
                actions = { if (editing != null && !editing.isNew) EntryOverflowMenu(onDeleteClick) }
            )
        },
        bottomBar = {
            if (editing != null) {
                // The one primary action, at the bottom where the thumb is; rides above the keyboard.
                Button(
                    onClick = onSave,
                    enabled = !editing.isWorking,
                    modifier = Modifier.fillMaxWidth().imePadding().padding(16.dp)
                ) {
                    Text(stringResource(R.string.entry_save))
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when (uiState) {
                LorebookEntryUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                LorebookEntryUiState.NotFound -> Text(
                    text = stringResource(R.string.entry_not_found),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
                LorebookEntryUiState.LoadFailed -> EmptyState(
                    message = stringResource(R.string.lorebook_load_failed),
                    actionLabel = stringResource(R.string.lorebook_retry),
                    onAction = onRetry
                )
                is LorebookEntryUiState.Editing -> EntryFields(uiState, onFormChange)
            }
        }
    }
}

@Composable
private fun EntryOverflowMenu(onDeleteClick: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.lorebook_more_options))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.entry_delete)) },
                onClick = {
                    open = false
                    onDeleteClick()
                }
            )
        }
    }
}

@Composable
private fun EntryFields(state: LorebookEntryUiState.Editing, onFormChange: (EntryForm) -> Unit) {
    val form = state.form
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        OutlinedTextField(
            value = form.title,
            onValueChange = { onFormChange(form.copy(title = it)) },
            label = { Text(stringResource(R.string.entry_title)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = form.keywords,
            onValueChange = { onFormChange(form.copy(keywords = it)) },
            label = { Text(stringResource(R.string.entry_keywords)) },
            supportingText = { Text(stringResource(R.string.entry_keywords_hint)) },
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = form.content,
            onValueChange = { onFormChange(form.copy(content = it)) },
            label = { Text(stringResource(R.string.entry_content)) },
            supportingText = state.tokenCount?.let { count -> { Text(pluralStringResource(R.plurals.entry_tokens, count, count)) } },
            minLines = CONTENT_MIN_LINES,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth()
        )
        ListItem(
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            headlineContent = { Text(stringResource(R.string.entry_enabled)) },
            trailingContent = { Switch(checked = form.enabled, onCheckedChange = { onFormChange(form.copy(enabled = it)) }) }
        )
        Text(
            text = stringResource(R.string.entry_more_settings),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private const val CONTENT_MIN_LINES = 6

@Preview(name = "Entry · light", showBackground = true)
@Preview(name = "Entry · dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun LorebookEntryContentPreview() {
    TravelingTavernTheme {
        LorebookEntryContent(
            uiState = LorebookEntryUiState.Editing(
                form = EntryForm("Eldoria", "eldoria, wood, forest", "A magical forest in the far west.", true),
                isNew = false,
                tokenCount = 9
            ),
            onBack = {}, onFormChange = {}, onSave = {}, onDeleteClick = {}, onRetry = {}
        )
    }
}

@Preview(name = "Not found", showBackground = true)
@Composable
private fun LorebookEntryNotFoundPreview() {
    TravelingTavernTheme {
        LorebookEntryContent(
            uiState = LorebookEntryUiState.NotFound,
            onBack = {}, onFormChange = {}, onSave = {}, onDeleteClick = {}, onRetry = {}
        )
    }
}
