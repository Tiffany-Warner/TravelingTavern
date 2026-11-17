package com.tiffles.travelingtavern.data.bridge

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Builds the JavaScript that calls `window.__ttBridge`. Arguments are JSON-encoded, never
 * concatenated, so any string (quotes, newlines, unicode) arrives in JS exactly as sent.
 */
object BridgeScript {

    /** `window.__ttBridge && window.__ttBridge.fn(arg1, arg2);` — a no-op if the bridge isn't there. */
    fun call(function: String, vararg args: JsonElement): String {
        require(FUNCTION_NAME.matches(function)) { "Not a bridge function name: $function" }
        val encodedArgs = args.joinToString(",") { it.toJsLiteral() }
        return "window.__ttBridge && window.__ttBridge.$function($encodedArgs);"
    }

    /**
     * JSON is valid JavaScript except that older engines treat U+2028/U+2029 as line
     * breaks inside string literals, so those are escaped too.
     */
    private fun JsonElement.toJsLiteral(): String =
        Json.encodeToString(JsonElement.serializer(), this)
            .replace(" ", "\\u2028")
            .replace(" ", "\\u2029")

    private val FUNCTION_NAME = Regex("^[A-Za-z][A-Za-z0-9]*$")
}
