package com.tiffles.travelingtavern.ui.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tiffles.travelingtavern.domain.model.CharacterSummary
import com.tiffles.travelingtavern.domain.model.ChatInputTarget
import com.tiffles.travelingtavern.domain.model.PageError
import com.tiffles.travelingtavern.domain.model.PageState
import com.tiffles.travelingtavern.domain.model.SpeechError
import com.tiffles.travelingtavern.domain.model.VoiceSessionEvent
import com.tiffles.travelingtavern.domain.repository.CharacterRepository
import com.tiffles.travelingtavern.domain.repository.ChatInputRepository
import com.tiffles.travelingtavern.domain.repository.MicrophonePermission
import com.tiffles.travelingtavern.domain.usecase.IsVoiceInputAvailableUseCase
import com.tiffles.travelingtavern.domain.usecase.ListenForSpeechUseCase
import com.tiffles.travelingtavern.ui.navigation.Routes
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** 1.7 adds the thread / Story view to [Ready]; until then Chat is the main chat only. */
sealed interface ChatUiState {
    /** SillyTavern is still opening the chat; [character] once the library is known. */
    data class Loading(val character: CharacterSummary? = null) : ChatUiState
    data class Ready(
        val character: CharacterSummary,
        val voiceInput: VoiceInputState = VoiceInputState.Idle
    ) : ChatUiState
    data class Error(val error: PageError) : ChatUiState
}

/** The mic button's state. [Unavailable]: no on-device recognition, so the page shows no mic. */
enum class VoiceInputState { Idle, Listening, Unavailable }

/** A short message about voice input, shown once in a snackbar. */
enum class VoiceMessage { PermissionDenied, LanguagePackMissing, NoMatch, Failed }

/** One character's chat. Opens it in SillyTavern on entry; the page is the shared WebView. */
@HiltViewModel
class ChatViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: CharacterRepository,
    private val chatInput: ChatInputRepository,
    private val listenForSpeech: ListenForSpeechUseCase,
    private val microphone: MicrophonePermission,
    isVoiceInputAvailable: IsVoiceInputAvailableUseCase
) : ViewModel() {

    val characterId: String = checkNotNull(savedStateHandle[Routes.FullScreen.Chat.ARG_CHARACTER_ID])

    private val chatOpen = MutableStateFlow(false)
    private val voiceInput = MutableStateFlow(
        if (isVoiceInputAvailable()) VoiceInputState.Idle else VoiceInputState.Unavailable
    )
    private val _voiceMessage = MutableStateFlow<VoiceMessage?>(null)
    private val _permissionRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var session: Job? = null

    val uiState: StateFlow<ChatUiState> =
        combine(repository.pageState, repository.characters, chatOpen, voiceInput) { page, characters, open, voice ->
            val character = characters?.firstOrNull { it.id == characterId }
            when {
                page is PageState.Error -> ChatUiState.Error(page.error)
                character == null || !open -> ChatUiState.Loading(character)
                else -> ChatUiState.Ready(character, voice)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ChatUiState.Loading())

    /** The current voice message, until [onVoiceMessageShown]. */
    val voiceMessage: StateFlow<VoiceMessage?> = _voiceMessage.asStateFlow()

    /** Asks the screen to request `RECORD_AUDIO`; answer with [onMicPermissionResult]. */
    val permissionRequests: SharedFlow<Unit> = _permissionRequests.asSharedFlow()

    init {
        viewModelScope.launch {
            repository.openChat(characterId)
            chatOpen.value = true
        }
        viewModelScope.launch {
            chatInput.micPresses.collect { target ->
                // Only the main chat has a mic until 1.7 brings the EchoText thread.
                if (target == ChatInputTarget.Story) onMicPressed()
            }
        }
    }

    /** The in-page mic was tapped: start listening, or stop if already listening. */
    fun onMicPressed() {
        when (voiceInput.value) {
            VoiceInputState.Listening -> stopListening()
            VoiceInputState.Idle ->
                if (microphone.isGranted()) startListening() else _permissionRequests.tryEmit(Unit)
            VoiceInputState.Unavailable -> Unit
        }
    }

    fun onMicPermissionResult(granted: Boolean) {
        if (granted) startListening() else _voiceMessage.value = VoiceMessage.PermissionDenied
    }

    /** Stops recording and keeps what was recognized (tap on the mic, or the app went to the background). */
    fun stopListening() {
        session?.cancel()
    }

    fun onVoiceMessageShown() {
        _voiceMessage.value = null
    }

    private fun startListening() {
        if (session?.isActive == true || voiceInput.value != VoiceInputState.Idle) return
        voiceInput.value = VoiceInputState.Listening
        session = viewModelScope.launch {
            try {
                chatInput.beginVoiceInput(ChatInputTarget.Story)
                listenForSpeech().collect { event ->
                    when (event) {
                        is VoiceSessionEvent.Text -> chatInput.updateVoiceInput(event.text)
                        is VoiceSessionEvent.Failed -> _voiceMessage.value = event.error.toMessage()
                    }
                }
            } finally {
                // Also when stopped or when leaving Chat: the page's mic must not stay "listening".
                withContext(NonCancellable) { chatInput.endVoiceInput() }
                if (voiceInput.value == VoiceInputState.Listening) voiceInput.value = VoiceInputState.Idle
            }
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L

        fun SpeechError.toMessage(): VoiceMessage = when (this) {
            SpeechError.PermissionDenied -> VoiceMessage.PermissionDenied
            SpeechError.LanguagePackMissing -> VoiceMessage.LanguagePackMissing
            SpeechError.NoMatch -> VoiceMessage.NoMatch
            SpeechError.Unavailable, SpeechError.Busy, SpeechError.Other -> VoiceMessage.Failed
        }
    }
}
