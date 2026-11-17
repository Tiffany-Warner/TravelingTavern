package com.tiffles.travelingtavern.data.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BridgeMessageParserTest {

    private fun parse(raw: String) = BridgeMessageParser.parse(raw)

    @Test
    fun `ready`() {
        assertEquals(BridgeMessage.Ready, parse("""{"v":1,"type":"ready"}"""))
    }

    @Test
    fun `characters with and without a last chat time`() {
        val raw = """
            {"v":1,"type":"characters","items":[
              {"id":"Seraphina.png","name":"Seraphina","avatarUrl":"http://h:8000/thumbnail?type=avatar&file=Seraphina.png","lastChatAt":1794698573120},
              {"id":"Bob.png","name":"Bob","avatarUrl":"http://h:8000/thumbnail?type=avatar&file=Bob.png","lastChatAt":null},
              {"id":"New.png","name":"New","avatarUrl":"http://h:8000/thumbnail?type=avatar&file=New.png"}
            ]}
        """.trimIndent()

        val message = parse(raw) as BridgeMessage.Characters

        assertEquals(listOf("Seraphina.png", "Bob.png", "New.png"), message.items.map { it.id })
        assertEquals(1794698573120L, message.items[0].lastChatAt)
        assertNull(message.items[1].lastChatAt)
        assertNull(message.items[2].lastChatAt)
    }

    @Test
    fun `chat changed with a character and with none`() {
        assertEquals(
            BridgeMessage.ChatChanged(characterId = "Bob.png"),
            parse("""{"v":1,"type":"chatChanged","characterId":"Bob.png"}""")
        )
        assertEquals(
            BridgeMessage.ChatChanged(characterId = null),
            parse("""{"v":1,"type":"chatChanged","characterId":null}""")
        )
    }

    @Test
    fun `message received`() {
        assertEquals(
            BridgeMessage.MessageReceived(characterId = "Bob.png", messageId = 14),
            parse("""{"v":1,"type":"messageReceived","characterId":"Bob.png","messageId":14}""")
        )
    }

    @Test
    fun `error`() {
        assertEquals(
            BridgeMessage.Error(message = "Unknown character"),
            parse("""{"v":1,"type":"error","message":"Unknown character"}""")
        )
    }

    @Test
    fun `unknown type is kept as unknown, not dropped`() {
        assertEquals(BridgeMessage.Unknown("somethingNew"), parse("""{"v":2,"type":"somethingNew","x":1}"""))
    }

    @Test
    fun `extra fields are ignored`() {
        assertEquals(BridgeMessage.Ready, parse("""{"v":1,"type":"ready","extra":{"a":1}}"""))
    }

    @Test
    fun `malformed json is dropped`() {
        assertNull(parse("not json"))
        assertNull(parse("""{"v":1,"type":"ready""""))
        assertNull(parse("[]"))
        assertNull(parse(""))
    }

    @Test
    fun `missing version or type is dropped`() {
        assertNull(parse("""{"type":"ready"}"""))
        assertNull(parse("""{"v":"one","type":"ready"}"""))
        assertNull(parse("""{"v":1}"""))
        assertNull(parse("""{"v":1,"type":5}"""))
    }

    @Test
    fun `missing or wrongly typed required fields are dropped`() {
        assertNull(parse("""{"v":1,"type":"characters"}"""))
        assertNull(parse("""{"v":1,"type":"characters","items":[{"id":"Bob.png"}]}"""))
        assertNull(parse("""{"v":1,"type":"messageReceived","characterId":"Bob.png"}"""))
        assertNull(parse("""{"v":1,"type":"messageReceived","messageId":"fourteen"}"""))
        assertNull(parse("""{"v":1,"type":"error"}"""))
    }
}
