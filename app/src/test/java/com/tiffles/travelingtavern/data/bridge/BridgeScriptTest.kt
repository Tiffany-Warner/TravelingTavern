package com.tiffles.travelingtavern.data.bridge

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class BridgeScriptTest {

    private val prefix = "window.__ttBridge && window.__ttBridge.selectCharacter("

    /** Pulls the single argument back out of the generated call and decodes it as JSON. */
    private fun roundTrip(id: String): String {
        val script = BridgeScript.call("selectCharacter", JsonPrimitive(id))
        val argument = script.removePrefix(prefix).removeSuffix(");")
        return Json.parseToJsonElement(argument).jsonPrimitive.content
    }

    @Test
    fun `call without arguments`() {
        assertEquals(
            "window.__ttBridge && window.__ttBridge.requestCharacters();",
            BridgeScript.call("requestCharacters")
        )
    }

    @Test
    fun `plain id is a quoted string literal`() {
        assertEquals("$prefix\"Bob.png\");", BridgeScript.call("selectCharacter", JsonPrimitive("Bob.png")))
    }

    @Test
    fun `quotes, backslashes and script breakers survive unchanged`() {
        val ids = listOf(
            "O'Brien \"The Bard\".png",
            "back\\slash.png",
            "\"); alert(1); (\"",
            "</script><script>alert(1)</script>",
            "line\nbreak\ttab.png"
        )
        ids.forEach { assertEquals(it, roundTrip(it)) }
    }

    @Test
    fun `spaces and unicode survive unchanged`() {
        val ids = listOf("Seraphina the Kind.png", "Ангел.png", "雪.png", "Rosé 🌹.png")
        ids.forEach { assertEquals(it, roundTrip(it)) }
    }

    @Test
    fun `line and paragraph separators are escaped`() {
        val id = "a b c"
        val script = BridgeScript.call("selectCharacter", JsonPrimitive(id))

        assertFalse(script.contains(' '))
        assertFalse(script.contains(' '))
        assertEquals(id, roundTrip(id))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `function names other than plain identifiers are rejected`() {
        BridgeScript.call("selectCharacter(1);alert")
    }
}
