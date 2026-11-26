package com.tiffles.travelingtavern.ui.lease

import com.tiffles.travelingtavern.domain.FakeLeaseRepository
import com.tiffles.travelingtavern.domain.model.LeaseAction
import com.tiffles.travelingtavern.domain.model.LeaseStatus
import com.tiffles.travelingtavern.domain.model.ParkReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LeaseGuardViewModelTest {

    private val repository = FakeLeaseRepository()
    private val dispatcher = UnconfinedTestDispatcher()
    private val damaged = LeaseStatus.Parked(
        ParkReason.Damaged,
        listOf(LeaseAction.TakeOverAnyway, LeaseAction.Refresh, LeaseAction.TurnOffProtection)
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.collecting(): LeaseGuardViewModel {
        val viewModel = LeaseGuardViewModel(repository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        return viewModel
    }

    @Test
    fun `every lease status maps to its row of the state table`() {
        val expected = mapOf(
            LeaseStatus.NotReported to null,
            LeaseStatus.Holder(busy = true, connected = false) to null,
            LeaseStatus.Unprotected to null,
            LeaseStatus.Off to null,
            LeaseStatus.VersionMismatch() to ScrimState.RestartNeeded,
            LeaseStatus.NoDocumentStart to ScrimState.UpdateWebView,
            LeaseStatus.Parked(ParkReason.Held) to ScrimState.InUse,
            LeaseStatus.Parked(ParkReason.Lost) to ScrimState.InUse,
            LeaseStatus.Parked(ParkReason.Claiming) to ScrimState.Claiming,
            LeaseStatus.Parked(ParkReason.WaitingForReply) to ScrimState.WaitingForReply,
            LeaseStatus.Parked(ParkReason.LostRace) to ScrimState.LostRace,
            LeaseStatus.Parked(ParkReason.Unreadable) to ScrimState.Unreadable,
            LeaseStatus.Parked(ParkReason.Damaged) to ScrimState.Damaged,
            LeaseStatus.Parked(ParkReason.Error) to ScrimState.Error,
            LeaseStatus.Parked(ParkReason.UnknownFormat) to ScrimState.UpdateApp,
            LeaseStatus.Parked(ParkReason.ReloadPending) to ScrimState.ReloadNeeded,
            LeaseStatus.Parked(ParkReason.Unknown("future")) to ScrimState.UnknownState
        )
        expected.forEach { (status, scrim) -> assertEquals(status.toString(), scrim, status.toScrimState()) }
    }

    @Test
    fun `the scrim shows the status's actions in order`() = runTest(dispatcher) {
        val viewModel = collecting()

        repository.status.value = damaged
        assertEquals(damaged.actions, viewModel.uiState.value.actions)

        val mismatch = LeaseStatus.VersionMismatch(listOf(LeaseAction.Refresh, LeaseAction.ContinueWithoutProtection))
        repository.status.value = mismatch
        assertEquals(mismatch.actions, viewModel.uiState.value.actions)
    }

    @Test
    fun `unprotected shows the banner, with the scrim when an extension's lease parks the page`() = runTest(dispatcher) {
        val viewModel = collecting()

        repository.unprotected.value = true
        repository.status.value = LeaseStatus.Unprotected
        assertEquals(LeaseGuardUiState(unprotected = true), viewModel.uiState.value)

        repository.status.value = LeaseStatus.Parked(ParkReason.Held, listOf(LeaseAction.UseHere))
        assertEquals(
            LeaseGuardUiState(scrim = ScrimState.InUse, actions = listOf(LeaseAction.UseHere), unprotected = true),
            viewModel.uiState.value
        )
    }

    @Test
    fun `protection off shows only the off banner, no scrim`() = runTest(dispatcher) {
        val viewModel = collecting()

        repository.unprotected.value = true
        repository.status.value = LeaseStatus.Off

        assertEquals(LeaseGuardUiState(protectionOff = true), viewModel.uiState.value)
    }

    @Test
    fun `Turn protection back on runs at once, without a warning`() = runTest(dispatcher) {
        val viewModel = collecting()
        repository.status.value = LeaseStatus.Off

        viewModel.onAction(LeaseAction.TurnProtectionOn)

        assertNull(viewModel.uiState.value.warning)
        assertEquals(listOf("turnProtectionOn"), repository.leaseCalls)
    }

    @Test
    fun `actions without a warning reach the repository at once`() = runTest(dispatcher) {
        val viewModel = collecting()

        viewModel.onAction(LeaseAction.UseHere)
        viewModel.onAction(LeaseAction.Refresh)
        viewModel.continueWithoutProtection()

        assertEquals(1, repository.useHereCalls)
        assertEquals(listOf("refresh"), repository.leaseCalls)
        assertEquals(1, repository.continueCalls)
    }

    @Test
    fun `risky actions open the warning step first and run only on confirm`() = runTest(dispatcher) {
        val viewModel = collecting()
        repository.status.value = damaged
        val cases = mapOf(
            LeaseAction.TakeOverAnyway to { repository.leaseCalls.lastOrNull() == "takeOverAnyway" },
            LeaseAction.TurnOffProtection to { repository.leaseCalls.lastOrNull() == "turnOffProtection" },
            LeaseAction.ContinueWithoutProtection to { repository.continueCalls == 1 }
        )
        cases.forEach { (action, ran) ->
            viewModel.onAction(action)
            assertEquals(action, viewModel.uiState.value.warning)
            assertFalse("$action ran before confirm", ran())

            viewModel.confirmWarning()
            assertNull(viewModel.uiState.value.warning)
            assertTrue("$action did not run", ran())
        }
    }

    @Test
    fun `Cancel closes the warning step and runs nothing`() = runTest(dispatcher) {
        val viewModel = collecting()
        repository.status.value = damaged

        viewModel.onAction(LeaseAction.TakeOverAnyway)
        viewModel.cancelWarning()
        viewModel.confirmWarning()

        assertNull(viewModel.uiState.value.warning)
        assertTrue(repository.leaseCalls.isEmpty())
    }

    @Test
    fun `a new lease state replaces the warning step, and a late confirm does nothing`() = runTest(dispatcher) {
        val viewModel = collecting()
        repository.status.value = damaged
        viewModel.onAction(LeaseAction.TakeOverAnyway)

        repository.status.value = LeaseStatus.Parked(ParkReason.Held, listOf(LeaseAction.UseHere))
        assertNull(viewModel.uiState.value.warning)

        viewModel.confirmWarning()
        assertTrue(repository.leaseCalls.isEmpty())
    }

    @Test
    fun `a warning closed by a new state doesn't come back when the old state returns`() = runTest(dispatcher) {
        val viewModel = collecting()
        repository.status.value = damaged
        viewModel.onAction(LeaseAction.TakeOverAnyway)

        repository.status.value = LeaseStatus.Parked(ParkReason.Error, listOf(LeaseAction.Refresh))
        repository.status.value = damaged

        assertNull(viewModel.uiState.value.warning)
        viewModel.confirmWarning()
        assertTrue(repository.leaseCalls.isEmpty())
    }

    @Test
    fun `the same state re-reported keeps the warning step`() = runTest(dispatcher) {
        val viewModel = collecting()
        repository.status.value = damaged
        viewModel.onAction(LeaseAction.TurnOffProtection)

        repository.status.value = damaged.copy()

        assertEquals(LeaseAction.TurnOffProtection, viewModel.uiState.value.warning)
    }
}
