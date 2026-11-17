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
    val testState: TestState = TestState.Idle
) {
    /** Save is only allowed once the current inputs have passed a connection test. */
    val canSave: Boolean
        get() = testState is TestState.Result && testState.result is ConnectionResult.Success

    val isTesting: Boolean
        get() = testState is TestState.Testing
}

sealed class TestState {
    data object Idle : TestState()
    data object Testing : TestState()
    data class Result(val result: ConnectionResult) : TestState()
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

    fun testConnection() {
        val state = _uiState.value
        if (state.isTesting) return
        _uiState.update { it.copy(testState = TestState.Testing) }
        viewModelScope.launch {
            val result = testConnection(
                rawUrl = state.url,
                username = state.username.takeIf { state.loginEnabled },
                password = state.password.takeIf { state.loginEnabled }
            )
            _uiState.update {
                it.copy(
                    testState = TestState.Result(result),
                    // The server asked for a login the user hadn't turned on: reveal the fields.
                    loginEnabled = it.loginEnabled || result is ConnectionResult.AuthRequired
                )
            }
        }
    }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        val baseUrl = normalizeServerUrl(state.url) ?: return
        val username = state.username.takeIf { state.loginEnabled && it.isNotBlank() }
        val password = state.password.takeIf { state.loginEnabled && it.isNotEmpty() }
        viewModelScope.launch {
            repository.save(
                config = ServerConfig(baseUrl = baseUrl, username = username, hasPassword = password != null),
                password = password
            )
            _events.emit(ConnectEvent.Saved)
        }
    }

    /** Any input change invalidates the last test result. */
    private fun edit(transform: ConnectUiState.() -> ConnectUiState) {
        _uiState.update { it.transform().copy(testState = TestState.Idle) }
    }
}
