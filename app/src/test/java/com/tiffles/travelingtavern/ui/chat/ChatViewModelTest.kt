package com.tiffles.travelingtavern.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.tiffles.travelingtavern.domain.FakeCharacterRepository
import com.tiffles.travelingtavern.domain.FakeChatInputRepository
import com.tiffles.travelingtavern.domain.FakeMicrophonePermission
import com.tiffles.travelingtavern.domain.FakeSpeechInputRepository
import com.tiffles.travelingtavern.domain.character
import com.tiffles.travelingtavern.domain.model.ChatInputTarget
import com.tiffles.travelingtavern.domain.model.PageError
import com.tiffles.travelingtavern.domain.model.PageState
import com.tiffles.travelingtavern.domain.model.SpeechError
import com.tiffles.travelingtavern.domain.model.SpeechEvent
import com.tiffles.travelingtavern.domain.step
import com.tiffles.travelingtavern.domain.usecase.IsVoiceInputAvailableUseCase
import com.tiffles.travelingtavern.domain.usecase.ListenForSpeechUseCase
import com.tiffles.travelingtavern.ui.navigation.Routes
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val repository = FakeCharacterRepository()
    private val chatInput = FakeChatInputRepository()
    private val microphone = FakeMicrophonePermission()
    private var speech = FakeSpeechInputRepository()
    private lateinit var viewModel: ChatViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    private fun createViewModel() {
        val args = SavedStateHandle(mapOf(Routes.FullScreen.Chat.ARG_CHARACTER_ID to "Tide Pods.png"))
        viewModel = ChatViewModel(
            savedStateHandle = args,
            repository = repository,
            chatInput = chatInput,
            listenForSpeech = ListenForSpeechUseCase(speech),
            microphone = microphone,
            isVoiceInputAvailable = IsVoiceInputAvailableUseCase(speech)
        )
    }

    private fun TestScope.collectState() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    }

    /** A chat that's open and ready, with the Ready state collected. */
    private fun TestScope.openReadyChat() {
        repository.characters.value = listOf(character("Tide Pods"))
        createViewModel()
        collectState()
    }

    private fun voiceState() = (viewModel.uiState.value as ChatUiState.Ready).voiceInput

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

    @Test
    fun `mic tap in the page starts listening and fills the input, a second tap stops and keeps the text`() = runTest {
        speech = FakeSpeechInputRepository(
            passes = listOf(listOf(step(0, SpeechEvent.Listening), step(500, SpeechEvent.Partial("hello"))))
        )
        openReadyChat()

        chatInput.micPresses.emit(ChatInputTarget.Story)
        advanceTimeBy(600)
        assertEquals(VoiceInputState.Listening, voiceState())
        assertEquals(listOf("begin:Story", "update:hello"), chatInput.calls)

        chatInput.micPresses.emit(ChatInputTarget.Story)
        runCurrent()
        assertEquals(VoiceInputState.Idle, voiceState())
        assertEquals(listOf("begin:Story", "update:hello", "end"), chatInput.calls)
        assertNull(viewModel.voiceMessage.value)
    }

    @Test
    fun `back to idle after the pause tolerance runs out, with NoMatch if nothing was heard`() = runTest {
        openReadyChat()

        viewModel.onMicPressed()
        advanceTimeBy(ListenForSpeechUseCase.PAUSE_TOLERANCE_MS + 1)

        assertEquals(VoiceInputState.Idle, voiceState())
        assertEquals(VoiceMessage.NoMatch, viewModel.voiceMessage.value)
        assertEquals(listOf("begin:Story", "end"), chatInput.calls)
    }

    @Test
    fun `back to idle on a recognizer error, with a message`() = runTest {
        speech = FakeSpeechInputRepository(
            passes = listOf(listOf(step(0, SpeechEvent.Listening), step(300, SpeechEvent.Error(SpeechError.LanguagePackMissing))))
        )
        openReadyChat()

        viewModel.onMicPressed()
        advanceTimeBy(400)

        assertEquals(VoiceInputState.Idle, voiceState())
        assertEquals(VoiceMessage.LanguagePackMissing, viewModel.voiceMessage.value)
        assertEquals(listOf("begin:Story", "end"), chatInput.calls)

        viewModel.onVoiceMessageShown()
        assertNull(viewModel.voiceMessage.value)
    }

    @Test
    fun `leaving Chat stops listening and resets the page's mic`() = runTest {
        openReadyChat()
        viewModel.onMicPressed()
        runCurrent()

        viewModel.viewModelScope.cancel()
        runCurrent()

        assertEquals(listOf("begin:Story", "end"), chatInput.calls)
    }

    @Test
    fun `without the microphone permission it asks first, and says so when refused`() = runTest {
        microphone.granted = false
        openReadyChat()
        val requests = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.permissionRequests.collect { requests += it } }

        viewModel.onMicPressed()
        assertEquals(1, requests.size)
        assertEquals(VoiceInputState.Idle, voiceState())

        viewModel.onMicPermissionResult(granted = false)
        assertEquals(VoiceMessage.PermissionDenied, viewModel.voiceMessage.value)
        assertEquals(VoiceInputState.Idle, voiceState())

        viewModel.onMicPermissionResult(granted = true)
        assertEquals(VoiceInputState.Listening, voiceState())
    }

    @Test
    fun `no on-device recognition means no mic, and taps do nothing`() = runTest {
        speech = FakeSpeechInputRepository(available = false)
        openReadyChat()

        viewModel.onMicPressed()
        runCurrent()

        assertEquals(VoiceInputState.Unavailable, voiceState())
        assertEquals(emptyList<String>(), chatInput.calls)
    }

    @Test
    fun `taps from the EchoText input are ignored until 1_7`() = runTest {
        openReadyChat()

        chatInput.micPresses.emit(ChatInputTarget.Text)
        runCurrent()

        assertEquals(VoiceInputState.Idle, voiceState())
        assertEquals(emptyList<String>(), chatInput.calls)
    }
}
