package com.tiffles.travelingtavern.ui.connect

import com.tiffles.travelingtavern.domain.model.ConnectionResult
import com.tiffles.travelingtavern.domain.model.ServerConfig
import com.tiffles.travelingtavern.domain.repository.ServerConfigRepository
import com.tiffles.travelingtavern.domain.usecase.TestConnectionUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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

    @Test
    fun `save is disabled until a successful test`() = runTest {
        viewModel.onUrlChange("100.64.0.2:8000")
        assertFalse(viewModel.uiState.value.canSave)

        coEvery { testConnection("100.64.0.2:8000", null, null) } returns ConnectionResult.Success
        viewModel.testConnection()

        assertTrue(viewModel.uiState.value.canSave)
        assertEquals(TestState.Result(ConnectionResult.Success), viewModel.uiState.value.testState)
    }

    @Test
    fun `editing any field invalidates the last result`() = runTest {
        viewModel.onUrlChange("100.64.0.2:8000")
        coEvery { testConnection(any(), any(), any()) } returns ConnectionResult.Success
        viewModel.testConnection()

        viewModel.onUrlChange("100.64.0.2:8001")

        assertEquals(TestState.Idle, viewModel.uiState.value.testState)
        assertFalse(viewModel.uiState.value.canSave)
    }

    @Test
    fun `auth required reveals the login fields`() = runTest {
        viewModel.onUrlChange("100.64.0.2:8000")
        coEvery { testConnection(any(), null, null) } returns ConnectionResult.AuthRequired

        viewModel.testConnection()

        assertTrue(viewModel.uiState.value.loginEnabled)
        assertFalse(viewModel.uiState.value.canSave)
    }

    @Test
    fun `credentials are only sent when login is enabled`() = runTest {
        viewModel.onUrlChange("100.64.0.2:8000")
        viewModel.onUsernameChange("user")
        viewModel.onPasswordChange("pass")
        coEvery { testConnection("100.64.0.2:8000", null, null) } returns ConnectionResult.Success

        viewModel.testConnection()

        coVerify(exactly = 1) { testConnection("100.64.0.2:8000", null, null) }
    }

    @Test
    fun `save persists the normalized config and emits Saved`() = runTest {
        viewModel.onUrlChange("100.64.0.2:8000/")
        viewModel.onLoginEnabledChange(true)
        viewModel.onUsernameChange("user")
        viewModel.onPasswordChange("pass")
        coEvery { testConnection(any(), "user", "pass") } returns ConnectionResult.Success
        viewModel.testConnection()

        val events = mutableListOf<ConnectEvent>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) { viewModel.events.collect { events += it } }
        viewModel.save()
        runCurrent()
        collector.cancel()

        assertEquals(listOf(ConnectEvent.Saved), events)
        coVerify {
            repository.save(
                ServerConfig(baseUrl = "http://100.64.0.2:8000", username = "user", hasPassword = true),
                "pass"
            )
        }
    }

    @Test
    fun `save does nothing without a successful test`() = runTest {
        viewModel.onUrlChange("100.64.0.2:8000")

        viewModel.save()

        coVerify(exactly = 0) { repository.save(any(), any()) }
    }
}
