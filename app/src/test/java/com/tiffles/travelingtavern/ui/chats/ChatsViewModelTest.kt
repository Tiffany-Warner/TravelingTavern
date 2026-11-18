package com.tiffles.travelingtavern.ui.chats

import com.tiffles.travelingtavern.domain.FakeCharacterRepository
import com.tiffles.travelingtavern.domain.activity
import com.tiffles.travelingtavern.domain.character
import com.tiffles.travelingtavern.domain.model.PageError
import com.tiffles.travelingtavern.domain.model.PageState
import com.tiffles.travelingtavern.domain.usecase.GetRecentChatsUseCase
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
class ChatsViewModelTest {

    private val repository = FakeCharacterRepository()
    private lateinit var viewModel: ChatsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = ChatsViewModel(GetRecentChatsUseCase(repository), repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** uiState only updates while collected (WhileSubscribed), as it would on screen. */
    private fun TestScope.collectState() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    }

    @Test
    fun `loading until SillyTavern reports the lists`() = runTest {
        collectState()
        assertEquals(ChatsUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun `empty when there are no chats`() = runTest {
        collectState()
        repository.characters.value = listOf(character("Bob"))
        repository.chatActivity.value = emptyList()

        assertEquals(ChatsUiState.Empty, viewModel.uiState.value)
    }

    @Test
    fun `success with the recent chats`() = runTest {
        collectState()
        repository.characters.value = listOf(character("Bob"))
        repository.chatActivity.value = listOf(activity("Bob.png", "Hi", "2026-11-17T10:00:00Z"))

        val state = viewModel.uiState.value as ChatsUiState.Success
        assertEquals(listOf("Bob"), state.chats.map { it.character.name })
    }

    @Test
    fun `a page error wins over any data`() = runTest {
        collectState()
        repository.characters.value = listOf(character("Bob"))
        repository.chatActivity.value = listOf(activity("Bob.png", "Hi", "2026-11-17T10:00:00Z"))
        repository.pageState.value = PageState.Error(PageError.AuthFailed)

        assertEquals(ChatsUiState.Error(PageError.AuthFailed), viewModel.uiState.value)
    }

    @Test
    fun `refresh shows the indicator until the repository answers and ignores repeats`() = runTest {
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
