package com.tiffles.travelingtavern.ui.characters

import com.tiffles.travelingtavern.domain.FakeCharacterRepository
import com.tiffles.travelingtavern.domain.character
import com.tiffles.travelingtavern.domain.model.PageError
import com.tiffles.travelingtavern.domain.model.PageState
import com.tiffles.travelingtavern.domain.usecase.GetCharacterLibraryUseCase
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
class CharactersViewModelTest {

    private val repository = FakeCharacterRepository()
    private lateinit var viewModel: CharactersViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = CharactersViewModel(GetCharacterLibraryUseCase(repository), repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun TestScope.collectState() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    }

    @Test
    fun `loading until the library arrives`() = runTest {
        collectState()
        assertEquals(CharactersUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun `empty library is Empty`() = runTest {
        collectState()
        repository.characters.value = emptyList()

        assertEquals(CharactersUiState.Empty, viewModel.uiState.value)
    }

    @Test
    fun `query filters the grid and a search with no matches is not Empty`() = runTest {
        collectState()
        repository.characters.value = listOf(character("Seraphina"), character("Bob"))

        viewModel.setQuery("bo")
        assertEquals(CharactersUiState.Success(listOf(character("Bob")), "bo"), viewModel.uiState.value)
        assertEquals("bo", viewModel.searchQuery.value)

        viewModel.setQuery("zed")
        assertEquals(CharactersUiState.Success(emptyList(), "zed"), viewModel.uiState.value)
    }

    @Test
    fun `a page error wins over the library`() = runTest {
        collectState()
        repository.characters.value = listOf(character("Bob"))
        repository.pageState.value = PageState.Error(PageError.Unreachable)

        assertEquals(CharactersUiState.Error(PageError.Unreachable), viewModel.uiState.value)
    }

    @Test
    fun `pull to refresh shows the indicator until the repository answers and ignores repeats`() = runTest {
        val gate = CompletableDeferred<Unit>()
        repository.refreshGate = gate

        viewModel.refresh()
        viewModel.refresh()
        assertTrue(viewModel.isRefreshing.value)
        assertEquals(1, repository.refreshCalls)

        gate.complete(Unit)
        assertFalse(viewModel.isRefreshing.value)
    }

    @Test
    fun `retry reloads the page`() {
        viewModel.retry()
        assertEquals(1, repository.retryCalls)
    }
}
