package com.tiffles.travelingtavern.ui.lease

import com.tiffles.travelingtavern.domain.FakeLeaseRepository
import com.tiffles.travelingtavern.domain.model.LeaseStatus
import com.tiffles.travelingtavern.domain.model.ParkReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LeaseGuardViewModelTest {

    private val repository = FakeLeaseRepository()
    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `every lease status maps to its row of the state table`() {
        val expected = mapOf(
            LeaseStatus.NotReported to null,
            LeaseStatus.Holder(busy = true, connected = false) to null,
            LeaseStatus.Unprotected to null,
            LeaseStatus.VersionMismatch to ScrimState.RestartNeeded,
            LeaseStatus.NoDocumentStart to ScrimState.UpdateWebView,
            LeaseStatus.Parked(ParkReason.Held) to ScrimState.InUse,
            LeaseStatus.Parked(ParkReason.Lost) to ScrimState.InUse,
            LeaseStatus.Parked(ParkReason.Claiming) to ScrimState.Claiming,
            LeaseStatus.Parked(ParkReason.WaitingForReply) to ScrimState.WaitingForReply,
            LeaseStatus.Parked(ParkReason.LostRace) to ScrimState.LostRace,
            LeaseStatus.Parked(ParkReason.Unreadable) to ScrimState.Unreadable,
            LeaseStatus.Parked(ParkReason.UnknownFormat) to ScrimState.UpdateApp,
            LeaseStatus.Parked(ParkReason.ReloadPending) to ScrimState.ReloadNeeded,
            LeaseStatus.Parked(ParkReason.Unknown("future")) to ScrimState.InUse
        )
        expected.forEach { (status, scrim) -> assertEquals(status.toString(), scrim, status.toScrimState()) }
    }

    @Test
    fun `unprotected shows the banner, with the scrim when an extension's lease parks the page`() = runTest(dispatcher) {
        val viewModel = LeaseGuardViewModel(repository)
        val collector = launch { viewModel.uiState.collect {} }

        repository.unprotected.value = true
        repository.status.value = LeaseStatus.Unprotected
        assertEquals(LeaseGuardUiState(unprotected = true), viewModel.uiState.value)

        repository.status.value = LeaseStatus.Parked(ParkReason.Held)
        assertEquals(LeaseGuardUiState(scrim = ScrimState.InUse, unprotected = true), viewModel.uiState.value)
        collector.cancel()
    }

    @Test
    fun `ui state follows the repository`() = runTest(dispatcher) {
        val viewModel = LeaseGuardViewModel(repository)
        val collector = launch { viewModel.uiState.collect {} }

        repository.status.value = LeaseStatus.Parked(ParkReason.Held)
        assertEquals(ScrimState.InUse, viewModel.uiState.value.scrim)

        repository.status.value = LeaseStatus.Holder(busy = false, connected = true)
        assertEquals(LeaseGuardUiState(), viewModel.uiState.value)
        collector.cancel()
    }

    @Test
    fun `actions reach the repository`() {
        val viewModel = LeaseGuardViewModel(repository)

        viewModel.useHere()
        viewModel.reload()
        viewModel.continueWithoutProtection()

        assertEquals(1, repository.useHereCalls)
        assertEquals(1, repository.reloadCalls)
        assertEquals(1, repository.continueCalls)
    }
}
