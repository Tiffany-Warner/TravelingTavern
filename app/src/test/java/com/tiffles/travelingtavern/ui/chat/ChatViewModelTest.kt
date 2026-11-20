package com.tiffles.travelingtavern.ui.chat

import androidx.lifecycle.SavedStateHandle
import com.tiffles.travelingtavern.domain.FakeCharacterRepository
import com.tiffles.travelingtavern.domain.FakeChatSessionRepository
import com.tiffles.travelingtavern.domain.character
import com.tiffles.travelingtavern.domain.model.ChatActionResult
import com.tiffles.travelingtavern.domain.model.MessageAction
import com.tiffles.travelingtavern.domain.model.OpenChat
import com.tiffles.travelingtavern.domain.model.PageError
import com.tiffles.travelingtavern.domain.model.PageState
import com.tiffles.travelingtavern.domain.tappedMessage
import com.tiffles.travelingtavern.domain.usecase.GetMessageActionsUseCase
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val repository = FakeCharacterRepository()
    private val chat = FakeChatSessionRepository()
    private lateinit var savedState: SavedStateHandle
    private lateinit var viewModel: ChatViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(chatFile: String? = null, saved: Map<String, Any?> = emptyMap()) {
        savedState = SavedStateHandle(
            mapOf(
                Routes.FullScreen.Chat.ARG_CHARACTER_ID to "Tide Pods.png",
                Routes.FullScreen.Chat.ARG_CHAT_FILE to chatFile
            ) + saved
        )
        viewModel = ChatViewModel(savedState, repository, chat, GetMessageActionsUseCase())
    }

    private fun TestScope.collectState() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    }

    private fun TestScope.collectEvents(): List<ChatEvent> {
        val events = mutableListOf<ChatEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.events.collect { events += it } }
        return events
    }

    /** A chat that's open and ready, with the state collected. */
    private fun TestScope.openReadyChat(chatFile: String? = null) {
        repository.characters.value = listOf(character("Tide Pods"))
        createViewModel(chatFile)
        collectState()
    }

    private fun ready() = viewModel.uiState.value as ChatUiState.Ready

    // -- Opening and following the chat

    @Test
    fun `opens the route's chat file in SillyTavern on entry and says Chat is showing`() {
        createViewModel(chatFile = "Tide Pods - 2026-11-19")
        assertEquals(listOf("Tide Pods.png"), repository.openedChats)
        assertEquals(listOf<String?>("Tide Pods - 2026-11-19"), repository.requestedChatFiles)
        assertTrue(repository.visibleNow)
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

    @Test
    fun `follows the chat the page switches to, so a restore reopens it`() = runTest {
        openReadyChat(chatFile = "Tide Pods - original")

        chat.chatChanges.emit(OpenChat("Tide Pods.png", "Tide Pods - branch"))
        chat.chatChanges.emit(OpenChat("Bob.png", "Bob - other")) // another character: ignored

        assertEquals("Tide Pods - branch", viewModel.followedChatFile)
        assertEquals("Tide Pods - branch", savedState.get<String>(ChatViewModel.KEY_FOLLOWED_CHAT_FILE))
    }

    @Test
    fun `after process death the followed chat opens, not the route's`() {
        createViewModel(
            chatFile = "Tide Pods - original",
            saved = mapOf(ChatViewModel.KEY_FOLLOWED_CHAT_FILE to "Tide Pods - branch")
        )
        assertEquals(listOf<String?>("Tide Pods - branch"), repository.requestedChatFiles)
    }

    @Test
    fun `without a chat file it follows whatever SillyTavern opened`() {
        repository.currentChatFile = "Tide Pods - current"
        createViewModel()
        assertEquals("Tide Pods - current", viewModel.followedChatFile)
    }

    // -- Message actions

    @Test
    fun `a tap on the latest character message offers Regenerate and Continue`() = runTest {
        openReadyChat()

        chat.messageTaps.emit(tappedMessage())

        val sheet = ready().sheet!!
        assertEquals(
            listOf(
                MessageAction.Regenerate, MessageAction.Continue, MessageAction.Copy, MessageAction.Edit,
                MessageAction.Branch, MessageAction.Hide, MessageAction.Delete
            ),
            sheet.actions
        )
        assertEquals(sheet.actions.toSet(), sheet.enabled)
    }

    @Test
    fun `the latest user message offers Regenerate but not Continue, older ones neither`() = runTest {
        openReadyChat()

        chat.messageTaps.emit(tappedMessage(isUser = true))
        assertTrue(MessageAction.Regenerate in ready().sheet!!.actions)
        assertFalse(MessageAction.Continue in ready().sheet!!.actions)

        chat.messageTaps.emit(tappedMessage(isLatest = false))
        assertFalse(MessageAction.Regenerate in ready().sheet!!.actions)
        assertFalse(MessageAction.Continue in ready().sheet!!.actions)
    }

    @Test
    fun `while generating only Copy is enabled and other actions don't run`() = runTest {
        openReadyChat()
        chat.generating.value = true

        chat.messageTaps.emit(tappedMessage())
        assertEquals(setOf(MessageAction.Copy), ready().sheet!!.enabled)

        viewModel.onMessageAction(MessageAction.Edit)
        assertTrue(chat.actions.isEmpty())
    }

    @Test
    fun `an action runs on the tapped message and closes the sheet`() = runTest {
        openReadyChat()
        chat.messageTaps.emit(tappedMessage(id = 5))

        viewModel.onMessageAction(MessageAction.Branch)

        assertEquals(listOf(tappedMessage(id = 5) to MessageAction.Branch), chat.actions)
        assertNull(ready().sheet)
    }

    @Test
    fun `the open sheet reveals the tapped message, and closing it ends the reveal`() = runTest {
        openReadyChat()
        chat.messageTaps.emit(tappedMessage(id = 5))

        viewModel.onSheetShown(coveredDp = 320)
        assertEquals(listOf(5 to 320), chat.reveals)

        viewModel.onMessageAction(MessageAction.Copy)
        assertEquals(1, chat.endRevealCalls)

        viewModel.onSheetShown(coveredDp = 320)
        assertEquals(1, chat.reveals.size)
    }

    @Test
    fun `Delete asks first, then deletes`() = runTest {
        openReadyChat()
        chat.messageTaps.emit(tappedMessage())

        viewModel.onMessageAction(MessageAction.Delete)
        assertEquals(ChatConfirmation.DeleteMessage(tappedMessage()), ready().confirmation)
        assertTrue(chat.actions.isEmpty())

        viewModel.onConfirm()
        assertEquals(listOf(tappedMessage() to MessageAction.Delete), chat.actions)
        assertNull(ready().confirmation)
    }

    @Test
    fun `Copy hands the text to the screen`() = runTest {
        openReadyChat()
        val events = collectEvents()
        chat.actionResult = ChatActionResult.Done("Hello there")
        chat.messageTaps.emit(tappedMessage())

        viewModel.onMessageAction(MessageAction.Copy)

        assertEquals(listOf<ChatEvent>(ChatEvent.CopyText("Hello there")), events)
    }

    @Test
    fun `a stale message says so and re-reads the chat`() = runTest {
        openReadyChat()
        val events = collectEvents()
        chat.actionResult = ChatActionResult.Stale
        chat.messageTaps.emit(tappedMessage())

        viewModel.onMessageAction(MessageAction.Hide)

        assertEquals(listOf<ChatEvent>(ChatEvent.MessageChanged), events)
        assertEquals(1, chat.reloadCalls)
    }

    // -- ⋮ menu

    @Test
    fun `Start new chat and Restart chat ask first, then call SillyTavern`() = runTest {
        openReadyChat()

        viewModel.onStartNewChat()
        assertEquals(ChatConfirmation.StartNewChat, ready().confirmation)
        viewModel.onConfirm()

        viewModel.onRestartChat()
        assertEquals(ChatConfirmation.RestartChat, ready().confirmation)
        viewModel.onConfirmationDismissed()
        assertTrue(chat.newChats == listOf(false))

        viewModel.onRestartChat()
        viewModel.onConfirm()
        assertEquals(listOf(false, true), chat.newChats)
    }

    @Test
    fun `the menu is disabled while generating`() = runTest {
        openReadyChat()
        chat.generating.value = true

        assertFalse(ready().menuEnabled)
        viewModel.onRestartChat()
        assertNull(ready().confirmation)
    }

    // -- Input bar

    @Test
    fun `Send is disabled while empty, sends the text and clears the field`() = runTest {
        openReadyChat()
        assertFalse(ready().canSend(viewModel.input))

        viewModel.onInputChange("Good morning!")
        assertTrue(ready().canSend(viewModel.input))

        viewModel.onSend()
        assertEquals(listOf("Good morning!"), chat.sent)
        assertEquals("", viewModel.input)
    }

    @Test
    fun `while generating Send is disabled and Stop stops`() = runTest {
        openReadyChat()
        viewModel.onInputChange("Wait")
        chat.generating.value = true

        assertTrue(ready().generating)
        assertFalse(ready().canSend(viewModel.input))
        viewModel.onSend()
        viewModel.onWriteForMe()
        viewModel.onStop()

        assertTrue(chat.sent.isEmpty())
        assertEquals(0, chat.impersonateCalls)
        assertEquals(1, chat.stopCalls)
    }

    @Test
    fun `Write for me fills the field with impersonate's text`() = runTest {
        openReadyChat()

        viewModel.onWriteForMe()
        chat.impersonations.emit("I wave back.")

        assertEquals(1, chat.impersonateCalls)
        assertEquals("I wave back.", viewModel.input)
    }
}
