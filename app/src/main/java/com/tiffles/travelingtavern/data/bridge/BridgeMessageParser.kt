package com.tiffles.travelingtavern.data.bridge

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Parses the JSON strings `bridge.js` posts: `{ "v": 1, "type": "...", ... }`.
 * Never throws — anything that isn't a valid message comes back as null.
 */
object BridgeMessageParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(raw: String): BridgeMessage? {
        val obj = try {
            json.parseToJsonElement(raw).jsonObject
        } catch (_: SerializationException) {
            return null
        } catch (_: IllegalArgumentException) {
            return null
        }
        // Every message carries a version; a missing one means it isn't ours.
        if ((obj["v"] as? JsonPrimitive)?.intOrNull == null) return null
        val type = (obj["type"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
        return when (type) {
            "ready" -> BridgeMessage.Ready
            "chatSaved" -> BridgeMessage.ChatSaved
            "characters" -> decode(obj, BridgeMessage.Characters.serializer())
            "recentChats" -> decode(obj, BridgeMessage.RecentChats.serializer())
            "chatChanged" -> decode(obj, BridgeMessage.ChatChanged.serializer())
            "chatOpened" -> decode(obj, BridgeMessage.ChatOpened.serializer())
            "messageReceived" -> decode(obj, BridgeMessage.MessageReceived.serializer())
            "lorebooks" -> decode(obj, BridgeMessage.Lorebooks.serializer())
            "lorebookEntries" -> decode(obj, BridgeMessage.LorebookEntries.serializer())
            "lorebookResult" -> decode(obj, BridgeMessage.LorebookResult.serializer())
            "personas" -> decode(obj, BridgeMessage.Personas.serializer())
            "personaResult" -> decode(obj, BridgeMessage.PersonaResult.serializer())
            "tokenCount" -> decode(obj, BridgeMessage.TokenCount.serializer())
            "generationState" -> decode(obj, BridgeMessage.GenerationState.serializer())
            "messageTapped" -> decode(obj, BridgeMessage.MessageTapped.serializer())
            "chatResult" -> decode(obj, BridgeMessage.ChatResult.serializer())
            "impersonateReady" -> decode(obj, BridgeMessage.ImpersonateReady.serializer())
            "error" -> decode(obj, BridgeMessage.Error.serializer())
            "leaseState" -> decode(obj, BridgeMessage.LeaseState.serializer())
            "leaseInfo" -> decode(obj, BridgeMessage.LeaseInfo.serializer())
            "leaseNotice" -> decode(obj, BridgeMessage.LeaseNotice.serializer())
            "connectResult" -> decode(obj, BridgeMessage.ConnectResult.serializer())
            else -> BridgeMessage.Unknown(type)
        }
    }

    private fun <T : BridgeMessage> decode(obj: JsonObject, strategy: DeserializationStrategy<T>): T? =
        try {
            json.decodeFromJsonElement(strategy, obj)
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
}
