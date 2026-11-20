package com.tiffles.travelingtavern.ui.personas

import androidx.lifecycle.SavedStateHandle
import com.tiffles.travelingtavern.domain.FakePersonaRepository
import com.tiffles.travelingtavern.domain.model.PageError
import com.tiffles.travelingtavern.domain.model.PageState
import com.tiffles.travelingtavern.domain.persona
import com.tiffles.travelingtavern.domain.repository.PersonaFailure
import com.tiffles.travelingtavern.domain.usecase.GetPersonasUseCase
import com.tiffles.travelingtavern.ui.navigation.Routes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PersonasViewModelTest {

    private val repository = FakePersonaRepository()
    private val dispatcher = UnconfinedTestDispatcher()
    private val mira = persona("1-Mira.png", name = "Mira", isActive = true)
    private val rowan = persona("2-Rowan.png", name = "Rowan", isDefault = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(inChat: Boolean) = PersonasViewModel(
        SavedStateHandle(mapOf(Routes.FullScreen.Personas.ARG_IN_CHAT to inChat)),
        GetPersonasUseCase(repository),
        repository
    )

    private fun TestScope.collect(vm: PersonasViewModel): MutableList<PersonasEvent> {
        val events = mutableListOf<PersonasEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect { events += it } }
        return events
    }

    private val PersonasViewModel.success get() = uiState.value as PersonasUiState.Success

    @Test
    fun `states follow the page and the list`() = runTest(dispatcher) {
        val vm = viewModel(inChat = false)
        collect(vm)
        assertEquals(PersonasUiState.Loading, vm.uiState.value)

        repository.personas.value = emptyList()
        assertEquals(PersonasUiState.Empty, vm.uiState.value)

        repository.personas.value = listOf(rowan, mira)
        assertEquals(listOf(mira, rowan), vm.success.personas)

        repository.pageState.value = PageState.Error(PageError.Unreachable)
        assertEquals(PersonasUiState.Error(PageError.Unreachable), vm.uiState.value)
    }

    @Test
    fun `opening the screen refreshes pictures`() = runTest(dispatcher) {
        viewModel(inChat = false)
        advanceUntilIdle()
        assertEquals(1, repository.refreshCalls)
    }

    @Test
    fun `in a chat, a tap selects, locks and closes`() = runTest(dispatcher) {
        repository.personas.value = listOf(mira, rowan)
        val vm = viewModel(inChat = true)
        val events = collect(vm)
        assertTrue(vm.success.inChat)

        vm.pick(rowan)
        advanceUntilIdle()

        assertEquals(listOf("2-Rowan.png" to true), repository.selected)
        assertEquals(listOf<PersonasEvent>(PersonasEvent.Close), events)
    }

    @Test
    fun `without a chat, a tap selects only and stays`() = runTest(dispatcher) {
        repository.personas.value = listOf(mira, rowan)
        val vm = viewModel(inChat = false)
        val events = collect(vm)

        vm.pick(rowan)
        advanceUntilIdle()

        assertEquals(listOf("2-Rowan.png" to false), repository.selected)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `a failed pick reports and keeps the screen`() = runTest(dispatcher) {
        repository.personas.value = listOf(mira)
        repository.failure = PersonaFailure.Failed
        val vm = viewModel(inChat = true)
        val events = collect(vm)

        vm.pick(mira)
        advanceUntilIdle()

        assertEquals(listOf<PersonasEvent>(PersonasEvent.ActionFailed), events)
        assertEquals(false, vm.success.isWorking)
    }

    @Test
    fun `one action at a time`() = runTest(dispatcher) {
        repository.personas.value = listOf(mira, rowan)
        repository.actionGate = CompletableDeferred()
        val vm = viewModel(inChat = false)
        collect(vm)

        vm.pick(rowan)
        vm.pick(mira)
        assertTrue(vm.success.isWorking)
        repository.actionGate!!.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf("2-Rowan.png" to false), repository.selected)
    }

    @Test
    fun `default toggles from the actions sheet`() = runTest(dispatcher) {
        repository.personas.value = listOf(mira, rowan)
        val vm = viewModel(inChat = false)
        collect(vm)

        vm.openActions(mira)
        assertEquals(mira, vm.success.actionsFor)
        vm.toggleDefault(mira)
        vm.toggleDefault(rowan)
        advanceUntilIdle()

        assertNull(vm.success.actionsFor)
        assertEquals(listOf("1-Mira.png" to true, "2-Rowan.png" to false), repository.defaults)
    }

    @Test
    fun `delete asks first, then deletes`() = runTest(dispatcher) {
        repository.personas.value = listOf(mira, rowan)
        val vm = viewModel(inChat = false)
        collect(vm)

        vm.openActions(rowan)
        vm.askDelete(rowan)
        assertNull(vm.success.actionsFor)
        assertEquals(rowan, vm.success.confirmDelete)
        assertTrue(repository.deleted.isEmpty())

        vm.confirmDelete()
        runCurrent()

        assertEquals(listOf("2-Rowan.png"), repository.deleted)
        assertNull(vm.success.confirmDelete)
    }
}
