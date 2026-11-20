package com.tiffles.travelingtavern.ui.personas

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tiffles.travelingtavern.R
import com.tiffles.travelingtavern.domain.model.Persona
import com.tiffles.travelingtavern.ui.components.ConfirmDeleteDialog
import com.tiffles.travelingtavern.ui.components.ConnectionBanner
import com.tiffles.travelingtavern.ui.components.EmptyState
import com.tiffles.travelingtavern.ui.personas.components.PersonaCard
import com.tiffles.travelingtavern.ui.personas.components.PersonaMenu
import com.tiffles.travelingtavern.ui.theme.TravelingTavernTheme

/** The callbacks [PersonasContent] needs, grouped so previews and tests stay readable. */
data class PersonasActions(
    val onClose: () -> Unit = {},
    val onPick: (Persona) -> Unit = {},
    val onMore: (Persona) -> Unit = {},
    val onCreate: () -> Unit = {},
    val onEdit: (Persona) -> Unit = {},
    val onToggleDefault: (Persona) -> Unit = {},
    val onDelete: (Persona) -> Unit = {},
    val onDismissActions: () -> Unit = {},
    val onConfirmDelete: () -> Unit = {},
    val onDismissDelete: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onOpenServerSettings: () -> Unit = {}
)

@Composable
fun PersonasScreen(
    onClose: () -> Unit,
    onOpenEditor: (avatarId: String?) -> Unit,
    onOpenServerSettings: () -> Unit,
    viewModel: PersonasViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                PersonasEvent.Close -> onClose()
                PersonasEvent.ActionFailed -> snackbar.showSnackbar(context.getString(R.string.personas_action_failed))
            }
        }
    }
    PersonasContent(
        uiState = uiState,
        snackbar = snackbar,
        actions = PersonasActions(
            onClose = onClose,
            onPick = viewModel::pick,
            onMore = viewModel::openActions,
            onCreate = { onOpenEditor(null) },
            onEdit = { persona ->
                viewModel.dismissActions()
                onOpenEditor(persona.avatarId)
            },
            onToggleDefault = viewModel::toggleDefault,
            onDelete = viewModel::askDelete,
            onDismissActions = viewModel::dismissActions,
            onConfirmDelete = viewModel::confirmDelete,
            onDismissDelete = viewModel::dismissDelete,
            onRetry = viewModel::retry,
            onOpenServerSettings = onOpenServerSettings
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonasContent(
    uiState: PersonasUiState,
    actions: PersonasActions,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState = remember { SnackbarHostState() }
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.personas_title)) },
                navigationIcon = {
                    IconButton(onClick = actions.onClose) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.personas_close))
                    }
                }
            )
        },
        bottomBar = {
            if (uiState is PersonasUiState.Success) {
                // The screen's one primary action, at the bottom where the thumb is.
                Button(onClick = actions.onCreate, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text(stringResource(R.string.personas_create))
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when (uiState) {
                PersonasUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                is PersonasUiState.Error -> ConnectionBanner(
                    error = uiState.error,
                    onRetry = actions.onRetry,
                    onOpenServerSettings = actions.onOpenServerSettings
                )
                PersonasUiState.Empty -> EmptyState(
                    message = stringResource(R.string.personas_empty),
                    actionLabel = stringResource(R.string.personas_create),
                    onAction = actions.onCreate
                )
                is PersonasUiState.Success -> PersonaList(uiState, actions)
            }
        }
    }
    (uiState as? PersonasUiState.Success)?.let { DeleteConfirmation(it, actions) }
}

@Composable
private fun PersonaList(state: PersonasUiState.Success, actions: PersonasActions) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text(
                text = stringResource(R.string.personas_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        items(state.personas, key = { it.avatarId }) { persona ->
            PersonaCard(
                persona = persona,
                enabled = !state.isWorking,
                onClick = { actions.onPick(persona) }
            ) {
                PersonaMenu(
                    persona = persona,
                    expanded = state.actionsFor?.avatarId == persona.avatarId,
                    enabled = !state.isWorking,
                    onOpen = { actions.onMore(persona) },
                    onDismiss = actions.onDismissActions,
                    onEdit = { actions.onEdit(persona) },
                    onToggleDefault = { actions.onToggleDefault(persona) },
                    onDelete = { actions.onDelete(persona) }
                )
            }
        }
    }
}

/** Delete asks first, in the same alert dialog as deleting a chat (user decision, 2026-11-20). */
@Composable
private fun DeleteConfirmation(state: PersonasUiState.Success, actions: PersonasActions) {
    state.confirmDelete?.let { persona ->
        ConfirmDeleteDialog(
            title = stringResource(R.string.personas_delete_title, persona.name),
            warning = stringResource(R.string.personas_delete_warning),
            message = stringResource(R.string.personas_delete_message),
            confirmLabel = stringResource(R.string.personas_action_delete),
            isWorking = state.isWorking,
            onConfirm = actions.onConfirmDelete,
            onDismiss = actions.onDismissDelete
        )
    }
}

internal val previewPersonas = listOf(
    Persona("1-Mira.png", "Mira", "Traveling bard", "A cheerful bard who collects stories from every town she passes through.", null, isActive = true, isDefault = true),
    Persona("2-Rowan.png", "Rowan", "", "Quiet, careful, and always a little tired.", null, isActive = false, isDefault = false)
)

@Preview(name = "Personas · light", showBackground = true)
@Preview(name = "Personas · dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PersonasContentPreview() {
    TravelingTavernTheme {
        PersonasContent(uiState = PersonasUiState.Success(previewPersonas, inChat = true), actions = PersonasActions())
    }
}

@Preview(name = "Personas · empty", showBackground = true)
@Composable
private fun PersonasEmptyPreview() {
    TravelingTavernTheme {
        PersonasContent(uiState = PersonasUiState.Empty, actions = PersonasActions())
    }
}
