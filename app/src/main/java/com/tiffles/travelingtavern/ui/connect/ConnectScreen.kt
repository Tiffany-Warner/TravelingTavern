package com.tiffles.travelingtavern.ui.connect

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.FilledTonalButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tiffles.travelingtavern.R
import com.tiffles.travelingtavern.domain.model.ConnectionResult
import com.tiffles.travelingtavern.ui.theme.TravelingTavernTheme

@Composable
fun ConnectScreen(
    onSaved: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ConnectViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                ConnectEvent.Saved -> onSaved()
            }
        }
    }

    ConnectContent(
        uiState = uiState,
        onUrlChange = viewModel::onUrlChange,
        onLoginEnabledChange = viewModel::onLoginEnabledChange,
        onUsernameChange = viewModel::onUsernameChange,
        onPasswordChange = viewModel::onPasswordChange,
        onTest = viewModel::testConnection,
        onSave = viewModel::save,
        modifier = modifier
    )
}

@Composable
private fun ConnectContent(
    uiState: ConnectUiState,
    onUrlChange: (String) -> Unit,
    onLoginEnabledChange: (Boolean) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTest: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.connect_title),
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = stringResource(R.string.connect_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            ServerUrlField(url = uiState.url, enabled = !uiState.isTesting, onUrlChange = onUrlChange)

            LoginToggleRow(
                checked = uiState.loginEnabled,
                enabled = !uiState.isTesting,
                onCheckedChange = onLoginEnabledChange
            )
            if (uiState.loginEnabled) {
                LoginFields(
                    username = uiState.username,
                    password = uiState.password,
                    enabled = !uiState.isTesting,
                    onUsernameChange = onUsernameChange,
                    onPasswordChange = onPasswordChange
                )
            }

            TestResultMessage(testState = uiState.testState)

            Spacer(modifier = Modifier.height(8.dp))
            ActionButtons(
                isTesting = uiState.isTesting,
                canTest = uiState.url.isNotBlank(),
                canSave = uiState.canSave,
                onTest = onTest,
                onSave = onSave
            )
        }
    }
}

@Composable
private fun ServerUrlField(url: String, enabled: Boolean, onUrlChange: (String) -> Unit) {
    OutlinedTextField(
        value = url,
        onValueChange = onUrlChange,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        label = { Text(stringResource(R.string.connect_url_label)) },
        placeholder = { Text(stringResource(R.string.connect_url_placeholder)) },
        supportingText = { Text(stringResource(R.string.connect_url_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done)
    )
}

@Composable
private fun LoginToggleRow(checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = stringResource(R.string.connect_requires_login),
            style = MaterialTheme.typography.bodyLarge
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun LoginFields(
    username: String,
    password: String,
    enabled: Boolean,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit
) {
    OutlinedTextField(
        value = username,
        onValueChange = onUsernameChange,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        label = { Text(stringResource(R.string.connect_username)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
    )
    OutlinedTextField(
        value = password,
        onValueChange = onPasswordChange,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        label = { Text(stringResource(R.string.connect_password)) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done)
    )
}

@Composable
private fun TestResultMessage(testState: TestState) {
    val result = (testState as? TestState.Result)?.result ?: return
    val isSuccess = result is ConnectionResult.Success
    Text(
        text = stringResource(result.messageRes()),
        style = MaterialTheme.typography.bodyMedium,
        color = if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    )
}

private fun ConnectionResult.messageRes(): Int = when (this) {
    ConnectionResult.Success -> R.string.connect_result_success
    ConnectionResult.Unreachable -> R.string.connect_result_unreachable
    ConnectionResult.AuthRequired -> R.string.connect_result_auth_required
    ConnectionResult.AuthFailed -> R.string.connect_result_auth_failed
    ConnectionResult.NotSillyTavern -> R.string.connect_result_not_sillytavern
    ConnectionResult.InvalidUrl -> R.string.connect_result_invalid_url
}

@Composable
private fun ActionButtons(
    isTesting: Boolean,
    canTest: Boolean,
    canSave: Boolean,
    onTest: () -> Unit,
    onSave: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FilledTonalButton(
            onClick = onTest,
            enabled = canTest && !isTesting,
            modifier = Modifier.weight(1f)
        ) {
            if (isTesting) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.connect_test))
            }
        }
        Button(
            onClick = onSave,
            enabled = canSave,
            modifier = Modifier.weight(1f)
        ) {
            Text(stringResource(R.string.connect_save))
        }
    }
}

@Preview(name = "Empty · light", showBackground = true)
@Preview(name = "Empty · dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ConnectContentEmptyPreview() {
    TravelingTavernTheme {
        ConnectContent(
            uiState = ConnectUiState(),
            onUrlChange = {}, onLoginEnabledChange = {}, onUsernameChange = {},
            onPasswordChange = {}, onTest = {}, onSave = {}
        )
    }
}

@Preview(name = "Login + error", showBackground = true)
@Composable
private fun ConnectContentErrorPreview() {
    TravelingTavernTheme {
        ConnectContent(
            uiState = ConnectUiState(
                url = "http://100.64.0.2:8000",
                loginEnabled = true,
                username = "tavern",
                password = "secret",
                testState = TestState.Result(ConnectionResult.AuthFailed)
            ),
            onUrlChange = {}, onLoginEnabledChange = {}, onUsernameChange = {},
            onPasswordChange = {}, onTest = {}, onSave = {}
        )
    }
}

@Preview(name = "Success", showBackground = true)
@Composable
private fun ConnectContentSuccessPreview() {
    TravelingTavernTheme {
        ConnectContent(
            uiState = ConnectUiState(
                url = "http://100.64.0.2:8000",
                testState = TestState.Result(ConnectionResult.Success)
            ),
            onUrlChange = {}, onLoginEnabledChange = {}, onUsernameChange = {},
            onPasswordChange = {}, onTest = {}, onSave = {}
        )
    }
}

@Preview(name = "Testing", showBackground = true)
@Composable
private fun ConnectContentTestingPreview() {
    TravelingTavernTheme {
        ConnectContent(
            uiState = ConnectUiState(url = "100.64.0.2:8000", testState = TestState.Testing),
            onUrlChange = {}, onLoginEnabledChange = {}, onUsernameChange = {},
            onPasswordChange = {}, onTest = {}, onSave = {}
        )
    }
}
