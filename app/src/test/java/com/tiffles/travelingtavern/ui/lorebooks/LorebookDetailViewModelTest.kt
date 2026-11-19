package com.tiffles.travelingtavern.ui.lorebooks

import androidx.lifecycle.SavedStateHandle
import com.tiffles.travelingtavern.domain.FakeCharacterRepository
import com.tiffles.travelingtavern.domain.FakeLorebookRepository
import com.tiffles.travelingtavern.domain.character
import com.tiffles.travelingtavern.domain.entry
import com.tiffles.travelingtavern.domain.lorebook
import com.tiffles.travelingtavern.domain.repository.LorebookFailure
import com.tiffles.travelingtavern.domain.usecase.DeleteLorebookUseCase
import com.tiffles.travelingtavern.domain.usecase.GetLorebookEntriesUseCase
import com.tiffles.travelingtavern.domain.usecase.GetLorebooksUseCase
import com.tiffles.travelingtavern.domain.usecase.SetLorebookActiveUseCase
import com.tiffles.travelingtavern.ui.navigation.Routes
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LorebookDetailViewModelTest {

    private val lorebooks = FakeLorebookRepository()
    private val characters = FakeCharacterRepository()
    private val seraphina = character("Seraphina")

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        characters.characters.value = listOf(seraphina)
        lorebooks.lorebooks.value = listOf(lorebook("Eldoria", characterIds = listOf(seraphina.id)))
        lorebooks.entriesByName["Eldoria"] = listOf(entry(0), entry(1))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = LorebookDetailViewModel(
        SavedStateHandle(mapOf(Routes.FullScreen.LorebookDetail.ARG_NAME to "Eldoria")),
        GetLorebooksUseCase(lorebooks, characters),
        GetLorebookEntriesUseCase(lorebooks),
        SetLorebookActiveUseCase(lorebooks),
        DeleteLorebookUseCase(lorebooks),
        lorebooks
    )

    private fun TestScope.collected(vm: LorebookDetailViewModel) = vm.also {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }
    }

    private val LorebookDetailViewModel.success get() = uiState.value as LorebookDetailUiState.Success

    @Test
    fun `shows entries and where it's attached`() = runTest {
        val vm = collected(viewModel())

        assertEquals(listOf(0, 1), vm.success.entries.map { it.uid })
        assertEquals(listOf(seraphina), vm.success.characters)
        assertFalse(vm.success.isGlobal)
    }

    @Test
    fun `a lorebook that no longer exists is NotFound, other failures LoadFailed`() = runTest {
        lorebooks.entriesByName.clear()
        assertEquals(LorebookDetailUiState.NotFound, collected(viewModel()).uiState.value)

        lorebooks.failure = LorebookFailure.Failed
        assertEquals(LorebookDetailUiState.LoadFailed, collected(viewModel()).uiState.value)
    }

    @Test
    fun `the switch shows the new value at once and keeps it until SillyTavern confirms`() = runTest {
        val vm = collected(viewModel())
        lorebooks.actionGate = CompletableDeferred()

        vm.setGlobal(true)
        assertTrue(vm.success.isGlobal)
        assertTrue(vm.success.isWorking)

        lorebooks.actionGate!!.complete(Unit)
        assertEquals(listOf("Eldoria" to true), lorebooks.globalChanges)
        assertTrue(vm.success.isGlobal)

        lorebooks.lorebooks.value = listOf(lorebook("Eldoria", global = true, characterIds = listOf(seraphina.id)))
        assertTrue(vm.success.isGlobal)
        assertFalse(vm.success.isWorking)
    }

    @Test
    fun `a failed toggle goes back and reports it`() = runTest {
        val vm = collected(viewModel())
        val failures = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.actionFailed.collect { failures += it } }
        lorebooks.failure = LorebookFailure.Failed

        vm.setGlobal(true)

        assertFalse(vm.success.isGlobal)
        assertEquals(1, failures.size)
    }

    @Test
    fun `deleting closes the screen`() = runTest {
        val vm = collected(viewModel())
        val closed = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.closed.collect { closed += it } }

        vm.delete()

        assertEquals(listOf("Eldoria"), lorebooks.deleted)
        assertEquals(1, closed.size)
    }

    @Test
    fun `entries reload after one of them changed`() = runTest {
        val vm = collected(viewModel())
        lorebooks.entriesByName["Eldoria"] = listOf(entry(0))

        lorebooks.entriesChanged.emit("Other")
        assertEquals(2, vm.success.entries.size)

        lorebooks.entriesChanged.emit("Eldoria")
        assertEquals(1, vm.success.entries.size)
    }
}
