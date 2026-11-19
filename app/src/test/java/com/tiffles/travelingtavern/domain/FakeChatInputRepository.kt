package com.tiffles.travelingtavern.domain

import com.tiffles.travelingtavern.domain.model.ChatInputTarget
import com.tiffles.travelingtavern.domain.repository.ChatInputRepository
import com.tiffles.travelingtavern.domain.repository.MicrophonePermission
import kotlinx.coroutines.flow.MutableSharedFlow

/** Records what would be sent to the page's chat input; emit taps on [micPresses]. */
class FakeChatInputRepository : ChatInputRepository {
    override val micPresses = MutableSharedFlow<ChatInputTarget>(extraBufferCapacity = 8)

    /** Every call in order: "begin:Story", "update:<text>", "end". */
    val calls = mutableListOf<String>()

    override suspend fun beginVoiceInput(target: ChatInputTarget) {
        calls += "begin:$target"
    }

    override suspend fun updateVoiceInput(text: String) {
        calls += "update:$text"
    }

    override suspend fun endVoiceInput() {
        calls += "end"
    }
}

class FakeMicrophonePermission(var granted: Boolean = true) : MicrophonePermission {
    override fun isGranted(): Boolean = granted
}
