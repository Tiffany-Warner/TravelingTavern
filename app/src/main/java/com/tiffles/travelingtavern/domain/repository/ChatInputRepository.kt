package com.tiffles.travelingtavern.domain.repository

import com.tiffles.travelingtavern.domain.model.ChatInputTarget
import kotlinx.coroutines.flow.Flow

/** The chat input inside SillyTavern's page, as far as voice input needs it. */
interface ChatInputRepository {

    /** Each tap on the in-page mic button, with the input it belongs to. */
    val micPresses: Flow<ChatInputTarget>

    /**
     * Starts filling [target]: its current text becomes the base that recognized text is added
     * to, and the mic button shows that it's listening.
     */
    suspend fun beginVoiceInput(target: ChatInputTarget)

    /** Sets the input to the base plus [text], the way typing would. */
    suspend fun updateVoiceInput(text: String)

    /** Ends filling: the mic button goes back to idle and the cursor sits after the text. */
    suspend fun endVoiceInput()
}

/** Whether the app may record audio. Checked right before listening; never cached. */
interface MicrophonePermission {
    fun isGranted(): Boolean
}
