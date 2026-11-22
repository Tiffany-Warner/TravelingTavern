package com.tiffles.travelingtavern.data.repository

import android.util.Log
import com.tiffles.travelingtavern.data.bridge.BridgeClient
import com.tiffles.travelingtavern.data.bridge.BridgeRepository
import com.tiffles.travelingtavern.data.bridge.LeaseStateStore
import com.tiffles.travelingtavern.data.webview.PageReloader
import com.tiffles.travelingtavern.domain.model.ExtensionOutcome
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/** A8: install and update through the page, their outcomes, and the cache-clearing reload. */
@OptIn(ExperimentalCoroutinesApi::class)
class ExtensionRepositoryImplTest {

    private val bridge = BridgeRepository(LeaseStateStore(appLeaseVersion = 1))
    private val client: BridgeClient = mockk()
    private var reloads = 0
    private val page = PageReloader { reloads++ }
    private val bodies = mutableListOf<JsonObject>()
    private val repository = ExtensionRepositoryImpl(client, bridge, page, answerTimeoutMs = 1_000)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
        Dispatchers.resetMain()
    }

    /** The page answers each install with [answer] (a JSON tail after `requestId`), or not at all when null. */
    private fun pageAnswersInstall(answer: String?, sent: Boolean = true) {
        coEvery { client.installExtension(any(), any()) } answers {
            bodies += secondArg<JsonObject>()
            if (answer != null) reply(firstArg(), answer)
            sent
        }
    }

    private fun pageAnswersUpdate(answer: String?, sent: Boolean = true) {
        coEvery { client.updateExtension(any(), any()) } answers {
            bodies += secondArg<JsonObject>()
            if (answer != null) reply(firstArg(), answer)
            sent
        }
    }

    private fun reply(requestId: String, tail: String) =
        bridge.onRawMessage("""{"v":1,"type":"extensionResult","requestId":"$requestId",$tail}""")

    @Test
    fun `install sends only the repo url, without branch or global`() = runTest {
        pageAnswersInstall(""""ok":true,"status":200,"upToDate":null""")

        repository.install()

        val body = bodies.single()
        assertEquals(setOf("url"), body.keys)
        assertEquals("https://github.com/Tiffany-Warner/TravelingTavern-Extension", body.getValue("url").jsonPrimitive.content)
    }

    @Test
    fun `a successful install clears the cache and reloads`() = runTest {
        pageAnswersInstall(""""ok":true,"status":200,"upToDate":null""")

        assertEquals(ExtensionOutcome.Installed, repository.install())
        assertEquals(1, reloads)
    }

    @Test
    fun `409 counts as already installed, with no reload`() = runTest {
        pageAnswersInstall(""""ok":false,"status":409,"upToDate":null""")

        assertEquals(ExtensionOutcome.AlreadyInstalled, repository.install())
        assertEquals(0, reloads)
    }

    @Test
    fun `any other failure, or no answer, is an install failure`() = runTest {
        pageAnswersInstall(""""ok":false,"status":500,"upToDate":null""")
        assertEquals(ExtensionOutcome.InstallFailed, repository.install())

        pageAnswersInstall(""""ok":false,"status":0,"upToDate":null""")
        assertEquals(ExtensionOutcome.InstallFailed, repository.install())

        pageAnswersInstall(answer = null)
        assertEquals(ExtensionOutcome.InstallFailed, repository.install())

        assertEquals(0, reloads)
    }

    @Test
    fun `refused by the pre-check is reported as refused, with no reload`() = runTest {
        pageAnswersInstall(answer = null, sent = false)
        assertEquals(ExtensionOutcome.Refused, repository.install())

        pageAnswersUpdate(answer = null, sent = false)
        assertEquals(ExtensionOutcome.Refused, repository.update())

        assertEquals(0, reloads)
    }

    @Test
    fun `update sends the extension folder name only`() = runTest {
        pageAnswersUpdate(""""ok":true,"status":200,"upToDate":true""")

        repository.update()

        val body = bodies.single()
        assertEquals(setOf("extensionName"), body.keys)
        assertEquals("TravelingTavern-Extension", body.getValue("extensionName").jsonPrimitive.content)
    }

    @Test
    fun `update that was already up to date says so, with no reload`() = runTest {
        pageAnswersUpdate(""""ok":true,"status":200,"upToDate":true""")

        assertEquals(ExtensionOutcome.UpToDate, repository.update())
        assertEquals(0, reloads)
    }

    @Test
    fun `update that pulled something clears the cache and reloads`() = runTest {
        pageAnswersUpdate(""""ok":true,"status":200,"upToDate":false""")

        assertEquals(ExtensionOutcome.Updated, repository.update())
        assertEquals(1, reloads)
    }

    @Test
    fun `update failures, 404 included, are update failures`() = runTest {
        pageAnswersUpdate(""""ok":false,"status":404,"upToDate":null""")
        assertEquals(ExtensionOutcome.UpdateFailed, repository.update())

        pageAnswersUpdate(answer = null)
        assertEquals(ExtensionOutcome.UpdateFailed, repository.update())

        assertEquals(0, reloads)
    }

    @Test
    fun `an answer to another request is ignored`() = runTest {
        coEvery { client.installExtension(any(), any()) } answers {
            reply("someone-else", """"ok":true,"status":200,"upToDate":null""")
            true
        }

        assertEquals(ExtensionOutcome.InstallFailed, repository.install())
        assertEquals(0, reloads)
    }
}
