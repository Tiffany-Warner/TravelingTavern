package com.tiffles.travelingtavern.ui.chats

import com.tiffles.travelingtavern.domain.FakeCharacterRepository
import com.tiffles.travelingtavern.domain.FakeHiddenChatsRepository
import com.tiffles.travelingtavern.domain.activity
import com.tiffles.travelingtavern.domain.character
import com.tiffles.travelingtavern.domain.model.ChatRef
import com.tiffles.travelingtavern.domain.model.PageError
import com.tiffles.travelingtavern.domain.model.PageState
import com.tiffles.travelingtavern.domain.usecase.GetRecentChatsUseCase
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatsViewModelTest {

    private val repository = FakeCharacterRepository()
    private val hidden = FakeHiddenChatsRepository()
    private lateinit var viewModel: ChatsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        viewModel = ChatsViewModel(GetRecentChatsUseCase(repository, hidden), repository, hidden)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** uiState only updates while collected (WhileSubscribed), as it would on screen. */
    private fun TestScope.collectState() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.optionsState.collect {} }
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

    private val bob1 = ChatRef("Bob.png", "Bob - 1")
    private val bob2 = ChatRef("Bob.png", "Bob - 2")

    /** Bob with two chats; returns the newest row ("Bob - 2"). */
    private fun twoBobChats() = run {
        repository.characters.value = listOf(character("Bob"))
        repository.chatActivity.value = listOf(
            activity("Bob.png", "old", "2026-11-01T10:00:00Z", chatFile = bob1.chatFile),
            activity("Bob.png", "new", "2026-11-17T10:00:00Z", chatFile = bob2.chatFile)
        )
        (viewModel.uiState.value as ChatsUiState.Success).chats.first()
    }

    private fun visibleFiles() = (viewModel.uiState.value as ChatsUiState.Success).chats.map { it.chatFile }

    @Test
    fun `long press opens the options sheet for that chat`() = runTest {
        collectState()
        val chat = twoBobChats()

        viewModel.onChatLongPress(chat)

        assertEquals(chat, viewModel.optionsState.value.optionsFor)
        assertTrue(viewModel.optionsState.value.canDelete)
        viewModel.onDismissOptions()
        assertNull(viewModel.optionsState.value.optionsFor)
    }

    @Test
    fun `hide removes the row and undo brings it back`() = runTest {
        collectState()
        val events = mutableListOf<ChatsEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.toList(events) }
        viewModel.onChatLongPress(twoBobChats())

        viewModel.onHide()

        assertNull(viewModel.optionsState.value.optionsFor)
        assertEquals(listOf("Bob - 1"), visibleFiles())
        assertEquals(listOf(ChatsEvent.ChatHidden(bob2)), events)

        viewModel.onUndoHide(bob2)
        assertEquals(listOf("Bob - 2", "Bob - 1"), visibleFiles())
    }

    @Test
    fun `delete asks first and only deletes on confirm`() = runTest {
        collectState()
        val chat = twoBobChats()
        viewModel.onChatLongPress(chat)

        viewModel.onDelete()
        assertEquals(chat, viewModel.optionsState.value.confirmDeleteFor)
        assertTrue(repository.deletedChats.isEmpty())

        viewModel.onDismissDelete()
        assertNull(viewModel.optionsState.value.confirmDeleteFor)
        assertTrue(repository.deletedChats.isEmpty())

        viewModel.onChatLongPress(chat)
        viewModel.onDelete()
        viewModel.onConfirmDelete()
        assertEquals(listOf(bob2), repository.deletedChats)
        assertNull(viewModel.optionsState.value.confirmDeleteFor)
    }

    @Test
    fun `the confirmation stays open and can't be dismissed while deleting`() = runTest {
        collectState()
        val gate = CompletableDeferred<Unit>()
        repository.deleteGate = gate
        viewModel.onChatLongPress(twoBobChats())
        viewModel.onDelete()

        viewModel.onConfirmDelete()
        viewModel.onDismissDelete()
        viewModel.onConfirmDelete()

        assertTrue(viewModel.optionsState.value.isDeleting)
        assertEquals(1, repository.deletedChats.size)
        gate.complete(Unit)
        assertFalse(viewModel.optionsState.value.isDeleting)
        assertNull(viewModel.optionsState.value.confirmDeleteFor)
    }

    @Test
    fun `a failed delete keeps the row and shows the snackbar`() = runTest {
        collectState()
        val events = mutableListOf<ChatsEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.toList(events) }
        repository.deleteSucceeds = false
        viewModel.onChatLongPress(twoBobChats())
        viewModel.onDelete()

        viewModel.onConfirmDelete()

        assertEquals(listOf(ChatsEvent.DeleteFailed), events)
        assertEquals(listOf("Bob - 2", "Bob - 1"), visibleFiles())
        assertNull(viewModel.optionsState.value.confirmDeleteFor)
    }

    @Test
    fun `a deleted chat is dropped from the hidden list`() = runTest {
        collectState()
        val chat = twoBobChats()
        viewModel.onChatLongPress(chat)
        viewModel.onDelete()
        hidden.hiddenChats.value = setOf(bob2, bob1)

        viewModel.onConfirmDelete()

        assertEquals(setOf(bob1), hidden.hiddenChats.value)
    }

    @Test
    fun `delete is disabled for the chat SillyTavern is generating in`() = runTest {
        collectState()
        val chat = twoBobChats()
        repository.generatingChat.value = bob2
        viewModel.onChatLongPress(chat)

        assertFalse(viewModel.optionsState.value.canDelete)
        viewModel.onDelete()
        assertNull(viewModel.optionsState.value.confirmDeleteFor)

        repository.generatingChat.value = bob1
        assertTrue(viewModel.optionsState.value.canDelete)
    }

    @Test
    fun `the menu shows only while a chat is hidden and show hidden chats clears the list`() = runTest {
        collectState()
        twoBobChats()
        assertFalse(viewModel.optionsState.value.hasHiddenChats)

        hidden.hiddenChats.value = setOf(bob1)
        assertTrue(viewModel.optionsState.value.hasHiddenChats)

        viewModel.onShowHiddenChats()
        assertFalse(viewModel.optionsState.value.hasHiddenChats)
        assertEquals(listOf("Bob - 2", "Bob - 1"), visibleFiles())
    }

    @Test
    fun `every chat hidden shows the empty state, with the menu`() = runTest {
        collectState()
        twoBobChats()

        hidden.hiddenChats.value = setOf(bob1, bob2)

        assertEquals(ChatsUiState.Empty, viewModel.uiState.value)
        assertTrue(viewModel.optionsState.value.hasHiddenChats)
    }
}
