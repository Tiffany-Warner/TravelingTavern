package com.tiffles.travelingtavern.data.bridge

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.util.Base64

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

    /** `window.__ttLease && window.__ttLease.fn();` — the session lease's page API (E11). */
    fun leaseCall(function: String): String {
        require(function in LEASE_FUNCTIONS) { "Not a lease function: $function" }
        return "window.__ttLease && window.__ttLease.$function();"
    }

    /**
     * Adds or replaces `<style id=[id]>` with [css]. The stylesheet travels Base64-encoded
     * (UTF-8) and is decoded in the page, so its quotes and newlines can't break the script.
     */
    fun installStyle(id: String, css: String): String =
        call("installStyle", JsonPrimitive(id), JsonPrimitive(encodeBase64(css)))

    /** `setTheme({ dark, colors: { name: "#rrggbb" } })` for the clean layout's `--tt-*` variables. */
    fun setTheme(theme: PageTheme): String =
        call(
            "setTheme",
            buildJsonObject {
                put("dark", JsonPrimitive(theme.dark))
                put("colors", JsonObject(theme.colors.mapValues { JsonPrimitive(it.value) }))
            }
        )

    fun encodeBase64(text: String): String =
        Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))

    /**
     * JSON is valid JavaScript except that older engines treat U+2028/U+2029 as line
     * breaks inside string literals, so those are escaped too.
     */
    private fun JsonElement.toJsLiteral(): String =
        Json.encodeToString(JsonElement.serializer(), this)
            .replace(" ", "\\u2028")
            .replace(" ", "\\u2029")

    private val LEASE_FUNCTIONS = setOf("useHere", "onForeground")
    private val FUNCTION_NAME = Regex("^[A-Za-z][A-Za-z0-9]*$")
}
