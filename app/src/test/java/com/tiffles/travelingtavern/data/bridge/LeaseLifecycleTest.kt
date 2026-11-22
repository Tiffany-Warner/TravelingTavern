package com.tiffles.travelingtavern.data.bridge

import android.util.Log
import com.tiffles.travelingtavern.domain.model.ParkReason
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.util.Base64

/** A5: the background release, its retry, and the order of things on return. */
@OptIn(ExperimentalCoroutinesApi::class)
class LeaseLifecycleTest {

    private val foreground = MutableStateFlow(false)
    private val store = LeaseStateStore(appLeaseVersion = 1)
    private val uploader = FakeUploader()
    private val client: BridgeClient = mockk(relaxed = true)
    private val backoffs = mutableListOf<Long>()

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        coEvery { client.setAppState(any()) } returns true
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    private fun TestScope.lifecycle() = LeaseLifecycle(
        isForeground = foreground,
        store = store,
        uploader = uploader,
        client = client,
        scope = backgroundScope,
        now = { Instant.parse("2026-11-21T10:00:00.123456Z") },
        backoffMs = { (100L + backoffs.size * 50).also { backoffs += it } },
        returnWaitMs = 5_500
    ).also { it.start(); runCurrent() }

    private fun report(state: String = "holder", reason: String? = null, epoch: Long? = 4L, busy: Boolean = false) =
        store.onLeaseState(
            BridgeMessage.LeaseState(
                state = state,
                reason = reason,
                pageId = "t1.l1",
                epoch = epoch,
                busy = busy,
                connected = true,
                origin = "http://192.0.2.1:8000"
            )
        )

    /** Started, then to the background. */
    private fun TestScope.goBackground() {
        foreground.value = true
        runCurrent()
        foreground.value = false
        runCurrent()
    }

    private class FakeUploader : ReleaseUploader {
        val calls = mutableListOf<Pair<String, String>>()
        var results = ArrayDeque<Boolean>()
        var gate: CompletableDeferred<Boolean>? = null

        override suspend fun upload(origin: String, body: String): Boolean {
            calls += origin to body
            gate?.let { return it.await() }
            return results.removeFirstOrNull() ?: true
        }
    }

    // -- Who releases

    @Test
    fun `a holder releases on going to the background`() = runTest {
        lifecycle()
        report()
        goBackground()

        assertEquals(1, uploader.calls.size)
        assertEquals("http://192.0.2.1:8000", uploader.calls.single().first)
    }

    @Test
    fun `a parked page doesn't release`() = runTest {
        lifecycle()
        report(state = "parked", reason = "held", epoch = null)
        goBackground()

        assertTrue(uploader.calls.isEmpty())
    }

    @Test
    fun `nothing is released before the page reports, or unprotected`() = runTest {
        lifecycle()
        goBackground()
        store.onContinueWithoutProtection()
        foreground.value = true
        runCurrent()
        foreground.value = false
        runCurrent()

        assertTrue(uploader.calls.isEmpty())
    }

    @Test
    fun `a failed pre-check counts as parked`() = runTest {
        lifecycle()
        report()
        store.onPreCheckFailed(ParkReason.Lost)
        goBackground()

        assertTrue(uploader.calls.isEmpty())
    }

    @Test
    fun `the first start is not a background`() = runTest {
        report()
        lifecycle()

        assertTrue(uploader.calls.isEmpty())
        coVerify(exactly = 1) { client.setAppState(false) }
    }

    @Test
    fun `release is deferred while a reply generates`() = runTest {
        lifecycle()
        report(busy = true)
        goBackground()

        assertTrue(uploader.calls.isEmpty())
    }

    // -- The marker

    @Test
    fun `the body is the upload of the marker naming the last pair`() = runTest {
        lifecycle()
        report(epoch = 7)
        goBackground()

        val upload = Json.parseToJsonElement(uploader.calls.single().second) as JsonObject
        assertEquals(setOf("name", "data"), upload.keys)
        assertEquals("tt-session-release.json", upload["name"]!!.jsonPrimitive.content)
        val marker = Json.parseToJsonElement(String(Base64.getDecoder().decode(upload["data"]!!.jsonPrimitive.content), Charsets.UTF_8)) as JsonObject
        assertEquals(setOf("format", "holder", "epoch", "releasedAt"), marker.keys)
        assertEquals("1", marker["format"]!!.jsonPrimitive.content)
        assertEquals("t1.l1", marker["holder"]!!.jsonPrimitive.content)
        assertEquals("7", marker["epoch"]!!.jsonPrimitive.content)
        assertTrue(marker["releasedAt"]!!.jsonPrimitive.isString)
        assertEquals("2026-11-21T10:00:00.123Z", marker["releasedAt"]!!.jsonPrimitive.content)
    }

    // -- Bounded retry (E3)

    @Test
    fun `a failed upload is retried after the backoff, at most 3 attempts in total`() = runTest {
        uploader.results = ArrayDeque(listOf(false, false, false, false))
        lifecycle()
        report()
        goBackground()
        assertEquals(1, uploader.calls.size)

        advanceTimeBy(99)
        runCurrent()
        assertEquals(1, uploader.calls.size)
        advanceTimeBy(1)
        runCurrent()
        assertEquals(2, uploader.calls.size)

        advanceTimeBy(10_000)
        runCurrent()
        assertEquals(3, uploader.calls.size)
        assertEquals(listOf(100L, 150L), backoffs)
    }

    @Test
    fun `the backoff is random between 100 and 200 ms`() {
        val random = kotlin.random.Random(42)
        val values = List(500) { releaseBackoffMs(random) }
        assertTrue(values.all { it in 100L..200L })
        assertTrue(values.min() < 110 && values.max() > 190)
    }

    @Test
    fun `a success stops the retries`() = runTest {
        uploader.results = ArrayDeque(listOf(false, true))
        lifecycle()
        report()
        goBackground()
        advanceTimeBy(10_000)
        runCurrent()

        assertEquals(2, uploader.calls.size)
    }

    // -- Return

    @Test
    fun `onForeground is called on return, before setAppState(true)`() = runTest {
        lifecycle()
        report()
        goBackground()
        foreground.value = true
        runCurrent()

        coVerifyOrder {
            client.onForeground()
            client.setAppState(true)
        }
    }

    @Test
    fun `onForeground is called on return even when nothing was released`() = runTest {
        lifecycle()
        report(state = "parked", reason = "held", epoch = null)
        goBackground()
        foreground.value = true
        runCurrent()

        coVerify(exactly = 1) { client.onForeground() }
    }

    @Test
    fun `the first start doesn't call onForeground`() = runTest {
        lifecycle()
        foreground.value = true
        runCurrent()

        coVerify(exactly = 0) { client.onForeground() }
        coVerify { client.setAppState(true) }
    }

    @Test
    fun `a release in flight is waited for before onForeground`() = runTest {
        val gate = CompletableDeferred<Boolean>()
        uploader.gate = gate
        lifecycle()
        report()
        goBackground()
        foreground.value = true
        runCurrent()
        advanceTimeBy(1_000)
        runCurrent()

        coVerify(exactly = 0) { client.onForeground() }

        gate.complete(true)
        runCurrent()

        coVerify(exactly = 1) { client.onForeground() }
        coVerifyOrder {
            client.onForeground()
            client.setAppState(true)
        }
    }

    @Test
    fun `the wait for a release in flight is bounded`() = runTest {
        uploader.gate = CompletableDeferred()
        lifecycle()
        report()
        goBackground()
        foreground.value = true
        runCurrent()

        advanceTimeBy(5_499)
        runCurrent()
        coVerify(exactly = 0) { client.onForeground() }
        advanceTimeBy(1)
        runCurrent()
        coVerify(exactly = 1) { client.onForeground() }
    }

    @Test
    fun `no attempt starts after the return`() = runTest {
        uploader.results = ArrayDeque(listOf(false, false, false))
        lifecycle()
        report()
        goBackground()
        assertEquals(1, uploader.calls.size)

        foreground.value = true
        runCurrent()
        advanceTimeBy(10_000)
        runCurrent()

        assertEquals(1, uploader.calls.size)
        coVerify(exactly = 1) { client.onForeground() }
    }

    @Test
    fun `each background releases again with the latest pair`() = runTest {
        lifecycle()
        report(epoch = 4)
        goBackground()
        foreground.value = true
        runCurrent()
        report(epoch = 5)
        foreground.value = false
        runCurrent()

        assertEquals(2, uploader.calls.size)
    }
}
