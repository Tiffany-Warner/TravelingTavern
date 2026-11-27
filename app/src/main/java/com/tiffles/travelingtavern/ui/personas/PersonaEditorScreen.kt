package com.tiffles.travelingtavern.ui.personas

import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tiffles.travelingtavern.R
import com.tiffles.travelingtavern.domain.repository.PersonaFailure
import com.tiffles.travelingtavern.ui.components.CharacterImage
import com.tiffles.travelingtavern.ui.components.EmptyState
import com.tiffles.travelingtavern.ui.theme.TravelingTavernTheme

@Composable
fun PersonaEditorScreen(onBack: () -> Unit, viewModel: PersonaEditorViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val pickPicture = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.setPicture(uri.toString())
    }

    LaunchedEffect(viewModel) { viewModel.done.collect { onBack() } }
    LaunchedEffect(viewModel) {
        viewModel.failed.collect { failure ->
            val message = if (failure == PersonaFailure.PictureUnreadable) R.string.persona_picture_unreadable
            else R.string.personas_action_failed
            snackbar.showSnackbar(resources.getString(message))
        }
    }

    PersonaEditorContent(
        uiState = uiState,
        snackbar = snackbar,
        onBack = onBack,
        onFormChange = viewModel::update,
        onPickPicture = {
            pickPicture.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        onSave = viewModel::save,
        onRetry = viewModel::retry
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonaEditorContent(
    uiState: PersonaEditorUiState,
    onBack: () -> Unit,
    onFormChange: (PersonaForm) -> Unit,
    onPickPicture: () -> Unit,
    onSave: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    snackbar: SnackbarHostState = remember { SnackbarHostState() }
) {
    val editing = uiState as? PersonaEditorUiState.Editing
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(if (editing?.isNew == true) R.string.persona_new_title else R.string.persona_edit_title))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.chat_back))
                    }
                }
            )
        },
        bottomBar = {
            if (editing != null) {
                // The one primary action, at the bottom where the thumb is; rides above the keyboard.
                Button(
                    onClick = onSave,
                    enabled = editing.canSave,
                    modifier = Modifier.fillMaxWidth().imePadding().padding(16.dp)
                ) {
                    Text(stringResource(R.string.persona_save))
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when (uiState) {
                PersonaEditorUiState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                PersonaEditorUiState.NotFound -> Text(
                    text = stringResource(R.string.persona_not_found),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp)
                )
                PersonaEditorUiState.LoadFailed -> EmptyState(
                    message = stringResource(R.string.persona_load_failed),
                    actionLabel = stringResource(R.string.persona_retry),
                    onAction = onRetry
                )
                is PersonaEditorUiState.Editing -> PersonaFields(uiState, onFormChange, onPickPicture)
            }
        }
    }
}

@Composable
private fun PersonaFields(
    state: PersonaEditorUiState.Editing,
    onFormChange: (PersonaForm) -> Unit,
    onPickPicture: () -> Unit
) {
    val form = state.form
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        PictureField(
            imageUrl = state.newPictureUri ?: state.pictureUrl,
            name = form.name,
            onClick = onPickPicture,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
        OutlinedTextField(
            value = form.name,
            onValueChange = { onFormChange(form.copy(name = it)) },
            label = { Text(stringResource(R.string.persona_name)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = form.title,
            onValueChange = { onFormChange(form.copy(title = it)) },
            label = { Text(stringResource(R.string.persona_title_field)) },
            supportingText = { Text(stringResource(R.string.persona_title_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = form.description,
            onValueChange = { onFormChange(form.copy(description = it)) },
            label = { Text(stringResource(R.string.persona_description)) },
            supportingText = state.tokenCount?.let { count -> { Text(pluralStringResource(R.plurals.entry_tokens, count, count)) } },
            minLines = DESCRIPTION_MIN_LINES,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = stringResource(R.string.persona_more_settings),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** The persona's 3:4 picture with a "Change picture" button; tapping either picks a new one. */
@Composable
private fun PictureField(imageUrl: String?, name: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .width(PICTURE_WIDTH)
                .aspectRatio(PICTURE_RATIO)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(role = Role.Image, onClickLabel = stringResource(R.string.persona_picture), onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            CharacterImage(
                imageUrl = imageUrl,
                name = name,
                initialStyle = MaterialTheme.typography.displaySmall,
                modifier = Modifier.fillMaxSize()
            )
        }
        TextButton(onClick = onClick) { Text(stringResource(R.string.persona_picture)) }
    }
}

private val PICTURE_WIDTH = 120.dp
private const val PICTURE_RATIO = 3f / 4f
private const val DESCRIPTION_MIN_LINES = 6

@Preview(name = "Editor · light", showBackground = true)
@Preview(name = "Editor · dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PersonaEditorPreview() {
    TravelingTavernTheme {
        val form = PersonaForm("Mira", "Traveling bard", "A cheerful bard who collects stories.")
        PersonaEditorContent(
            uiState = PersonaEditorUiState.Editing(form = form, original = form, isNew = false, tokenCount = 8),
            onBack = {}, onFormChange = {}, onPickPicture = {}, onSave = {}, onRetry = {}
        )
    }
}

@Preview(name = "Editor · new", showBackground = true)
@Composable
private fun PersonaEditorNewPreview() {
    TravelingTavernTheme {
        PersonaEditorContent(
            uiState = PersonaEditorUiState.Editing(PersonaForm(), PersonaForm(), isNew = true),
            onBack = {}, onFormChange = {}, onPickPicture = {}, onSave = {}, onRetry = {}
        )
    }
}
