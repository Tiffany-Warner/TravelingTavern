package com.tiffles.travelingtavern.data.repository

import android.util.Log
import com.tiffles.travelingtavern.data.bridge.BridgeClient
import com.tiffles.travelingtavern.data.bridge.BridgeMessage
import com.tiffles.travelingtavern.data.bridge.BridgeRepository
import com.tiffles.travelingtavern.domain.model.ChatInputTarget
import com.tiffles.travelingtavern.domain.repository.ChatInputRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.mapNotNull
import javax.inject.Inject
import javax.inject.Singleton

/** [ChatInputRepository] through the JS bridge: the mic button and the input live in the page. */
@Singleton
class ChatInputRepositoryImpl @Inject constructor(
    private val bridge: BridgeRepository,
    private val client: BridgeClient
) : ChatInputRepository {

    override val micPresses: Flow<ChatInputTarget> = bridge.messages
        .filterIsInstance<BridgeMessage.MicPressed>()
        .mapNotNull { message ->
            message.target.toChatInputTarget().also {
                if (it == null) Log.d(TAG, "Ignored micPressed with an unknown target")
            }
        }

    override suspend fun beginVoiceInput(target: ChatInputTarget) = client.beginVoiceInput(target.wireName())

    override suspend fun updateVoiceInput(text: String) = client.updateVoiceInput(text)

    override suspend fun endVoiceInput() = client.endVoiceInput()

    private companion object {
        const val TAG = "ChatInputRepository"

        fun String.toChatInputTarget(): ChatInputTarget? = when (this) {
            "story" -> ChatInputTarget.Story
            "text" -> ChatInputTarget.Text
            else -> null
        }

        fun ChatInputTarget.wireName(): String = when (this) {
            ChatInputTarget.Story -> "story"
            ChatInputTarget.Text -> "text"
        }
    }
}
