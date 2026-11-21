package com.tiffles.travelingtavern.data.bridge

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.util.Base64

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

    @Test
    fun `stylesheet travels as base64 that decodes back to the exact css`() {
        val css = "body.tt-clean #chat::before { content: \"it's \\ fine\"; }\n/* ünïcode — ✓ */\n"
        val script = BridgeScript.installStyle("tt-clean-theme", css)

        val args = Json.parseToJsonElement(
            "[" + script.removePrefix("window.__ttBridge && window.__ttBridge.installStyle(").removeSuffix(");") + "]"
        ).jsonArray
        val decoded = String(Base64.getDecoder().decode(args[1].jsonPrimitive.content), Charsets.UTF_8)

        assertEquals("tt-clean-theme", args[0].jsonPrimitive.content)
        assertEquals(css, decoded)
    }

    @Test
    fun `base64 argument contains only safe characters`() {
        val script = BridgeScript.installStyle("tt-clean-theme", "a { b: \"c\" }\n'quote' \\ backslash")
        val encoded = script.substringAfter("\"tt-clean-theme\",\"").substringBefore("\");")

        assertEquals(true, encoded.matches(Regex("^[A-Za-z0-9+/=]+$")))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `function names other than plain identifiers are rejected`() {
        BridgeScript.call("selectCharacter(1);alert")
    }

    @Test
    fun `a message with quotes, backslashes, newlines, emoji and a slash command reaches the page unchanged`() {
        val texts = listOf(
            "She said \"hi\" and it's fine",
            "a back\\slash and a </script> tag",
            "line one\nline two\r\nline three",
            "emoji \uD83D\uDE00\uD83D\uDC4D\uD83C\uDFFD and line\u2028separator\u2029paragraph",
        )
        texts.forEach { text ->
            val script = BridgeScript.call("sendMessage", JsonPrimitive(text))
            val argument = script
                .removePrefix("window.__ttBridge && window.__ttBridge.sendMessage(")
                .removeSuffix(");")

            val breaksLine = listOf('\n', '\r', '\u2028', '\u2029').any { it in script }
            assertFalse("line break left in: $script", breaksLine)
            assertEquals(text, Json.parseToJsonElement(argument).jsonPrimitive.content)
        }
    }

    @Test
    fun `theme colors travel as one object`() {
        val script = BridgeScript.setTheme(PageTheme(dark = true, colors = mapOf("primaryContainer" to "#5A4300")))
        assertEquals(
            """window.__ttBridge && window.__ttBridge.setTheme({"dark":true,"colors":{"primaryContainer":"#5A4300"}});""",
            script
        )
    }

    @Test
    fun `selectCharacter with a chat file and without one`() {
        assertEquals(
            """window.__ttBridge && window.__ttBridge.selectCharacter("r1","Tide Pods.png","Tide Pods - 2026-11-19 @15h 04m",true);""",
            BridgeScript.call(
                "selectCharacter",
                JsonPrimitive("r1"),
                JsonPrimitive("Tide Pods.png"),
                JsonPrimitive("Tide Pods - 2026-11-19 @15h 04m"),
                JsonPrimitive(true)
            )
        )
        assertEquals(
            """window.__ttBridge && window.__ttBridge.selectCharacter("r1","Bob.png",null,false);""",
            BridgeScript.call("selectCharacter", JsonPrimitive("r1"), JsonPrimitive("Bob.png"), JsonNull, JsonPrimitive(false))
        )
    }

    @Test
    fun `message action and the other chat commands`() {
        assertEquals(
            """window.__ttBridge && window.__ttBridge.messageAction("c1",4,"2026-11-19T15:04:00.000Z","regenerate");""",
            BridgeScript.call(
                "messageAction",
                JsonPrimitive("c1"),
                JsonPrimitive(4),
                JsonPrimitive("2026-11-19T15:04:00.000Z"),
                JsonPrimitive("regenerate")
            )
        )
        assertEquals("window.__ttBridge && window.__ttBridge.setAppState(false);", BridgeScript.call("setAppState", JsonPrimitive(false)))
        assertEquals("window.__ttBridge && window.__ttBridge.reloadChat();", BridgeScript.call("reloadChat"))
        assertEquals("window.__ttBridge && window.__ttBridge.impersonate();", BridgeScript.call("impersonate"))
        assertEquals("window.__ttBridge && window.__ttBridge.stopGeneration();", BridgeScript.call("stopGeneration"))
        assertEquals(
            """window.__ttBridge && window.__ttBridge.newChat("n1",true);""",
            BridgeScript.call("newChat", JsonPrimitive("n1"), JsonPrimitive(true))
        )
        assertEquals(
            """window.__ttBridge && window.__ttBridge.deleteChat("d1","Bob.png","Bob - \"quoted\"");""",
            BridgeScript.call("deleteChat", JsonPrimitive("d1"), JsonPrimitive("Bob.png"), JsonPrimitive("Bob - \"quoted\""))
        )
    }

    @Test
    fun `persona input leaves out fields it doesn't change`() {
        val json = PersonaInput(description = "Says \"{{user}}\" | twice").toJson()

        assertEquals(setOf("description"), json.keys)
        assertEquals("Says \"{{user}}\" | twice", json.getValue("description").jsonPrimitive.content)
        assertEquals(
            setOf("name", "title", "description", "picture"),
            PersonaInput("Ash", "", "", "data:image/jpeg;base64,AA==").toJson().keys
        )
    }

    @Test
    fun `select persona passes its id and the lock flag`() {
        assertEquals(
            "window.__ttBridge && window.__ttBridge.selectPersona(\"pe1\",\"1-Mira.png\",true);",
            BridgeScript.call("selectPersona", JsonPrimitive("pe1"), JsonPrimitive("1-Mira.png"), JsonPrimitive(true))
        )
    }

    @Test
    fun `lease calls are guarded by the lease's presence`() {
        assertEquals("window.__ttLease && window.__ttLease.useHere();", BridgeScript.leaseCall("useHere"))
        assertEquals("window.__ttLease && window.__ttLease.onForeground();", BridgeScript.leaseCall("onForeground"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `only the lease's own functions can be called`() {
        BridgeScript.leaseCall("start")
    }
}
