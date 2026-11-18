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
    fun `characters carry the full-size avatar when the bridge sends it`() {
        val raw = """
            {"v":1,"type":"characters","items":[
              {"id":"Bob.png","name":"Bob","avatarUrl":"http://h:8000/thumbnail?type=avatar&file=Bob.png","fullAvatarUrl":"http://h:8000/characters/Bob.png"},
              {"id":"Old.png","name":"Old","avatarUrl":"http://h:8000/thumbnail?type=avatar&file=Old.png"}
            ]}
        """.trimIndent()

        val message = parse(raw) as BridgeMessage.Characters

        assertEquals("http://h:8000/characters/Bob.png", message.items[0].fullAvatarUrl)
        assertNull(message.items[1].fullAvatarUrl)
    }

    @Test
    fun `recent chats with a message, an empty chat and missing fields`() {
        val raw = """
            {"v":1,"type":"recentChats","items":[
              {"characterId":"Bob.png","lastMessage":"Hi there","lastMessageAt":1794925197539,"messageCount":17},
              {"characterId":"Oliver.png","lastMessage":null,"lastMessageAt":1794943178395,"messageCount":0},
              {"characterId":"New.png"}
            ]}
        """.trimIndent()

        val message = parse(raw) as BridgeMessage.RecentChats

        assertEquals(
            listOf(
                RecentChatDto("Bob.png", "Hi there", 1794925197539L, 17),
                RecentChatDto("Oliver.png", null, 1794943178395L, 0),
                RecentChatDto("New.png", null, null, 0)
            ),
            message.items
        )
    }

    @Test
    fun `recent chats with a row missing its character id are rejected as malformed`() {
        assertNull(parse("""{"v":1,"type":"recentChats","items":[{"lastMessage":"x"}]}"""))
    }

    @Test
    fun `chat opened needs a character id`() {
        assertEquals(
            BridgeMessage.ChatOpened(characterId = "Tide Pods.png"),
            parse("""{"v":1,"type":"chatOpened","characterId":"Tide Pods.png"}""")
        )
        assertNull(parse("""{"v":1,"type":"chatOpened"}"""))
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
