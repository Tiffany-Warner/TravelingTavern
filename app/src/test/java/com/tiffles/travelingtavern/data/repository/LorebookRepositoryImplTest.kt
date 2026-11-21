package com.tiffles.travelingtavern.data.repository

import android.util.Log
import com.tiffles.travelingtavern.data.bridge.BridgeClient
import com.tiffles.travelingtavern.data.bridge.BridgeRepository
import com.tiffles.travelingtavern.data.bridge.LeaseStateStore
import com.tiffles.travelingtavern.data.webview.WebViewHolder
import com.tiffles.travelingtavern.domain.entry
import com.tiffles.travelingtavern.domain.repository.LorebookException
import com.tiffles.travelingtavern.domain.repository.LorebookFailure
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LorebookRepositoryImplTest {

    private val bridge = BridgeRepository(LeaseStateStore())
    private val client: BridgeClient = mockk(relaxed = true)
    private val repository = LorebookRepositoryImpl(bridge, client, mockk<WebViewHolder>(relaxed = true))

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        bridge.onRawMessage("""{"v":1,"type":"ready"}""")
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    private fun answer(json: String) = bridge.onRawMessage(json)

    private fun failureOf(result: Result<*>) = (result.exceptionOrNull() as LorebookException).failure

    @Test
    fun `entries come from the answer with the same request ID`() = runTest {
        coEvery { client.requestLorebook(any(), "Eldoria") } coAnswers {
            val id = firstArg<String>()
            answer("""{"v":1,"type":"lorebookEntries","requestId":"other","name":"Eldoria","entries":[]}""")
            answer("""{"v":1,"type":"lorebookEntries","requestId":"$id","name":"Eldoria","entries":[{"uid":2,"title":"t","keys":["a"],"content":"c","enabled":false}]}""")
        }

        val entries = repository.entries("Eldoria").getOrThrow()

        assertEquals(listOf(entry(uid = 2, title = "t", keywords = listOf("a"), content = "c", enabled = false)), entries)
    }

    @Test
    fun `bridge errors map to failures`() = runTest {
        coEvery { client.requestLorebook(any(), any()) } coAnswers {
            answer("""{"v":1,"type":"lorebookResult","requestId":"${firstArg<String>()}","ok":false,"error":"notFound"}""")
        }
        coEvery { client.createLorebook(any(), any()) } coAnswers {
            answer("""{"v":1,"type":"lorebookResult","requestId":"${firstArg<String>()}","ok":false,"error":"exists"}""")
        }
        coEvery { client.deleteLorebook(any(), any()) } coAnswers {
            answer("""{"v":1,"type":"lorebookResult","requestId":"${firstArg<String>()}","ok":false,"error":"failed"}""")
        }

        assertEquals(LorebookFailure.NotFound, failureOf(repository.entries("Gone")))
        assertEquals(LorebookFailure.NameTaken, failureOf(repository.create("Eldoria")))
        assertEquals(LorebookFailure.Failed, failureOf(repository.delete("Eldoria")))
    }

    @Test
    fun `create returns the saved name`() = runTest {
        coEvery { client.createLorebook(any(), "Kingdoms/North") } coAnswers {
            answer("""{"v":1,"type":"lorebookResult","requestId":"${firstArg<String>()}","ok":true,"name":"KingdomsNorth"}""")
        }

        assertEquals("KingdomsNorth", repository.create("Kingdoms/North").getOrThrow())
    }

    @Test
    fun `no answer in time is Failed`() = runTest {
        val result = async { repository.setGlobal("Eldoria", true) }

        advanceTimeBy(LorebookRepositoryImpl.ACTION_TIMEOUT_MS + 1)

        assertEquals(LorebookFailure.Failed, failureOf(result.await()))
    }

    @Test
    fun `saving an entry returns its uid and announces the change`() = runTest {
        val changed = mutableListOf<String>()
        backgroundScope.launch { repository.entriesChanged.collect { changed += it } }
        runCurrent()
        coEvery { client.saveLorebookEntry(any(), "Eldoria", any()) } coAnswers {
            answer("""{"v":1,"type":"lorebookResult","requestId":"${firstArg<String>()}","ok":true,"uid":5}""")
        }

        val uid = repository.saveEntry("Eldoria", entry(uid = null)).getOrThrow()
        runCurrent()

        assertEquals(5, uid)
        assertEquals(listOf("Eldoria"), changed)
    }

    @Test
    fun `token count comes back, or null on timeout`() = runTest {
        coEvery { client.countTokens(any(), "hello") } coAnswers {
            answer("""{"v":1,"type":"tokenCount","requestId":"${firstArg<String>()}","count":2}""")
        }
        assertEquals(2, repository.countTokens("hello"))

        val silent = async { repository.countTokens("nobody answers") }
        advanceTimeBy(LorebookRepositoryImpl.TOKEN_TIMEOUT_MS + 1)
        assertTrue(silent.await() == null)
    }
}
