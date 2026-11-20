package com.tiffles.travelingtavern.ui.connect

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tiffles.travelingtavern.domain.model.ConnectionResult
import com.tiffles.travelingtavern.domain.model.ServerConfig
import com.tiffles.travelingtavern.domain.model.normalizeServerUrl
import com.tiffles.travelingtavern.domain.repository.ServerConfigRepository
import com.tiffles.travelingtavern.domain.usecase.TestConnectionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ConnectUiState(
    val url: String = "",
    val loginEnabled: Boolean = false,
    val username: String = "",
    val password: String = "",
    val status: ConnectStatus = ConnectStatus.Idle
) {
    val isConnecting: Boolean
        get() = status is ConnectStatus.Connecting

    val canConnect: Boolean
        get() = url.isNotBlank() && !isConnecting
}

sealed class ConnectStatus {
    data object Idle : ConnectStatus()
    data object Connecting : ConnectStatus()

    /** The last attempt failed with [result] (never [ConnectionResult.Success]). */
    data class Failed(val result: ConnectionResult) : ConnectStatus()
}

sealed class ConnectEvent {
    data object Saved : ConnectEvent()
}

@HiltViewModel
class ConnectViewModel @Inject constructor(
    private val testConnection: TestConnectionUseCase,
    private val repository: ServerConfigRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ConnectUiState())
    val uiState: StateFlow<ConnectUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ConnectEvent>(extraBufferCapacity = 1)
    val events: SharedFlow<ConnectEvent> = _events.asSharedFlow()

    fun onUrlChange(url: String) = edit { copy(url = url) }

    fun onLoginEnabledChange(enabled: Boolean) = edit { copy(loginEnabled = enabled) }

    fun onUsernameChange(username: String) = edit { copy(username = username) }

    fun onPasswordChange(password: String) = edit { copy(password = password) }

    /**
     * Tests the entered server and, if it is SillyTavern and the login works, saves it and
     * emits [ConnectEvent.Saved]. On failure the inputs stay as they are.
     */
    fun connect() {
        val state = _uiState.value
        if (!state.canConnect) return
        _uiState.update { it.copy(status = ConnectStatus.Connecting) }
        viewModelScope.launch {
            val username = state.username.takeIf { state.loginEnabled }
            val password = state.password.takeIf { state.loginEnabled }
            val result = testConnection(rawUrl = state.url, username = username, password = password)
            val baseUrl = normalizeServerUrl(state.url)
            if (result is ConnectionResult.Success && baseUrl != null) {
                save(state, baseUrl)
            } else {
                onFailed(if (baseUrl == null) ConnectionResult.InvalidUrl else result)
            }
        }
    }

    private suspend fun save(state: ConnectUiState, baseUrl: String) {
        val username = state.username.takeIf { state.loginEnabled && it.isNotBlank() }
        val password = state.password.takeIf { state.loginEnabled && it.isNotEmpty() }
        repository.save(
            config = ServerConfig(baseUrl = baseUrl, username = username, hasPassword = password != null),
            password = password
        )
        _events.emit(ConnectEvent.Saved)
    }

    private fun onFailed(result: ConnectionResult) {
        _uiState.update {
            it.copy(
                status = ConnectStatus.Failed(result),
                // The server asked for a login the user hadn't turned on: reveal the fields.
                loginEnabled = it.loginEnabled || result is ConnectionResult.AuthRequired
            )
        }
    }

    /** Any input change clears the last error. Ignored while connecting (the fields are disabled). */
    private fun edit(transform: ConnectUiState.() -> ConnectUiState) {
        _uiState.update { if (it.isConnecting) it else it.transform().copy(status = ConnectStatus.Idle) }
    }
}
