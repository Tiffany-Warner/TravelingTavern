package com.tiffles.travelingtavern.ui.lorebooks.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tiffles.travelingtavern.R
import com.tiffles.travelingtavern.domain.usecase.LorebookNameCheck
import com.tiffles.travelingtavern.ui.lorebooks.CreateLorebookState
import com.tiffles.travelingtavern.ui.theme.TravelingTavernTheme

/** Name field + Create. Create stays disabled until the name is non-empty and not taken. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateLorebookSheet(
    state: CreateLorebookState,
    onNameChange: (String) -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        CreateLorebookForm(state = state, onNameChange = onNameChange, onCreate = onCreate)
    }
}

@Composable
private fun CreateLorebookForm(
    state: CreateLorebookState,
    onNameChange: (String) -> Unit,
    onCreate: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 24.dp).imePadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(stringResource(R.string.lorebooks_new_title), style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = state.name,
            onValueChange = onNameChange,
            label = { Text(stringResource(R.string.lorebooks_name)) },
            singleLine = true,
            isError = state.check == LorebookNameCheck.Taken || state.failed,
            supportingText = createMessage(state)?.let { message -> { Text(message) } },
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onCreate() }),
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
        )
        Button(
            onClick = onCreate,
            enabled = state.canCreate,
            modifier = Modifier.align(Alignment.End)
        ) {
            Text(stringResource(R.string.lorebooks_create_action))
        }
    }
}

@Composable
private fun createMessage(state: CreateLorebookState): String? = when {
    state.check == LorebookNameCheck.Taken -> stringResource(R.string.lorebooks_name_taken)
    state.failed -> stringResource(R.string.lorebooks_create_failed)
    else -> null
}

@Preview(showBackground = true)
@Composable
private fun CreateLorebookFormTakenPreview() {
    TravelingTavernTheme {
        CreateLorebookForm(
            state = CreateLorebookState(name = "eldoria", check = LorebookNameCheck.Taken),
            onNameChange = {},
            onCreate = {}
        )
    }
}
