package com.tiffles.travelingtavern.ui.protection

import com.tiffles.travelingtavern.domain.FakeLeaseRepository
import com.tiffles.travelingtavern.domain.model.ExtensionOutcome
import com.tiffles.travelingtavern.domain.model.LeaseStatus
import com.tiffles.travelingtavern.domain.model.ParkReason
import com.tiffles.travelingtavern.domain.repository.ExtensionRepository
import com.tiffles.travelingtavern.domain.usecase.InstallExtensionUseCase
import com.tiffles.travelingtavern.domain.usecase.UpdateExtensionUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** A8: the one-time dialog, shown once ever, and the install it starts. */
@OptIn(ExperimentalCoroutinesApi::class)
class ExtensionPromptViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val repository = FakeLeaseRepository()
    private val extensions = FakeExtensionRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository.status.value = LeaseStatus.Holder(busy = false, connected = true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(): ExtensionPromptViewModel {
        val viewModel = ExtensionPromptViewModel(
            repository,
            InstallExtensionUseCase(extensions),
            UpdateExtensionUseCase(extensions)
        )
        viewModel.uiState.launchIn(backgroundScope)
        runCurrent()
        return viewModel
    }

    @Test
    fun `due shows the prompt and marks it shown at once`() = runTest(dispatcher) {
        val viewModel = viewModel()

        repository.extensionPromptDue.value = true
        runCurrent()

        assertTrue(viewModel.uiState.value.prompt)
        assertEquals(1, repository.extensionPromptShownCalls)
    }

    @Test
    fun `Not now closes it and it never asks again`() = runTest(dispatcher) {
        val viewModel = viewModel()
        repository.extensionPromptDue.value = true
        runCurrent()

        viewModel.notNow()
        runCurrent()

        assertFalse(viewModel.uiState.value.prompt)
        assertFalse("the flag stops it being due", repository.extensionPromptDue.value)
        assertEquals(1, repository.extensionPromptShownCalls)
    }

    @Test
    fun `Install runs the install without a second confirmation`() = runTest(dispatcher) {
        val viewModel = viewModel()
        repository.extensionPromptDue.value = true
        runCurrent()

        viewModel.install()
        runCurrent()

        assertEquals(1, extensions.installs)
        assertFalse(viewModel.uiState.value.prompt)
        assertEquals(ExtensionDialog.Done(ExtensionOutcome.Installed), viewModel.uiState.value.dialog)
    }

    @Test
    fun `nothing is drawn while the scrim covers the app`() = runTest(dispatcher) {
        val viewModel = viewModel()
        repository.extensionPromptDue.value = true
        runCurrent()

        repository.status.value = LeaseStatus.Parked(ParkReason.Held)
        runCurrent()
        assertFalse(viewModel.uiState.value.prompt)

        viewModel.install()
        repository.status.value = LeaseStatus.VersionMismatch()
        runCurrent()
        assertNull(viewModel.uiState.value.dialog)
    }
}

/** Counts calls; answers with the given outcomes. */
class FakeExtensionRepository(
    private val installOutcome: ExtensionOutcome = ExtensionOutcome.Installed,
    private val updateOutcome: ExtensionOutcome = ExtensionOutcome.Updated
) : ExtensionRepository {
    var installs = 0
    var updates = 0

    override suspend fun install(): ExtensionOutcome {
        installs++
        return installOutcome
    }

    override suspend fun update(): ExtensionOutcome {
        updates++
        return updateOutcome
    }
}
