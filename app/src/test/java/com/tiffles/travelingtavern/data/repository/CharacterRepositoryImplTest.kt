package com.tiffles.travelingtavern.data.repository

import android.util.Log
import com.tiffles.travelingtavern.data.bridge.BridgeClient
import com.tiffles.travelingtavern.data.bridge.BridgeRepository
import com.tiffles.travelingtavern.data.webview.WebViewHolder
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CharacterRepositoryImplTest {

    private val bridge = BridgeRepository()
    private val client: BridgeClient = mockk(relaxed = true)
    private val repository = CharacterRepositoryImpl(bridge, client, mockk<WebViewHolder>(relaxed = true))

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

    private fun pageReady() = bridge.onRawMessage("""{"v":1,"type":"ready"}""")

    private fun chatOpened(id: String) =
        bridge.onRawMessage("""{"v":1,"type":"chatOpened","characterId":"$id"}""")

    @Test
    fun `returns as soon as that character's chat is opened`() = runTest {
        pageReady()
        coEvery { client.selectCharacter("Bob.png") } coAnswers {
            chatOpened("Seraphina.png") // a different chat doesn't count
            chatOpened("Bob.png")
        }

        repository.openChat("Bob.png")

        assertEquals(0L, currentTime)
        verify(exactly = 0) { Log.w(any<String>(), any<String>()) }
    }

    @Test
    fun `gives up after 3 s and logs it`() = runTest {
        pageReady()
        var done = false
        launch {
            repository.openChat("Bob.png")
            done = true
        }

        advanceTimeBy(CharacterRepositoryImpl.OPEN_CHAT_TIMEOUT_MS - 1)
        runCurrent()
        assertFalse(done)

        advanceTimeBy(2)
        runCurrent()
        assertTrue(done)
        verify(exactly = 1) { Log.w(any<String>(), any<String>()) }
    }

    @Test
    fun `waiting for the page to load doesn't count toward the 3 s`() = runTest {
        var done = false
        launch {
            repository.openChat("Bob.png")
            done = true
        }

        advanceTimeBy(10_000)
        runCurrent()
        coVerify(exactly = 0) { client.selectCharacter(any()) }

        pageReady()
        runCurrent()
        coVerify(exactly = 1) { client.selectCharacter("Bob.png") }
        assertFalse(done)

        chatOpened("Bob.png")
        runCurrent()
        assertTrue(done)
    }

    @Test
    fun `refresh re-reads the characters from the server and waits for the recent chats`() = runTest {
        coEvery { client.requestRecentChats() } coAnswers {
            bridge.onRawMessage("""{"v":1,"type":"recentChats","items":[]}""")
        }

        repository.refresh()

        coVerify(exactly = 1) { client.requestCharacters(fromServer = true) }
        coVerify(exactly = 1) { client.requestRecentChats() }
    }
}
