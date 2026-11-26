package com.tiffles.travelingtavern.ui.connect

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tiffles.travelingtavern.R
import com.tiffles.travelingtavern.domain.model.ConnectionResult
import com.tiffles.travelingtavern.ui.components.AppLogo
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
        onConnect = viewModel::connect,
        modifier = modifier
    )
}

@Composable
fun ConnectContent(
    uiState: ConnectUiState,
    onUrlChange: (String) -> Unit,
    onLoginEnabledChange: (Boolean) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier
) {
    val editable = !uiState.isConnecting
    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(24.dp))
            ConnectHeader()
            Spacer(modifier = Modifier.height(32.dp))
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ServerUrlField(
                    url = uiState.url,
                    enabled = editable,
                    isLastField = !uiState.loginEnabled,
                    onUrlChange = onUrlChange,
                    onDone = onConnect
                )
                AuthToggleRow(checked = uiState.loginEnabled, enabled = editable, onCheckedChange = onLoginEnabledChange)
                if (uiState.loginEnabled) {
                    LoginFields(
                        username = uiState.username,
                        password = uiState.password,
                        enabled = editable,
                        onUsernameChange = onUsernameChange,
                        onPasswordChange = onPasswordChange,
                        onDone = onConnect
                    )
                }
                (uiState.status as? ConnectStatus.Failed)?.let { ConnectError(result = it.result) }
            }
            Spacer(modifier = Modifier.height(24.dp))
            ConnectAction(isConnecting = uiState.isConnecting, enabled = uiState.canConnect, onConnect = onConnect)
        }
    }
}

@Composable
private fun ConnectHeader() {
    AppLogo()
    Spacer(modifier = Modifier.height(24.dp))
    Text(
        text = stringResource(R.string.connect_title),
        style = MaterialTheme.typography.headlineSmall,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = stringResource(R.string.connect_subtitle),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun ServerUrlField(
    url: String,
    enabled: Boolean,
    isLastField: Boolean,
    onUrlChange: (String) -> Unit,
    onDone: () -> Unit
) {
    OutlinedTextField(
        value = url,
        onValueChange = onUrlChange,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        label = { Text(stringResource(R.string.connect_url_label)) },
        placeholder = { Text(stringResource(R.string.connect_url_placeholder)) },
        supportingText = { Text(stringResource(R.string.connect_url_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Uri,
            imeAction = if (isLastField) ImeAction.Done else ImeAction.Next
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() })
    )
}

@Composable
private fun AuthToggleRow(checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    // The whole row toggles (one tap target), so the switch itself takes no clicks.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.connect_requires_auth),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}

@Composable
private fun LoginFields(
    username: String,
    password: String,
    enabled: Boolean,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onDone: () -> Unit
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
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() })
    )
}

/** Inline error for a failed attempt, announced by screen readers when it appears. */
@Composable
private fun ConnectError(result: ConnectionResult) {
    Surface(
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer
    ) {
        Text(
            text = stringResource(result.messageRes()),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )
    }
}

private fun ConnectionResult.messageRes(): Int = when (this) {
    // Success never shows: it saves and leaves the screen.
    ConnectionResult.Success, ConnectionResult.Unreachable -> R.string.connect_result_unreachable
    ConnectionResult.AuthRequired -> R.string.connect_result_auth_required
    ConnectionResult.AuthFailed -> R.string.connect_result_auth_failed
    ConnectionResult.NotSillyTavern -> R.string.connect_result_not_sillytavern
    ConnectionResult.InvalidUrl -> R.string.connect_result_invalid_url
    ConnectionResult.ServerNotAllowed -> R.string.server_not_allowed
}

/** The full-width Connect button, or a spinner with "Connecting…" below it while connecting. */
@Composable
private fun ConnectAction(isConnecting: Boolean, enabled: Boolean, onConnect: () -> Unit) {
    // Same height either way, so nothing below jumps when the spinner replaces the button.
    Box(modifier = Modifier.fillMaxWidth().heightIn(min = ACTION_HEIGHT), contentAlignment = Alignment.Center) {
        if (isConnecting) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(32.dp))
                Text(
                    text = stringResource(R.string.connect_connecting),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Button(onClick = onConnect, enabled = enabled, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(stringResource(R.string.connect_action), style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

private val ACTION_HEIGHT = 80.dp

private val previewActions: @Composable (ConnectUiState) -> Unit = { state ->
    TravelingTavernTheme {
        ConnectContent(
            uiState = state,
            onUrlChange = {}, onLoginEnabledChange = {}, onUsernameChange = {}, onPasswordChange = {}, onConnect = {}
        )
    }
}

@Preview(name = "Empty · light", showBackground = true)
@Preview(name = "Empty · dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ConnectContentEmptyPreview() = previewActions(ConnectUiState())

@Preview(name = "Authentication + error", showBackground = true)
@Composable
private fun ConnectContentErrorPreview() = previewActions(
    ConnectUiState(
        url = "http://192.0.2.1:8000",
        loginEnabled = true,
        username = "user",
        password = "pass",
        status = ConnectStatus.Failed(ConnectionResult.AuthFailed)
    )
)

@Preview(name = "Connecting", showBackground = true)
@Composable
private fun ConnectContentConnectingPreview() =
    previewActions(ConnectUiState(url = "desktop.tail1234.ts.net:8000", status = ConnectStatus.Connecting))
