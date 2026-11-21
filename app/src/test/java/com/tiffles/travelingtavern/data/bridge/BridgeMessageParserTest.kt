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
              {"characterId":"Bob.png","chatFile":"Bob - 2026-11-19","lastMessage":"Hi there","lastMessageAt":1794925197539,"messageCount":17},
              {"characterId":"Oliver.png","lastMessage":null,"lastMessageAt":1794943178395,"messageCount":0},
              {"characterId":"New.png"}
            ]}
        """.trimIndent()

        val message = parse(raw) as BridgeMessage.RecentChats

        assertEquals(
            listOf(
                RecentChatDto("Bob.png", chatFile = "Bob - 2026-11-19", lastMessage = "Hi there", lastMessageAt = 1794925197539L, messageCount = 17),
                RecentChatDto("Oliver.png", lastMessage = null, lastMessageAt = 1794943178395L, messageCount = 0),
                RecentChatDto("New.png")
            ),
            message.items
        )
    }

    @Test
    fun `recent chats with a row missing its character id are rejected as malformed`() {
        assertNull(parse("""{"v":1,"type":"recentChats","items":[{"lastMessage":"x"}]}"""))
    }

    @Test
    fun `chat opened needs a request id and a character id, the chat file is optional`() {
        assertEquals(
            BridgeMessage.ChatOpened(requestId = "r1", characterId = "Tide Pods.png", chatFile = "Tide Pods - 2026-11-19"),
            parse("""{"v":1,"type":"chatOpened","requestId":"r1","characterId":"Tide Pods.png","chatFile":"Tide Pods - 2026-11-19"}""")
        )
        assertEquals(
            BridgeMessage.ChatOpened(requestId = "r1", characterId = "Tide Pods.png"),
            parse("""{"v":1,"type":"chatOpened","requestId":"r1","characterId":"Tide Pods.png","chatFile":null}""")
        )
        assertNull(parse("""{"v":1,"type":"chatOpened","characterId":"Tide Pods.png"}"""))
        assertNull(parse("""{"v":1,"type":"chatOpened","requestId":"r1"}"""))
    }

    @Test
    fun `chat changed with a character and with none`() {
        assertEquals(
            BridgeMessage.ChatChanged(characterId = "Bob.png", chatFile = "Bob - 1"),
            parse("""{"v":1,"type":"chatChanged","characterId":"Bob.png","chatFile":"Bob - 1"}""")
        )
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

    @Test
    fun `lorebooks with links, a missing count and defaults`() {
        val raw = """
            {"v":1,"type":"lorebooks","items":[
              {"name":"Eldoria","entryCount":4,"global":false,"characterIds":["default_Seraphina.png"],"personaNames":[]},
              {"name":"Rules","entryCount":null,"global":true,"characterIds":[],"personaNames":[""]},
              {"name":"Bare"}
            ]}
        """.trimIndent()

        val items = (parse(raw) as BridgeMessage.Lorebooks).items

        assertEquals(LorebookDto("Eldoria", 4, false, listOf("default_Seraphina.png"), emptyList()), items[0])
        assertEquals(LorebookDto("Rules", null, true, emptyList(), listOf("")), items[1])
        assertEquals(LorebookDto("Bare"), items[2])
    }

    @Test
    fun `lorebook entries`() {
        val raw = """
            {"v":1,"type":"lorebookEntries","requestId":"lb1","name":"Eldoria","entries":[
              {"uid":0,"title":"eldoria","keys":["eldoria","wood"],"content":"A forest","enabled":true},
              {"uid":3,"title":"","keys":[],"content":"","enabled":false}
            ]}
        """.trimIndent()

        val message = parse(raw) as BridgeMessage.LorebookEntries

        assertEquals("lb1", message.requestId)
        assertEquals(LorebookEntryDto(0, "eldoria", listOf("eldoria", "wood"), "A forest", true), message.entries[0])
        assertEquals(false, message.entries[1].enabled)
    }

    @Test
    fun `lorebook results and token counts`() {
        assertEquals(
            BridgeMessage.LorebookResult(requestId = "lb2", ok = true, name = "Kingdoms"),
            parse("""{"v":1,"type":"lorebookResult","requestId":"lb2","ok":true,"name":"Kingdoms"}""")
        )
        assertEquals(
            BridgeMessage.LorebookResult(requestId = "lb3", ok = false, error = "exists"),
            parse("""{"v":1,"type":"lorebookResult","requestId":"lb3","ok":false,"error":"exists"}""")
        )
        assertEquals(
            BridgeMessage.LorebookResult(requestId = "lb4", ok = true, uid = 7),
            parse("""{"v":1,"type":"lorebookResult","requestId":"lb4","ok":true,"uid":7}""")
        )
        assertEquals(BridgeMessage.TokenCount("lb5", 16), parse("""{"v":1,"type":"tokenCount","requestId":"lb5","count":16}"""))
    }

    @Test
    fun `personas with badges, and defaults for missing fields`() {
        val raw = """
            {"v":1,"type":"personas","items":[
              {"avatarId":"1-Mira.png","name":"Mira","title":"Bard","description":"Sings.","pictureUrl":"http://100.64.0.2:8000/thumbnail?type=persona&file=1-Mira.png","isActive":true,"isDefault":true},
              {"avatarId":"user-default.png"}
            ]}
        """.trimIndent()

        val items = (parse(raw) as BridgeMessage.Personas).items

        assertEquals(
            PersonaDto("1-Mira.png", "Mira", "Bard", "Sings.", "http://100.64.0.2:8000/thumbnail?type=persona&file=1-Mira.png", true, true),
            items[0]
        )
        assertEquals(PersonaDto("user-default.png"), items[1])
    }

    @Test
    fun `persona results`() {
        assertEquals(
            BridgeMessage.PersonaResult(requestId = "pe1", ok = true, avatarId = "123-Ash.png"),
            parse("""{"v":1,"type":"personaResult","requestId":"pe1","ok":true,"avatarId":"123-Ash.png"}""")
        )
        assertEquals(
            BridgeMessage.PersonaResult(requestId = "pe2", ok = false, error = "notFound"),
            parse("""{"v":1,"type":"personaResult","requestId":"pe2","ok":false,"error":"notFound"}""")
        )
        val details = parse("""{"v":1,"type":"personaResult","requestId":"pe3","ok":true,"persona":{"avatarId":"a.png","name":"A","description":"Full text"}}""")
        assertEquals("Full text", (details as BridgeMessage.PersonaResult).persona?.description)
    }

    @Test
    fun `malformed persona messages are dropped`() {
        assertNull(parse("""{"v":1,"type":"personas"}"""))
        assertNull(parse("""{"v":1,"type":"personas","items":[{"name":"No id"}]}"""))
        assertNull(parse("""{"v":1,"type":"personas","items":[{"avatarId":"a.png","isActive":"yes"}]}"""))
        assertNull(parse("""{"v":1,"type":"personaResult","ok":true}"""))
    }

    @Test
    fun `malformed lorebook messages are dropped`() {
        assertNull(parse("""{"v":1,"type":"lorebooks","items":[{"entryCount":3}]}"""))
        assertNull(parse("""{"v":1,"type":"lorebookEntries","requestId":"lb1","name":"E","entries":[{"title":"no uid"}]}"""))
        assertNull(parse("""{"v":1,"type":"lorebookResult","ok":true}"""))
        assertNull(parse("""{"v":1,"type":"tokenCount","requestId":"lb5","count":"many"}"""))
    }

    @Test
    fun `mic presses from an old page are an unknown type now`() {
        assertEquals(BridgeMessage.Unknown("micPressed"), parse("""{"v":1,"type":"micPressed","target":"story"}"""))
    }

    @Test
    fun `generation state`() {
        assertEquals(BridgeMessage.GenerationState(true), parse("""{"v":1,"type":"generationState","running":true}"""))
        assertEquals(BridgeMessage.GenerationState(false), parse("""{"v":1,"type":"generationState","running":false}"""))
        assertNull(parse("""{"v":1,"type":"generationState"}"""))
        assertNull(parse("""{"v":1,"type":"generationState","running":"yes"}"""))
    }

    @Test
    fun `message tapped with all fields, with defaults, and malformed`() {
        assertEquals(
            BridgeMessage.MessageTapped(
                messageId = 4, sendDate = "2026-11-19T15:04:00.000Z", name = "Seraphina", isUser = false,
                isLatest = true, isHidden = false, canRegenerate = true, canContinue = true
            ),
            parse(
                """{"v":1,"type":"messageTapped","messageId":4,"sendDate":"2026-11-19T15:04:00.000Z","name":"Seraphina",
                "isUser":false,"isLatest":true,"isHidden":false,"canRegenerate":true,"canContinue":true}"""
            )
        )
        assertEquals(
            BridgeMessage.MessageTapped(messageId = 0, sendDate = "", isUser = true, isLatest = false),
            parse("""{"v":1,"type":"messageTapped","messageId":0,"sendDate":"","isUser":true,"isLatest":false}""")
        )
        assertNull(parse("""{"v":1,"type":"messageTapped","sendDate":"x","isUser":true,"isLatest":false}"""))
        assertNull(parse("""{"v":1,"type":"messageTapped","messageId":"four","sendDate":"x","isUser":true,"isLatest":false}"""))
    }

    @Test
    fun `chat results, ok with copied text and failed with a reason`() {
        assertEquals(
            BridgeMessage.ChatResult(requestId = "c1", ok = true, text = "Hello"),
            parse("""{"v":1,"type":"chatResult","requestId":"c1","ok":true,"text":"Hello"}""")
        )
        assertEquals(
            BridgeMessage.ChatResult(requestId = "c2", ok = false, error = "stale"),
            parse("""{"v":1,"type":"chatResult","requestId":"c2","ok":false,"error":"stale"}""")
        )
        assertNull(parse("""{"v":1,"type":"chatResult","ok":true}"""))
    }

    @Test
    fun `impersonate ready carries its text`() {
        assertEquals(
            BridgeMessage.ImpersonateReady("I wave back."),
            parse("""{"v":1,"type":"impersonateReady","text":"I wave back."}""")
        )
        assertNull(parse("""{"v":1,"type":"impersonateReady"}"""))
    }

    // Session lease (extension spec E11, as posted by session-lease.js v0.2.0-rc.1)

    private val parkedState =
        """{"v":1,"type":"leaseState","state":"parked","reason":"held","pageId":"t1.l1","epoch":null,""" +
            """"busy":false,"connected":true,"origin":"http://192.0.2.1:8000"}"""

    @Test
    fun `leaseState with every field`() {
        val raw = """{"v":1,"type":"leaseState","state":"holder","reason":null,"pageId":"t1.l2","epoch":7,""" +
            """"busy":true,"connected":false,"origin":"http://192.0.2.1:8000"}"""

        assertEquals(
            BridgeMessage.LeaseState(
                state = "holder",
                reason = null,
                pageId = "t1.l2",
                epoch = 7L,
                busy = true,
                connected = false,
                origin = "http://192.0.2.1:8000"
            ),
            parse(raw)
        )
    }

    @Test
    fun `leaseState parked with a null epoch`() {
        val message = parse(parkedState) as BridgeMessage.LeaseState

        assertEquals("parked", message.state)
        assertEquals("held", message.reason)
        assertNull(message.epoch)
    }

    @Test
    fun `leaseState keeps an unknown state and reason`() {
        val message = parse(
            parkedState.replace("\"parked\"", "\"sleeping\"").replace("\"held\"", "\"napping\"")
        ) as BridgeMessage.LeaseState

        assertEquals("sleeping", message.state)
        assertEquals("napping", message.reason)
    }

    @Test
    fun `leaseState ignores unknown fields`() {
        val message = parse(parkedState.replace("\"busy\"", "\"since\":5,\"busy\""))

        assertEquals("t1.l1", (message as BridgeMessage.LeaseState).pageId)
    }

    @Test
    fun `leaseState missing a field or with a wrong type is dropped`() {
        assertNull(parse(parkedState.replace(",\"origin\":\"http://192.0.2.1:8000\"", "")))
        assertNull(parse(parkedState.replace("\"reason\":\"held\",", "")))
        assertNull(parse(parkedState.replace("\"epoch\":null", "\"epoch\":\"three\"")))
        assertNull(parse(parkedState.replace("\"busy\":false", "\"busy\":\"no\"")))
        assertNull(parse(parkedState.replace("\"pageId\":\"t1.l1\"", "\"pageId\":null")))
    }

    @Test
    fun `leaseInfo with an extension and without one`() {
        assertEquals(
            BridgeMessage.LeaseInfo(
                leaseVersion = 1,
                extensionVersion = "0.2.0",
                extensionLeaseVersion = 1,
                stVersion = "1.19.1",
                untestedServer = false
            ),
            parse(
                """{"v":1,"type":"leaseInfo","leaseVersion":1,"extensionVersion":"0.2.0",""" +
                    """"extensionLeaseVersion":1,"stVersion":"1.19.1","untestedServer":false}"""
            )
        )
        assertEquals(
            BridgeMessage.LeaseInfo(
                leaseVersion = 1,
                extensionVersion = null,
                extensionLeaseVersion = null,
                stVersion = null,
                untestedServer = true
            ),
            parse(
                """{"v":1,"type":"leaseInfo","leaseVersion":1,"extensionVersion":null,""" +
                    """"extensionLeaseVersion":null,"stVersion":null,"untestedServer":true}"""
            )
        )
    }

    @Test
    fun `leaseInfo missing its version or with a wrong type is dropped`() {
        assertNull(
            parse(
                """{"v":1,"type":"leaseInfo","extensionVersion":null,"extensionLeaseVersion":null,""" +
                    """"stVersion":null,"untestedServer":true}"""
            )
        )
        assertNull(
            parse(
                """{"v":1,"type":"leaseInfo","leaseVersion":"one","extensionVersion":null,""" +
                    """"extensionLeaseVersion":null,"stVersion":null,"untestedServer":true}"""
            )
        )
    }

    @Test
    fun `leaseNotice keeps known and unknown kinds`() {
        assertEquals(
            BridgeMessage.LeaseNotice("replyNotStarted"),
            parse("""{"v":1,"type":"leaseNotice","kind":"replyNotStarted"}""")
        )
        assertEquals(
            BridgeMessage.LeaseNotice("notConnected"),
            parse("""{"v":1,"type":"leaseNotice","kind":"notConnected"}""")
        )
        assertEquals(
            BridgeMessage.LeaseNotice("somethingNew"),
            parse("""{"v":1,"type":"leaseNotice","kind":"somethingNew"}""")
        )
        assertNull(parse("""{"v":1,"type":"leaseNotice"}"""))
    }

    @Test
    fun `lease messages without a version are not ours`() {
        assertNull(parse(parkedState.replace("\"v\":1,", "")))
    }
}
