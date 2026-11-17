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
            "characters" -> decode(obj, BridgeMessage.Characters.serializer())
            "chatChanged" -> decode(obj, BridgeMessage.ChatChanged.serializer())
            "messageReceived" -> decode(obj, BridgeMessage.MessageReceived.serializer())
            "error" -> decode(obj, BridgeMessage.Error.serializer())
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
