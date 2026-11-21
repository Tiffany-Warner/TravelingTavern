package com.tiffles.travelingtavern.ui.connect

import com.tiffles.travelingtavern.domain.model.ConnectionResult
import com.tiffles.travelingtavern.domain.model.ServerConfig
import com.tiffles.travelingtavern.domain.repository.ServerConfigRepository
import com.tiffles.travelingtavern.domain.usecase.TestConnectionUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectViewModelTest {

    private val testConnection: TestConnectionUseCase = mockk()
    private val repository: ServerConfigRepository = mockk(relaxed = true)
    private lateinit var viewModel: ConnectViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = ConnectViewModel(testConnection, repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.collectEvents(): List<ConnectEvent> {
        val events = mutableListOf<ConnectEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.toList(events) }
        return events
    }

    @Test
    fun `connect is disabled until there is an address`() {
        assertFalse(viewModel.uiState.value.canConnect)

        viewModel.onUrlChange("192.0.2.1:8000")

        assertTrue(viewModel.uiState.value.canConnect)
    }

    @Test
    fun `success saves the normalized config and emits Saved`() = runTest {
        val events = collectEvents()
        viewModel.onUrlChange("192.0.2.1:8000/")
        viewModel.onLoginEnabledChange(true)
        viewModel.onUsernameChange("user")
        viewModel.onPasswordChange("pass")
        coEvery { testConnection(any(), "user", "pass") } returns ConnectionResult.Success

        viewModel.connect()

        assertEquals(listOf(ConnectEvent.Saved), events)
        coVerify {
            repository.save(
                ServerConfig(baseUrl = "http://192.0.2.1:8000", username = "user", hasPassword = true),
                "pass"
            )
        }
    }

    @Test
    fun `shows connecting while the test runs and ignores repeat taps`() = runTest {
        val gate = CompletableDeferred<ConnectionResult>()
        viewModel.onUrlChange("192.0.2.1:8000")
        coEvery { testConnection(any(), any(), any()) } coAnswers { gate.await() }

        viewModel.connect()
        viewModel.connect()
        viewModel.onUrlChange("ignored while connecting")

        assertEquals(ConnectStatus.Connecting, viewModel.uiState.value.status)
        assertEquals("192.0.2.1:8000", viewModel.uiState.value.url)
        coVerify(exactly = 1) { testConnection(any(), any(), any()) }
        gate.complete(ConnectionResult.Unreachable)
        assertEquals(ConnectStatus.Failed(ConnectionResult.Unreachable), viewModel.uiState.value.status)
    }

    @Test
    fun `failure shows the error, keeps the inputs and saves nothing`() = runTest {
        val events = collectEvents()
        viewModel.onUrlChange("192.0.2.1:8000")
        viewModel.onLoginEnabledChange(true)
        viewModel.onUsernameChange("user")
        viewModel.onPasswordChange("wrong")
        coEvery { testConnection(any(), any(), any()) } returns ConnectionResult.AuthFailed

        viewModel.connect()

        val state = viewModel.uiState.value
        assertEquals(ConnectStatus.Failed(ConnectionResult.AuthFailed), state.status)
        assertEquals(listOf("192.0.2.1:8000", "user", "wrong"), listOf(state.url, state.username, state.password))
        assertTrue(state.loginEnabled && state.canConnect)
        assertTrue(events.isEmpty())
        coVerify(exactly = 0) { repository.save(any(), any()) }
    }

    @Test
    fun `editing any field clears the error`() = runTest {
        viewModel.onUrlChange("192.0.2.1:8000")
        coEvery { testConnection(any(), any(), any()) } returns ConnectionResult.Unreachable
        viewModel.connect()

        viewModel.onUrlChange("192.0.2.1:8080")

        assertEquals(ConnectStatus.Idle, viewModel.uiState.value.status)
    }

    @Test
    fun `auth required turns on the authentication fields`() = runTest {
        viewModel.onUrlChange("192.0.2.1:8000")
        coEvery { testConnection(any(), null, null) } returns ConnectionResult.AuthRequired

        viewModel.connect()

        assertTrue(viewModel.uiState.value.loginEnabled)
        assertEquals(ConnectStatus.Failed(ConnectionResult.AuthRequired), viewModel.uiState.value.status)
    }

    @Test
    fun `credentials are only sent when authentication is on`() = runTest {
        viewModel.onUrlChange("192.0.2.1:8000")
        viewModel.onUsernameChange("user")
        viewModel.onPasswordChange("pass")
        coEvery { testConnection("192.0.2.1:8000", null, null) } returns ConnectionResult.Success

        viewModel.connect()

        coVerify(exactly = 1) { testConnection("192.0.2.1:8000", null, null) }
        coVerify { repository.save(ServerConfig("http://192.0.2.1:8000", username = null, hasPassword = false), null) }
    }
}
