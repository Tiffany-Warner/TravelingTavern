package com.tiffles.travelingtavern.ui.chat

import androidx.lifecycle.SavedStateHandle
import com.tiffles.travelingtavern.domain.FakeCharacterRepository
import com.tiffles.travelingtavern.domain.character
import com.tiffles.travelingtavern.domain.model.PageError
import com.tiffles.travelingtavern.domain.model.PageState
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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val repository = FakeCharacterRepository()
    private lateinit var viewModel: ChatViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    private fun createViewModel() {
        val args = SavedStateHandle(mapOf(Routes.FullScreen.Chat.ARG_CHARACTER_ID to "Tide Pods.png"))
        viewModel = ChatViewModel(args, repository)
    }

    private fun TestScope.collectState() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `opens the character's chat in SillyTavern on entry`() {
        createViewModel()
        assertEquals(listOf("Tide Pods.png"), repository.openedChats)
    }

    @Test
    fun `loading until SillyTavern has drawn the chat, then ready`() = runTest {
        val gate = CompletableDeferred<Unit>()
        repository.openGate = gate
        repository.characters.value = listOf(character("Bob"), character("Tide Pods"))
        createViewModel()
        collectState()

        assertEquals(ChatUiState.Loading(character("Tide Pods")), viewModel.uiState.value)

        gate.complete(Unit)
        assertEquals(ChatUiState.Ready(character("Tide Pods")), viewModel.uiState.value)
    }

    @Test
    fun `loading until the character is known, error when the page fails`() = runTest {
        createViewModel()
        collectState()
        assertEquals(ChatUiState.Loading(null), viewModel.uiState.value)

        repository.characters.value = listOf(character("Tide Pods"))
        assertEquals(ChatUiState.Ready(character("Tide Pods")), viewModel.uiState.value)

        repository.pageState.value = PageState.Error(PageError.Unreachable)
        assertEquals(ChatUiState.Error(PageError.Unreachable), viewModel.uiState.value)
    }
}
