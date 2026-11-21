package com.tiffles.travelingtavern.data.bridge

import android.util.Log
import com.tiffles.travelingtavern.domain.model.LeaseStatus
import com.tiffles.travelingtavern.domain.model.ParkReason
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LeaseGateTest {

    private val store = LeaseStateStore(appLeaseVersion = 1)
    private val reader = FakeReader()

    @Before
    fun mockLog() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
    }

    @After
    fun unmockLog() {
        unmockkStatic(Log::class)
    }

    private fun TestScope.gate() = LeaseGate(store, reader, clock = { testScheduler.currentTime })

    private fun state(state: String = "holder", reason: String? = null, pageId: String = "t1.l1", epoch: Long? = 3L) =
        BridgeMessage.LeaseState(
            state = state,
            reason = reason,
            pageId = pageId,
            epoch = epoch,
            busy = false,
            connected = true,
            origin = "http://192.0.2.1:8000"
        )

    private fun info(leaseVersion: Int) = BridgeMessage.LeaseInfo(
        leaseVersion = leaseVersion,
        extensionVersion = null,
        extensionLeaseVersion = null,
        stVersion = null,
        untestedServer = false
    )

    /** Answers reads with [result]; [during] runs inside the read (a report arriving meanwhile). */
    private class FakeReader : LeaseFileReader {
        var result: LeaseFileRead = LeaseFileRead.Found("t1.l1", 3)
        var during: (() -> Unit)? = null
        var reads = 0
        val origins = mutableListOf<String>()

        override suspend fun read(origin: String): LeaseFileRead {
            reads++
            origins += origin
            during?.invoke()
            during = null
            return result
        }
    }

    // -- Holder: the pair rule

    @Test
    fun `a holder whose pair is in the lease file is allowed, read from the page's origin`() = runTest {
        store.onLeaseState(state())

        assertEquals(LeaseCheck.Allowed, gate().check())
        assertEquals(listOf("http://192.0.2.1:8000"), reader.origins)
    }

    @Test
    fun `the pair compares holder and epoch together`() = runTest {
        store.onLeaseState(state())
        reader.result = LeaseFileRead.Found("t2.l9", 3) // same epoch, another page

        assertEquals(LeaseCheck.RefusedPageMustCheck, gate().check())
        assertEquals(LeaseStatus.Parked(ParkReason.Lost), store.status.value)
    }

    @Test
    fun `a changed epoch parks as lost and asks the page to check`() = runTest {
        store.onLeaseState(state())
        reader.result = LeaseFileRead.Found("t1.l1", 4)

        assertEquals(LeaseCheck.RefusedPageMustCheck, gate().check())
        assertEquals(LeaseStatus.Parked(ParkReason.Lost), store.status.value)
    }

    @Test
    fun `no lease file while holding parks as lost`() = runTest {
        store.onLeaseState(state())
        reader.result = LeaseFileRead.Missing

        assertEquals(LeaseCheck.RefusedPageMustCheck, gate().check())
        assertEquals(LeaseStatus.Parked(ParkReason.Lost), store.status.value)
    }

    @Test
    fun `an unreadable lease file parks as unreadable and asks the page to check`() = runTest {
        store.onLeaseState(state())
        reader.result = LeaseFileRead.Unreadable

        assertEquals(LeaseCheck.RefusedPageMustCheck, gate().check())
        assertEquals(LeaseStatus.Parked(ParkReason.Unreadable), store.status.value)
    }

    @Test
    fun `a lease in an unknown format parks as update needed`() = runTest {
        store.onLeaseState(state())
        reader.result = LeaseFileRead.UnknownFormat

        assertEquals(LeaseCheck.RefusedPageMustCheck, gate().check())
        assertEquals(LeaseStatus.Parked(ParkReason.UnknownFormat), store.status.value)
    }

    @Test
    fun `a holder that never held an epoch can't match`() = runTest {
        store.onLeaseState(state(epoch = null))

        assertEquals(LeaseCheck.RefusedPageMustCheck, gate().check())
    }

    @Test
    fun `the page's next leaseState replaces the pre-check's parked state`() = runTest {
        store.onLeaseState(state())
        reader.result = LeaseFileRead.Unreadable
        gate().check()

        store.onLeaseState(state(state = "parked", reason = "lost"))

        assertEquals(LeaseStatus.Parked(ParkReason.Lost), store.status.value)
    }

    // -- Reuse

    @Test
    fun `a matching check at most 2 s old is reused, an older one is not`() = runTest {
        store.onLeaseState(state())
        val gate = gate()

        gate.check()
        advanceTimeBy(LeaseGate.REUSE_MS)
        assertEquals(LeaseCheck.Allowed, gate.check())
        assertEquals(1, reader.reads)

        advanceTimeBy(1)
        assertEquals(LeaseCheck.Allowed, gate.check())
        assertEquals(2, reader.reads)
    }

    @Test
    fun `a check for another pair is not reused`() = runTest {
        store.onLeaseState(state())
        val gate = gate()
        gate.check()

        store.onLeaseState(state(epoch = 4))
        reader.result = LeaseFileRead.Found("t1.l1", 4)

        assertEquals(LeaseCheck.Allowed, gate.check())
        assertEquals(2, reader.reads)
    }

    @Test
    fun `a failed check is never reused`() = runTest {
        store.onLeaseState(state())
        val gate = gate()
        reader.result = LeaseFileRead.Unreadable
        gate.check()

        store.onLeaseState(state()) // the page confirmed it still holds
        reader.result = LeaseFileRead.Found("t1.l1", 3)

        assertEquals(LeaseCheck.Allowed, gate.check())
        assertEquals(2, reader.reads)
    }

    @Test
    fun `the page's own epoch bump during the read leads to a second read, not a false park`() = runTest {
        store.onLeaseState(state())
        reader.result = LeaseFileRead.Found("t1.l1", 4)
        reader.during = { store.onLeaseState(state(epoch = 4)) }

        assertEquals(LeaseCheck.Allowed, gate().check())
        assertEquals(2, reader.reads)
    }

    // -- Refused up front

    @Test
    fun `refused up front while parked, without reading the lease`() = runTest {
        store.onLeaseState(state(state = "parked", reason = "held", epoch = null))

        assertEquals(LeaseCheck.Refused, gate().check())
        assertEquals(0, reader.reads)
    }

    @Test
    fun `refused while claiming after Use here`() = runTest {
        store.onLeaseState(state(state = "parked", reason = "held", epoch = null))
        store.onUseHere()

        assertEquals(LeaseCheck.Refused, gate().check())
        assertEquals(0, reader.reads)
    }

    @Test
    fun `a lease version mismatch is refused`() = runTest {
        store.onLeaseState(state())
        store.onLeaseInfo(info(leaseVersion = 2))

        assertEquals(LeaseCheck.Refused, gate().check())
        assertEquals(0, reader.reads)
    }

    @Test
    fun `refused while SillyTavern isn't loaded for lack of document start`() = runTest {
        store.onDocumentStartUnsupported()

        assertEquals(LeaseCheck.Refused, gate().check())
    }

    // -- Before the first leaseState

    @Test
    fun `before the first leaseState a command waits, then runs the normal check`() = runTest {
        val gate = gate()
        val result = async { gate.check() }
        runCurrent()
        assertTrue(gate.waiting.value)
        assertEquals(0, reader.reads)

        advanceTimeBy(LeaseGate.FIRST_REPORT_TIMEOUT_MS - 1)
        store.onLeaseState(state())
        runCurrent()

        assertEquals(LeaseCheck.Allowed, result.await())
        assertEquals(1, reader.reads)
        assertFalse(gate.waiting.value)
    }

    @Test
    fun `a first report of parked is refused after the wait`() = runTest {
        val gate = gate()
        val result = async { gate.check() }
        runCurrent()

        store.onLeaseState(state(state = "parked", reason = "held", epoch = null))

        assertEquals(LeaseCheck.Refused, result.await())
        assertEquals(0, reader.reads)
    }

    @Test
    fun `no report within 5 s is refused with the still loading message and no parked screen`() = runTest {
        val gate = gate()
        var stillLoading = 0
        backgroundScope.launch { gate.stillLoading.collect { stillLoading++ } }
        val result = async { gate.check() }
        runCurrent()

        advanceTimeBy(LeaseGate.FIRST_REPORT_TIMEOUT_MS + 1)

        assertEquals(LeaseCheck.Refused, result.await())
        assertEquals(1, stillLoading)
        assertEquals(LeaseStatus.NotReported, store.status.value)
        assertFalse(gate.waiting.value)
        assertEquals(0, reader.reads)
    }

    // -- App-started commands (D25)

    @Test
    fun `an app-started command waits for the first report without the loading bar`() = runTest {
        val gate = gate()
        val result = async { gate.check(CommandOrigin.App) }
        runCurrent()
        assertFalse(gate.waiting.value)

        store.onLeaseState(state())
        runCurrent()

        assertEquals(LeaseCheck.Allowed, result.await())
        assertEquals(1, reader.reads)
    }

    @Test
    fun `an app-started command with no report within 5 s is refused quietly`() = runTest {
        val gate = gate()
        var stillLoading = 0
        backgroundScope.launch { gate.stillLoading.collect { stillLoading++ } }
        val result = async { gate.check(CommandOrigin.App) }
        runCurrent()

        advanceTimeBy(LeaseGate.FIRST_REPORT_TIMEOUT_MS + 1)

        assertEquals(LeaseCheck.Refused, result.await())
        assertEquals(0, stillLoading)
        assertFalse(gate.waiting.value)
    }

    @Test
    fun `a user command waiting alongside an app command still shows the loading bar`() = runTest {
        val gate = gate()
        val app = async { gate.check(CommandOrigin.App) }
        val user = async { gate.check(CommandOrigin.User) }
        runCurrent()
        assertTrue(gate.waiting.value)

        store.onLeaseState(state())
        runCurrent()

        assertEquals(LeaseCheck.Allowed, app.await())
        assertEquals(LeaseCheck.Allowed, user.await())
        assertFalse(gate.waiting.value)
    }

    @Test
    fun `an app-started command gets the same pair check and park`() = runTest {
        store.onLeaseState(state())
        reader.result = LeaseFileRead.Found("t2.l9", 4)

        assertEquals(LeaseCheck.RefusedPageMustCheck, gate().check(CommandOrigin.App))
        assertEquals(LeaseStatus.Parked(ParkReason.Lost), store.status.value)
    }

    @Test
    fun `an app-started command is refused while parked`() = runTest {
        store.onLeaseState(state(state = "parked", reason = "held", epoch = null))

        assertEquals(LeaseCheck.Refused, gate().check(CommandOrigin.App))
        assertEquals(0, reader.reads)
    }

    // -- Continue without protection

    @Test
    fun `unprotected with nothing reporting runs the command with no check`() = runTest {
        store.onDocumentStartUnsupported()
        store.onContinueWithoutProtection()

        assertEquals(LeaseCheck.Allowed, gate().check())
        assertEquals(0, reader.reads)
    }

    @Test
    fun `unprotected but an extension's lease reports holder, the normal check applies`() = runTest {
        store.onDocumentStartUnsupported()
        store.onContinueWithoutProtection()
        store.onLeaseState(state())
        reader.result = LeaseFileRead.Found("t2.l9", 5)

        assertEquals(LeaseCheck.RefusedPageMustCheck, gate().check())
        assertEquals(1, reader.reads)
    }

    @Test
    fun `unprotected but an extension's lease reports parked, refused`() = runTest {
        store.onDocumentStartUnsupported()
        store.onContinueWithoutProtection()
        store.onLeaseState(state(state = "parked", reason = "held", epoch = null))

        assertEquals(LeaseCheck.Refused, gate().check())
    }
}
