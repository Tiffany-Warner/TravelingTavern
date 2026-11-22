package com.tiffles.travelingtavern.data.bridge

import android.webkit.WebView
import com.tiffles.travelingtavern.data.webview.WebViewHolder
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Which commands go through the lease pre-check (A4) and what a refusal does. */
@OptIn(ExperimentalCoroutinesApi::class)
class BridgeClientTest {

    private val webView: WebView = mockk(relaxed = true)
    private val holder: WebViewHolder = mockk { every { webView } returns this@BridgeClientTest.webView }
    private val gate: LeaseGate = mockk()
    private val client = BridgeClient(holder, PageModeStore(), PageThemeStore(), gate)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun scriptsRun(): List<String> {
        val scripts = mutableListOf<String>()
        verify(atLeast = 0) { webView.evaluateJavascript(capture(scripts), any()) }
        return scripts
    }

    @Test
    fun `an allowed command runs and reports it was sent`() = runTest {
        coEvery { gate.check(any()) } returns LeaseCheck.Allowed

        assertTrue(client.sendMessage("Hi"))

        assertTrue(scriptsRun().single().contains("__ttBridge.sendMessage("))
    }

    @Test
    fun `a refused command never reaches the page`() = runTest {
        coEvery { gate.check(any()) } returns LeaseCheck.Refused

        assertFalse(client.sendMessage("Hi"))

        assertTrue(scriptsRun().isEmpty())
    }

    @Test
    fun `a changed or unreadable lease calls onForeground instead of the command`() = runTest {
        coEvery { gate.check(any()) } returns LeaseCheck.RefusedPageMustCheck

        assertFalse(client.deleteChat("r1", "a.png", "chat"))

        assertTrue(scriptsRun() == listOf("window.__ttLease && window.__ttLease.onForeground();"))
    }

    @Test
    fun `setAppState is checked`() = runTest {
        coEvery { gate.check(any()) } returns LeaseCheck.Refused

        assertFalse(client.setAppState(true))

        coVerify(exactly = 1) { gate.check(any()) }
        assertTrue(scriptsRun().isEmpty())
    }

    @Test
    fun `commands from a tap are checked as user-started`() = runTest {
        coEvery { gate.check(any()) } returns LeaseCheck.Allowed

        client.sendMessage("Hi")
        client.deleteChat("r1", "a.png", "chat")
        client.selectPersona("r2", "p.png", lockToChat = false)
        client.requestCharacters(fromServer = true)
        client.connectModel("r3")
        client.regenerateReply("r4")

        coVerify(exactly = 6) { gate.check(CommandOrigin.User) }
        coVerify(exactly = 0) { gate.check(CommandOrigin.App) }
    }

    @Test
    fun `commands the app sends by itself are checked as app-started`() = runTest {
        coEvery { gate.check(any()) } returns LeaseCheck.Allowed

        client.setAppState(true)
        client.reloadChat()
        client.requestPersonas(refreshPictures = true)
        client.countTokens("r1", "text")

        coVerify(exactly = 4) { gate.check(CommandOrigin.App) }
        coVerify(exactly = 0) { gate.check(CommandOrigin.User) }
    }

    @Test
    fun `the recent chats request is checked with the origin its caller gives`() = runTest {
        coEvery { gate.check(any()) } returns LeaseCheck.Allowed

        client.requestRecentChats(CommandOrigin.User)
        client.requestRecentChats(CommandOrigin.App)

        coVerify(exactly = 1) { gate.check(CommandOrigin.User) }
        coVerify(exactly = 1) { gate.check(CommandOrigin.App) }
    }

    @Test
    fun `DOM and CSS only commands are exempt from the check`() = runTest {
        coEvery { gate.check(any()) } returns LeaseCheck.Refused

        assertTrue(client.revealMessage(messageId = 3, coveredPx = 200))
        assertTrue(client.endReveal())
        client.setCleanMode(true)
        client.setPageTheme(PageTheme(dark = true, colors = emptyMap()))

        coVerify(exactly = 0) { gate.check(any()) }
        assertTrue(scriptsRun().size == 4)
    }

    @Test
    fun `the lease's own hooks are never gated`() = runTest {
        coEvery { gate.check(any()) } returns LeaseCheck.Refused

        client.useHere()
        client.onForeground()

        coVerify(exactly = 0) { gate.check(any()) }
        assertTrue(scriptsRun().size == 2)
    }
}
