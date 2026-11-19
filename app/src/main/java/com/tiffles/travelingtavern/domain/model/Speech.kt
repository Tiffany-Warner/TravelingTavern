package com.tiffles.travelingtavern.domain.model

/** What one pass of the on-device speech recognizer reports, in order. */
sealed interface SpeechEvent {
    /** The recognizer is ready and listening. */
    data object Listening : SpeechEvent

    /** Words so far in this pass; replaced by the next partial or the final result. */
    data class Partial(val text: String) : SpeechEvent

    /** This pass's result. The pass ends after it. */
    data class Final(val text: String) : SpeechEvent

    /** The pass ended without a result (e.g. silence). */
    data object Ended : SpeechEvent

    /** The pass failed. It ends after this. */
    data class Error(val error: SpeechError) : SpeechEvent
}

enum class SpeechError {
    /** On-device recognition isn't available on this phone. */
    Unavailable,

    /** The speech pack for the phone's language isn't installed. */
    LanguagePackMissing,

    /** Nothing was recognized. */
    NoMatch,

    /** The microphone permission isn't granted. */
    PermissionDenied,

    /** Another app is using the recognizer. */
    Busy,
    Other
}

/** What a voice input session (several recognizer passes, see `ListenForSpeechUseCase`) reports. */
sealed interface VoiceSessionEvent {
    /** Everything recognized so far in this session, ready to show in the chat input. */
    data class Text(val text: String) : VoiceSessionEvent

    /** The session failed; it ends after this. */
    data class Failed(val error: SpeechError) : VoiceSessionEvent
}

/** The chat inputs the mic can fill. Only [Story] (SillyTavern's main chat) has a mic in 1.7b. */
enum class ChatInputTarget { Story, Text }
