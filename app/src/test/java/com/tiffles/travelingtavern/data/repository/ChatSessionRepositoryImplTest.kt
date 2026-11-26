package com.tiffles.travelingtavern.data.repository

import android.util.Log
import com.tiffles.travelingtavern.data.bridge.BridgeClient
import com.tiffles.travelingtavern.data.bridge.BridgeRepository
import com.tiffles.travelingtavern.data.bridge.LeaseStateStore
import com.tiffles.travelingtavern.domain.model.ChatActionResult
import com.tiffles.travelingtavern.domain.model.MessageAction
import com.tiffles.travelingtavern.domain.tappedMessage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatSessionRepositoryImplTest {

    private val bridge = BridgeRepository(LeaseStateStore())
    private val client: BridgeClient = mockk(relaxed = true)
    private val repository = ChatSessionRepositoryImpl(bridge, client)

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    /** Answers every messageAction with [json] fields for its own request ID. */
    private fun answerActions(fields: String) {
        coEvery { client.messageAction(any(), any(), any(), any()) } coAnswers {
            bridge.onRawMessage("""{"v":1,"type":"chatResult","requestId":"other","ok":false,"error":"failed"}""")
            bridge.onRawMessage("""{"v":1,"type":"chatResult","requestId":"${firstArg<String>()}",$fields}""")
            true
        }
    }

    @Test
    fun `an action sends the message's index, date and wire name, and matches its own answer`() = runTest {
        answerActions(""""ok":true,"text":"Hello"""")

        val result = repository.runAction(tappedMessage(id = 4), MessageAction.Copy)

        assertEquals(ChatActionResult.Done("Hello"), result)
        coVerify { client.messageAction(any(), 4, tappedMessage(id = 4).sendDate, "copy") }
    }

    @Test
    fun `stale and busy answers are told apart from failures`() = runTest {
        answerActions(""""ok":false,"error":"stale"""")
        assertEquals(ChatActionResult.Stale, repository.runAction(tappedMessage(), MessageAction.Delete))

        answerActions(""""ok":false,"error":"busy"""")
        assertEquals(ChatActionResult.Busy, repository.runAction(tappedMessage(), MessageAction.Edit))

        answerActions(""""ok":false,"error":"failed"""")
        assertEquals(ChatActionResult.Failed, repository.runAction(tappedMessage(), MessageAction.Branch))
    }

    @Test
    fun `no answer in time is a failure`() = runTest {
        assertEquals(ChatActionResult.Failed, repository.runAction(tappedMessage(), MessageAction.Hide))
    }

    @Test
    fun `restart asks SillyTavern to delete the current chat`() = runTest {
        coEvery { client.newChat(any(), true) } coAnswers {
            bridge.onRawMessage("""{"v":1,"type":"chatResult","requestId":"${firstArg<String>()}","ok":true}""")
            true
        }

        assertEquals(ChatActionResult.Done(), repository.startNewChat(deleteCurrent = true))
    }

    @Test
    fun `regenerate reply matches its own answer, stale when the last message isn't the user's`() = runTest {
        coEvery { client.regenerateReply(any()) } coAnswers {
            bridge.onRawMessage("""{"v":1,"type":"chatResult","requestId":"other","ok":true}""")
            bridge.onRawMessage("""{"v":1,"type":"chatResult","requestId":"${firstArg<String>()}","ok":false,"error":"stale"}""")
            true
        }

        assertEquals(ChatActionResult.Stale, repository.regenerateReply())
    }

    @Test
    fun `regenerate reply refused by the pre-check fails at once`() = runTest {
        coEvery { client.regenerateReply(any()) } returns false

        assertEquals(ChatActionResult.Failed, repository.regenerateReply())
    }

    @Test
    fun `generation state follows the page and resets on a reload`() = runTest {
        bridge.onRawMessage("""{"v":1,"type":"generationState","running":true}""")
        assertTrue(bridge.generating.value)

        bridge.onPageStarted()
        assertFalse(bridge.generating.value)
    }
}
