package com.tiffles.travelingtavern.ui.lorebooks

import androidx.lifecycle.SavedStateHandle
import com.tiffles.travelingtavern.domain.FakeLorebookRepository
import com.tiffles.travelingtavern.domain.entry
import com.tiffles.travelingtavern.domain.repository.LorebookFailure
import com.tiffles.travelingtavern.domain.usecase.DeleteLorebookEntryUseCase
import com.tiffles.travelingtavern.domain.usecase.GetLorebookEntriesUseCase
import com.tiffles.travelingtavern.domain.usecase.SaveLorebookEntryUseCase
import com.tiffles.travelingtavern.ui.navigation.Routes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LorebookEntryViewModelTest {

    private val repository = FakeLorebookRepository()
    private val dispatcher = StandardTestDispatcher()

    private val regexEntry = entry(uid = 4, title = "Wood", keywords = listOf("/a,b/", "wood"), content = "Trees")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository.entriesByName["Eldoria"] = listOf(entry(0), regexEntry)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(uid: Int) = LorebookEntryViewModel(
        SavedStateHandle(
            mapOf(Routes.FullScreen.LorebookEntry.ARG_NAME to "Eldoria", Routes.FullScreen.LorebookEntry.ARG_UID to uid)
        ),
        GetLorebookEntriesUseCase(repository),
        SaveLorebookEntryUseCase(repository),
        DeleteLorebookEntryUseCase(repository),
        repository
    )

    private val LorebookEntryViewModel.editing get() = uiState.value as LorebookEntryUiState.Editing

    private fun TestScope.events(vm: LorebookEntryViewModel): Pair<MutableList<Unit>, MutableList<Unit>> {
        val done = mutableListOf<Unit>()
        val failed = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.done.collect { done += it } }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.actionFailed.collect { failed += it } }
        return done to failed
    }

    @Test
    fun `a new entry starts empty and saves without a uid`() = runTest(dispatcher) {
        val vm = viewModel(Routes.FullScreen.LorebookEntry.NEW_ENTRY)
        val (done, _) = events(vm)
        assertTrue(vm.editing.isNew)

        vm.update(EntryForm(title = "Glade", keywords = "glade, refuge , ", content = "A clearing"))
        vm.save()
        advanceUntilIdle()

        val saved = repository.savedEntries.single()
        assertEquals("Eldoria", saved.first)
        assertEquals(entry(uid = null, title = "Glade", keywords = listOf("glade", "refuge"), content = "A clearing"), saved.second)
        assertEquals(1, done.size)
    }

    @Test
    fun `an existing entry loads, and untouched keywords are saved exactly as they were`() = runTest(dispatcher) {
        val vm = viewModel(4)
        advanceUntilIdle()
        assertEquals(EntryForm("Wood", "/a,b/, wood", "Trees", true), vm.editing.form)

        vm.update(vm.editing.form.copy(title = "Woods"))
        vm.save()
        advanceUntilIdle()

        assertEquals(listOf("/a,b/", "wood"), repository.savedEntries.single().second.keywords)
        assertEquals("Woods", repository.savedEntries.single().second.title)
    }

    @Test
    fun `edited keywords are split on commas`() = runTest(dispatcher) {
        val vm = viewModel(4)
        advanceUntilIdle()

        vm.update(vm.editing.form.copy(keywords = "wood, forest"))
        vm.save()
        advanceUntilIdle()

        assertEquals(listOf("wood", "forest"), repository.savedEntries.single().second.keywords)
    }

    @Test
    fun `a missing entry is NotFound`() = runTest(dispatcher) {
        val vm = viewModel(42)
        advanceUntilIdle()
        assertEquals(LorebookEntryUiState.NotFound, vm.uiState.value)
    }

    @Test
    fun `delete removes it and leaves, a failure stays and reports`() = runTest(dispatcher) {
        val vm = viewModel(4)
        val (done, failed) = events(vm)
        advanceUntilIdle()

        repository.failure = LorebookFailure.Failed
        vm.delete()
        advanceUntilIdle()
        assertEquals(1, failed.size)
        assertEquals(false, vm.editing.isWorking)

        repository.failure = null
        vm.delete()
        advanceUntilIdle()
        assertEquals(listOf("Eldoria" to 4), repository.deletedEntries)
        assertEquals(1, done.size)
    }

    @Test
    fun `tokens are counted once typing pauses`() = runTest(dispatcher) {
        val vm = viewModel(Routes.FullScreen.LorebookEntry.NEW_ENTRY)
        runCurrent()

        vm.update(EntryForm(content = "H"))
        advanceTimeBy(100)
        vm.update(EntryForm(content = "Hello"))
        advanceTimeBy(600)

        assertEquals(listOf("Hello"), repository.countedTexts)
        assertEquals(7, vm.editing.tokenCount)
    }
}
