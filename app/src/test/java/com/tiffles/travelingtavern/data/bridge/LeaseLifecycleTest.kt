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

/** A5: the background release, its retry, and the order of things on return. A6: reply in the background. */
@OptIn(ExperimentalCoroutinesApi::class)
class LeaseLifecycleTest {

    private val foreground = MutableStateFlow(false)
    private val store = LeaseStateStore(appLeaseVersion = 1)
    private val uploader = FakeUploader()
    private val client: BridgeClient = mockk(relaxed = true)
    private val backoffs = mutableListOf<Long>()
    private val replyService = FakeReplyService()

    /** What happened, in order: `stop` (the interrupt's Stop), `upload`, `serviceStopped`. */
    private val events = mutableListOf<String>()

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        coEvery { client.setAppState(any()) } returns true
        coEvery { client.interruptReply() } answers {
            events += "stop"
            true
        }
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
        replyService = replyService,
        scope = backgroundScope,
        now = { Instant.parse("2026-11-21T10:00:00.123456Z") },
        backoffMs = { (100L + backoffs.size * 50).also { backoffs += it } },
        returnWaitMs = 5_500,
        interruptWaitMs = 3_000,
        timeoutStopMs = 8_500,
        backgroundReplyDeadlineMs = 45_000
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

    /** Started, then a reply starts in the foreground (the service starts if it can). */
    private fun TestScope.replyInForeground(): LeaseLifecycle {
        val lifecycle = lifecycle()
        foreground.value = true
        runCurrent()
        report(busy = false)
        report(busy = true)
        runCurrent()
        return lifecycle
    }

    /** After Stop: the page reports `busy: false`, then SillyTavern's save completes (`chatSaved`). */
    private fun TestScope.replyEndsAndSaves() {
        report(busy = false)
        runCurrent()
        events += "saved"
        store.onChatSaved()
        runCurrent()
    }

    private fun TestScope.toBackground() {
        foreground.value = false
        runCurrent()
    }

    private fun TestScope.toForeground() {
        foreground.value = true
        runCurrent()
    }

    private inner class FakeUploader : ReleaseUploader {
        val calls = mutableListOf<Pair<String, String>>()
        var results = ArrayDeque<Boolean>()
        var gate: CompletableDeferred<Boolean>? = null

        override suspend fun upload(origin: String, body: String): Boolean {
            calls += origin to body
            events += "upload"
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
    fun `release is deferred while a reply generates under the reply service`() = runTest {
        replyInForeground()
        toBackground()

        assertTrue(uploader.calls.isEmpty())
        coVerify(exactly = 0) { client.interruptReply() }
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

    // -- Reply service (A6, Android 14+)

    @Test
    fun `the service starts when a reply starts in the foreground`() = runTest {
        replyInForeground()

        assertEquals(1, replyService.starts)
    }

    @Test
    fun `the service doesn't start for a reply that starts in the background`() = runTest {
        lifecycle()
        report(busy = true)

        assertEquals(0, replyService.starts)
    }

    @Test
    fun `the service stops when the reply ends, with nothing released in the foreground`() = runTest {
        replyInForeground()
        report(busy = false)
        runCurrent()

        assertEquals(1, replyService.stops)
        assertTrue(uploader.calls.isEmpty())
    }

    @Test
    fun `a reply that ends in the background releases once SillyTavern's save is reported`() = runTest {
        replyInForeground()
        toBackground()
        report(busy = false)
        advanceTimeBy(800)
        runCurrent()
        coVerify(exactly = 1) { client.watchChatSave() }
        assertTrue(uploader.calls.isEmpty())

        events += "saved"
        store.onChatSaved()
        runCurrent()

        assertEquals(listOf("saved", "upload"), events)
        coVerify(exactly = 0) { client.interruptReply() }
        assertTrue(!store.replyInterrupted.value)
    }

    @Test
    fun `a reply that ends in the background without a save report releases at the bound`() = runTest {
        replyInForeground()
        toBackground()
        report(busy = false)
        advanceTimeBy(2_999)
        runCurrent()
        assertTrue(uploader.calls.isEmpty())

        advanceTimeBy(1)
        runCurrent()
        assertEquals(1, uploader.calls.size)
    }

    @Test
    fun `the service keeps running until the release after the reply is written`() = runTest {
        val gate = CompletableDeferred<Boolean>()
        uploader.gate = gate
        replyInForeground()
        toBackground()
        replyEndsAndSaves()
        assertEquals(listOf("saved", "upload"), events)
        assertEquals(0, replyService.stops)

        gate.complete(true)
        runCurrent()
        assertEquals(1, replyService.stops)
    }

    @Test
    fun `a return during the wait for the save releases nothing`() = runTest {
        replyInForeground()
        toBackground()
        report(busy = false)
        runCurrent()
        toForeground()
        store.onChatSaved()
        advanceTimeBy(5_000)
        runCurrent()

        assertTrue(uploader.calls.isEmpty())
        assertEquals(1, replyService.stops)
    }

    @Test
    fun `a return stops the old service and a new reply's service isn't stopped later`() = runTest {
        replyInForeground()
        toBackground()
        report(busy = false)
        runCurrent()
        assertEquals(0, replyService.stops)

        // The return cancels the release wait, so the old service stops at once. (The service
        // token in stopServiceAfter is a defensive guard behind this.)
        toForeground()
        assertEquals(1, replyService.stops)
        report(busy = true)
        runCurrent()
        assertEquals(2, replyService.starts)
        val stopsBefore = replyService.stops

        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(stopsBefore, replyService.stops)
        assertTrue(uploader.calls.isEmpty())
    }

    @Test
    fun `return then background again during the save wait releases once, without the stale job`() = runTest {
        replyInForeground()
        toBackground()
        report(busy = false)
        runCurrent()
        toForeground()
        toBackground()
        assertEquals(1, uploader.calls.size)

        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(1, uploader.calls.size)
    }

    @Test
    fun `a new reply after a return during the interrupt gets its own interrupt`() = runTest {
        replyService.supported = false
        replyInForeground()
        toBackground()
        toForeground()
        report(busy = false)
        report(busy = true)
        runCurrent()
        toBackground()

        coVerify(exactly = 2) { client.interruptReply() }
    }

    @Test
    fun `time limit in the background waits for a pending release after the reply`() = runTest {
        val lifecycle = replyInForeground()
        toBackground()
        report(busy = false)
        runCurrent()
        lifecycle.onReplyServiceTimeout { events += "serviceStopped" }
        runCurrent()
        assertTrue(events.isEmpty())

        events += "saved"
        store.onChatSaved()
        runCurrent()
        assertEquals(listOf("saved", "upload", "serviceStopped"), events)
    }

    @Test
    fun `the service keeps running until the interrupt's release is written`() = runTest {
        val gate = CompletableDeferred<Boolean>()
        uploader.gate = gate
        replyInForeground()
        toBackground()
        advanceTimeBy(45_000)
        runCurrent()
        replyEndsAndSaves()
        assertEquals(listOf("stop", "saved", "upload"), events)
        assertEquals(0, replyService.stops)

        gate.complete(true)
        runCurrent()
        assertEquals(1, replyService.stops)
    }

    @Test
    fun `a reply that ends after the return releases nothing`() = runTest {
        replyInForeground()
        toBackground()
        toForeground()
        report(busy = false)
        runCurrent()

        assertEquals(1, replyService.stops)
        assertTrue(uploader.calls.isEmpty())
    }

    @Test
    fun `a page that parks during the reply stops the service and releases nothing`() = runTest {
        replyInForeground()
        toBackground()
        report(state = "parked", reason = "lost", busy = false)
        runCurrent()

        assertEquals(1, replyService.stops)
        assertTrue(uploader.calls.isEmpty())
    }

    @Test
    fun `a service that couldn't start means the reply is interrupted in the background`() = runTest {
        replyService.startResult = false
        replyInForeground()
        toBackground()

        coVerify(exactly = 1) { client.interruptReply() }
    }

    // -- Time limit

    @Test
    fun `time limit in the foreground only stops the service`() = runTest {
        val lifecycle = replyInForeground()
        lifecycle.onReplyServiceTimeout { events += "serviceStopped" }
        runCurrent()

        assertEquals(listOf("serviceStopped"), events)
        coVerify(exactly = 0) { client.interruptReply() }
        assertTrue(uploader.calls.isEmpty())
    }

    @Test
    fun `after a time limit in the foreground, going to the background interrupts`() = runTest {
        val lifecycle = replyInForeground()
        lifecycle.onReplyServiceTimeout {}
        toBackground()

        coVerify(exactly = 1) { client.interruptReply() }
    }

    @Test
    fun `time limit in the background interrupts, releases, then stops the service`() = runTest {
        val lifecycle = replyInForeground()
        toBackground()
        lifecycle.onReplyServiceTimeout { events += "serviceStopped" }
        runCurrent()
        assertEquals(listOf("stop"), events)

        advanceTimeBy(500)
        replyEndsAndSaves()

        assertEquals(listOf("stop", "saved", "upload", "serviceStopped"), events)
        assertTrue(store.replyInterrupted.value)
    }

    @Test
    fun `time limit in the background stops the service within the allowed time`() = runTest {
        uploader.gate = CompletableDeferred()
        val lifecycle = replyInForeground()
        toBackground()
        lifecycle.onReplyServiceTimeout { events += "serviceStopped" }
        runCurrent()

        advanceTimeBy(8_499)
        runCurrent()
        assertEquals(listOf("stop", "upload"), events)  // no save report: released at the 3 s bound
        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf("stop", "upload", "serviceStopped"), events)
    }

    @Test
    fun `time limit after the reply's release was written just stops the service`() = runTest {
        val lifecycle = replyInForeground()
        toBackground()
        replyEndsAndSaves()
        events.clear()
        lifecycle.onReplyServiceTimeout { events += "serviceStopped" }
        runCurrent()

        assertEquals(listOf("serviceStopped"), events)
    }

    // -- Interrupt (Android 12–13: no service)

    @Test
    fun `without the service, a reply is interrupted on going to the background`() = runTest {
        replyService.supported = false
        replyInForeground()
        assertEquals(0, replyService.starts)
        toBackground()

        coVerify(exactly = 1) { client.interruptReply() }
        assertTrue(store.replyInterrupted.value)
    }

    @Test
    fun `without the service, the interrupt releases once the save is reported`() = runTest {
        replyService.supported = false
        replyInForeground()
        toBackground()
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(listOf("stop"), events)

        replyEndsAndSaves()

        assertEquals(listOf("stop", "saved", "upload"), events)
    }

    @Test
    fun `busy false alone doesn't release before the bound`() = runTest {
        replyService.supported = false
        replyInForeground()
        toBackground()
        report(busy = false)
        advanceTimeBy(2_999)
        runCurrent()
        assertEquals(listOf("stop"), events)

        advanceTimeBy(1)
        runCurrent()
        assertEquals(listOf("stop", "upload"), events)
    }

    @Test
    fun `a save report before busy false still waits for busy false`() = runTest {
        replyService.supported = false
        replyInForeground()
        toBackground()
        store.onChatSaved()
        advanceTimeBy(1_000)
        runCurrent()
        assertEquals(listOf("stop"), events)

        report(busy = false)
        runCurrent()
        assertEquals(listOf("stop", "upload"), events)
    }

    @Test
    fun `a save reported before the interrupt doesn't count`() = runTest {
        replyService.supported = false
        replyInForeground()
        store.onChatSaved()
        toBackground()
        report(busy = false)
        advanceTimeBy(2_999)
        runCurrent()

        assertEquals(listOf("stop"), events)
    }

    @Test
    fun `the release still goes out when the wait runs out`() = runTest {
        replyService.supported = false
        replyInForeground()
        toBackground()

        advanceTimeBy(2_999)
        runCurrent()
        assertEquals(listOf("stop"), events)
        advanceTimeBy(1)
        runCurrent()

        assertEquals(listOf("stop", "upload"), events)
    }

    @Test
    fun `a return during the interrupt's wait releases nothing`() = runTest {
        replyService.supported = false
        replyInForeground()
        toBackground()
        toForeground()
        replyEndsAndSaves()

        assertEquals(listOf("stop", "saved"), events)
    }

    @Test
    fun `a page that parks during the interrupt's wait releases nothing`() = runTest {
        replyService.supported = false
        replyInForeground()
        toBackground()
        report(state = "parked", reason = "lost", busy = false)
        advanceTimeBy(5_000)
        runCurrent()

        assertEquals(listOf("stop"), events)
    }

    // -- Deadline in the background (D30)

    @Test
    fun `the deadline interrupts a reply still running in the background`() = runTest {
        replyInForeground()
        toBackground()

        advanceTimeBy(44_999)
        runCurrent()
        assertTrue(events.isEmpty())
        advanceTimeBy(1)
        runCurrent()

        assertEquals(listOf("stop"), events)
        assertTrue(store.replyInterrupted.value)
    }

    @Test
    fun `the deadline's interrupt keeps the order, then the reply's end stops the service`() = runTest {
        replyInForeground()
        toBackground()
        advanceTimeBy(45_000)
        runCurrent()
        advanceTimeBy(500)
        replyEndsAndSaves()

        assertEquals(listOf("stop", "saved", "upload"), events)
        assertEquals(1, replyService.stops)
    }

    @Test
    fun `the deadline's interrupt releases at once when the save is reported`() = runTest {
        replyInForeground()
        toBackground()
        advanceTimeBy(45_000)
        runCurrent()
        report(busy = false)
        advanceTimeBy(700)
        runCurrent()
        assertEquals(listOf("stop"), events)

        events += "saved"
        store.onChatSaved()
        runCurrent()
        assertEquals(listOf("stop", "saved", "upload"), events)
    }

    @Test
    fun `the deadline's interrupt still releases when the wait runs out`() = runTest {
        replyInForeground()
        toBackground()
        advanceTimeBy(45_000 + 2_999)
        runCurrent()
        assertEquals(listOf("stop"), events)
        advanceTimeBy(1)
        runCurrent()

        assertEquals(listOf("stop", "upload"), events)
    }

    @Test
    fun `a reply that ends before the deadline cancels it and releases once`() = runTest {
        replyInForeground()
        toBackground()
        advanceTimeBy(20_000)
        report(busy = false)
        runCurrent()
        advanceTimeBy(60_000)
        runCurrent()

        assertEquals(listOf("upload"), events)
        assertEquals(1, replyService.stops)
        coVerify(exactly = 0) { client.interruptReply() }
    }

    @Test
    fun `a return before the deadline cancels it`() = runTest {
        replyInForeground()
        toBackground()
        advanceTimeBy(20_000)
        toForeground()
        advanceTimeBy(60_000)
        runCurrent()

        assertTrue(events.isEmpty())
        coVerify(exactly = 0) { client.interruptReply() }
        assertEquals(0, replyService.stops)
    }

    @Test
    fun `a return while the deadline's release uploads waits for it before onForeground`() = runTest {
        val gate = CompletableDeferred<Boolean>()
        uploader.gate = gate
        replyInForeground()
        toBackground()
        advanceTimeBy(45_000)
        runCurrent()
        replyEndsAndSaves()
        assertEquals(listOf("stop", "saved", "upload"), events)

        toForeground()
        advanceTimeBy(1_000)
        runCurrent()
        coVerify(exactly = 0) { client.onForeground() }

        gate.complete(true)
        runCurrent()
        coVerifyOrder {
            client.onForeground()
            client.setAppState(true)
        }
    }

    @Test
    fun `the time limit cancels the deadline`() = runTest {
        val lifecycle = replyInForeground()
        toBackground()
        lifecycle.onReplyServiceTimeout { events += "serviceStopped" }
        runCurrent()
        replyEndsAndSaves()
        advanceTimeBy(60_000)
        runCurrent()

        assertEquals(listOf("stop", "saved", "upload", "serviceStopped"), events)
        coVerify(exactly = 1) { client.interruptReply() }
    }

    private inner class FakeReplyService : ReplyServiceController {
        override var supported = true
        var startResult = true
        var starts = 0
        var stops = 0

        override fun start(): Boolean {
            starts++
            return startResult
        }

        override fun stop() {
            stops++
        }
    }
}
