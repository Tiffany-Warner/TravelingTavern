package com.tiffles.travelingtavern.data.repository

import android.util.Log
import androidx.lifecycle.LifecycleOwner
import com.tiffles.travelingtavern.data.bridge.BridgeClient
import com.tiffles.travelingtavern.data.bridge.BridgeRepository
import com.tiffles.travelingtavern.data.bridge.CommandOrigin
import com.tiffles.travelingtavern.data.bridge.LeaseStateStore
import com.tiffles.travelingtavern.data.lifecycle.AppForegroundTracker
import com.tiffles.travelingtavern.data.webview.WebViewHolder
import com.tiffles.travelingtavern.domain.model.ChatRef
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CharacterRepositoryImplTest {

    private val bridge = BridgeRepository(LeaseStateStore())
    private val client: BridgeClient = mockk(relaxed = true)
    private val foreground = AppForegroundTracker()
    private val owner: LifecycleOwner = mockk(relaxed = true)

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        // Every command is sent unless a test says otherwise (false = refused by the lease check).
        coEvery { client.requestCharacters(any()) } returns true
        coEvery { client.requestRecentChats(any()) } returns true
        coEvery { client.selectCharacter(any(), any(), any(), any()) } returns true
        coEvery { client.deleteChat(any(), any(), any()) } returns true
        coEvery { client.reloadChat() } returns true
        coEvery { client.setAppState(any()) } returns true
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    private fun TestScope.repository() = CharacterRepositoryImpl(
        bridge = bridge,
        client = client,
        holder = mockk<WebViewHolder>(relaxed = true),
        foreground = foreground,
        scope = backgroundScope,
    ).also { runCurrent() }

    private fun pageReady() = bridge.onRawMessage("""{"v":1,"type":"ready"}""")

    /** Delivers a chatOpened; true, as a sent command returns. */
    private fun chatOpened(requestId: String, id: String, chatFile: String): Boolean {
        bridge.onRawMessage("""{"v":1,"type":"chatOpened","requestId":"$requestId","characterId":"$id","chatFile":"$chatFile"}""")
        return true
    }

    /** Answers every selectCharacter with a chatOpened for [chatFile]. */
    private fun answerOpensWith(chatFile: String) {
        coEvery { client.selectCharacter(any(), any(), any(), any()) } coAnswers {
            chatOpened(requestId = firstArg(), id = secondArg(), chatFile = chatFile)
        }
    }

    private fun goToBackgroundAndBack() {
        foreground.onStart(owner)
        foreground.onStop(owner)
        foreground.onStart(owner)
    }

    @Test
    fun `returns the opened chat file as soon as its own chatOpened arrives`() = runTest(UnconfinedTestDispatcher()) {
        val repository = repository()
        pageReady()
        coEvery { client.selectCharacter(any(), "Bob.png", "Bob - 1", false) } coAnswers {
            chatOpened("an earlier request", "Bob.png", "Bob - 2") // another request's answer doesn't count
            chatOpened(firstArg(), "Bob.png", "Bob - 1")
        }

        val opened = repository.openChat("Bob.png", "Bob - 1")

        assertEquals("Bob - 1", opened)
        assertEquals(0L, currentTime)
        verify(exactly = 0) { Log.w(any<String>(), any<String>()) }
    }

    @Test
    fun `a missing chat file opens the current chat and logs it`() = runTest(UnconfinedTestDispatcher()) {
        val repository = repository()
        pageReady()
        answerOpensWith("Bob - current")

        val opened = repository.openChat("Bob.png", "Bob - deleted")

        assertEquals("Bob - current", opened)
        verify(exactly = 1) { Log.w(any<String>(), any<String>()) }
    }

    @Test
    fun `a refused open returns at once instead of waiting for chatOpened`() = runTest {
        val repository = repository()
        pageReady()
        coEvery { client.selectCharacter(any(), any(), any(), any()) } returns false

        val opened = repository.openChat("Bob.png")

        assertNull(opened)
        assertEquals(0L, currentTime)
    }

    @Test
    fun `gives up after 3 s and logs it`() = runTest {
        val repository = repository()
        pageReady()
        var done = false
        var opened: String? = "unset"
        launch {
            opened = repository.openChat("Bob.png")
            done = true
        }

        advanceTimeBy(CharacterRepositoryImpl.OPEN_CHAT_TIMEOUT_MS - 1)
        runCurrent()
        assertFalse(done)

        advanceTimeBy(2)
        runCurrent()
        assertTrue(done)
        assertNull(opened)
        verify(exactly = 1) { Log.w(any<String>(), any<String>()) }
    }

    @Test
    fun `waiting for the page to load doesn't count toward the 3 s`() = runTest {
        val repository = repository()
        answerOpensWith("Bob - 1")
        var done = false
        launch {
            repository.openChat("Bob.png")
            done = true
        }

        advanceTimeBy(10_000)
        runCurrent()
        coVerify(exactly = 0) { client.selectCharacter(any(), any(), any(), any()) }

        pageReady()
        runCurrent()
        coVerify(exactly = 1) { client.selectCharacter(any(), "Bob.png", null, false) }
        assertTrue(done)
    }

    @Test
    fun `refresh (pull to refresh) re-reads the characters from the server and waits for the recent chats`() = runTest {
        val repository = repository()
        coEvery { client.requestRecentChats(any()) } coAnswers {
            bridge.onRawMessage("""{"v":1,"type":"recentChats","items":[]}""")
            true
        }

        repository.refresh()

        coVerify(exactly = 1) { client.requestCharacters(fromServer = true) }
        coVerify(exactly = 1) { client.requestRecentChats(CommandOrigin.User) }
    }

    @Test
    fun `the page hears when the app goes to the background and back`() = runTest(UnconfinedTestDispatcher()) {
        repository()
        foreground.onStart(owner)
        foreground.onStop(owner)

        coVerify { client.setAppState(true) }
        coVerify { client.setAppState(false) }
    }

    @Test
    fun `returning while Chat shows re-reads the open chat and the Chats list, as app-started`() = runTest(UnconfinedTestDispatcher()) {
        val repository = repository()
        repository.setChatVisible(true)

        goToBackgroundAndBack()

        coVerify(exactly = 1) { client.reloadChat() }
        coVerify(exactly = 1) { client.requestRecentChats(CommandOrigin.App) }
    }

    @Test
    fun `returning elsewhere marks the chat stale, so the next open re-reads it`() = runTest(UnconfinedTestDispatcher()) {
        val repository = repository()
        pageReady()
        answerOpensWith("Bob - 1")

        goToBackgroundAndBack()
        repository.openChat("Bob.png")
        repository.openChat("Bob.png")

        coVerify(exactly = 0) { client.reloadChat() }
        coVerify(exactly = 1) { client.requestRecentChats(any()) }
        coVerify(exactly = 1) { client.selectCharacter(any(), "Bob.png", null, true) }
        coVerify(exactly = 1) { client.selectCharacter(any(), "Bob.png", null, false) }
    }

    @Test
    fun `the first start is not a return`() = runTest(UnconfinedTestDispatcher()) {
        val repository = repository()
        repository.setChatVisible(true)

        foreground.onStart(owner)

        coVerify(exactly = 0) { client.reloadChat() }
        coVerify(exactly = 0) { client.requestRecentChats(any()) }
    }

    @Test
    fun `leaving Chat asks for the Chats list again, as app-started`() = runTest(UnconfinedTestDispatcher()) {
        val repository = repository()
        repository.setChatVisible(true)
        coVerify(exactly = 0) { client.requestRecentChats(any()) }

        repository.setChatVisible(false)

        coVerify(exactly = 1) { client.requestRecentChats(CommandOrigin.App) }
    }

    private val bobChat = ChatRef("Bob.png", "Bob - 1")

    /** Delivers a chatResult; true, as a sent command returns. */
    private fun chatResult(requestId: String, ok: Boolean, error: String? = null): Boolean {
        val errorField = error?.let { ""","error":"$it"""" }.orEmpty()
        bridge.onRawMessage("""{"v":1,"type":"chatResult","requestId":"$requestId","ok":$ok$errorField}""")
        return true
    }

    @Test
    fun `deleteChat succeeds on its own chatResult and re-reads the lists`() = runTest(UnconfinedTestDispatcher()) {
        val repository = repository()
        pageReady()
        coEvery { client.deleteChat(any(), "Bob.png", "Bob - 1") } coAnswers {
            chatResult("another request", ok = false, error = "failed")
            chatResult(firstArg(), ok = true)
        }

        assertTrue(repository.deleteChat(bobChat))
        coVerify(exactly = 1) { client.requestRecentChats(any()) }
    }

    @Test
    fun `deleteChat fails when SillyTavern says so`() = runTest(UnconfinedTestDispatcher()) {
        val repository = repository()
        pageReady()
        coEvery { client.deleteChat(any(), any(), any()) } coAnswers { chatResult(firstArg(), ok = false, error = "failed") }

        assertFalse(repository.deleteChat(bobChat))
        coVerify(exactly = 0) { client.requestRecentChats(any()) }
    }

    @Test
    fun `deleteChat gives up when the page never loads (Tailscale off)`() = runTest(UnconfinedTestDispatcher()) {
        val repository = repository()

        assertFalse(repository.deleteChat(bobChat))
        assertEquals(CharacterRepositoryImpl.DELETE_CHAT_TIMEOUT_MS, currentTime)
        coVerify(exactly = 0) { client.deleteChat(any(), any(), any()) }
    }

    @Test
    fun `generatingChat is the open chat while SillyTavern generates`() = runTest(UnconfinedTestDispatcher()) {
        val repository = repository()
        bridge.onRawMessage("""{"v":1,"type":"chatChanged","characterId":"Bob.png","chatFile":"Bob - 1"}""")
        assertNull(repository.generatingChat.first())

        bridge.onRawMessage("""{"v":1,"type":"generationState","running":true}""")
        assertEquals(bobChat, repository.generatingChat.first())

        bridge.onPageStarted()
        assertNull(repository.generatingChat.first())
    }
}
