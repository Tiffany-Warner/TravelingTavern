package com.tiffles.travelingtavern.data.bridge

import android.util.Log
import com.tiffles.travelingtavern.domain.model.LeaseStatus
import com.tiffles.travelingtavern.domain.model.ParkReason
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** Reads the lease file (E2) from the page's origin. */
fun interface LeaseFileReader {
    suspend fun read(origin: String): LeaseFileRead
}

/**
 * Who started a bridge command (D25). [User]: a tap; its wait shows the loading bar and a
 * refusal for lack of a first report shows "still loading". [App]: the app on its own (return,
 * leaving Chat, a screen's automatic refresh, token counts while typing); the same check, but
 * waiting and refusals are only logged, and nothing retries it.
 */
enum class CommandOrigin { User, App }

/** The pre-check's answer for one bridge command. */
sealed interface LeaseCheck {

    /** Run the command. */
    data object Allowed : LeaseCheck

    /** Don't run it. The scrim (or the "still loading" message) already says why. */
    data object Refused : LeaseCheck

    /** Don't run it, and call `__ttLease.onForeground()` so the page parks too (A4). */
    data object RefusedPageMustCheck : LeaseCheck
}

/**
 * The Kotlin lease pre-check (A4), run before every bridge command that isn't exempt. Call on the
 * main thread ([LeaseStateStore] is written there).
 *
 * - Unprotected (no lease reports in the page): allowed with no check
 * - Not reported yet: waits up to [firstReportTimeoutMs] for the page's first `leaseState`
 *   ([waiting] is true meanwhile for a [CommandOrigin.User] command), then checks; none →
 *   refused ([stillLoading] fires for a user command; an app command is refused quietly)
 * - Parked, version mismatch, no document start: refused up front
 * - Holder: the lease file's (`holder`, `epoch`) must equal the last `leaseState`'s (`pageId`,
 *   `epoch`); a matching read at most [reuseMs] old is reused. Changed, missing or unreadable →
 *   parked at once ([LeaseStateStore.onPreCheckFailed]) and [LeaseCheck.RefusedPageMustCheck]
 *
 * Logs carry outcomes only, never the file, the pair, cookies or URLs.
 */
@Singleton
class LeaseGate internal constructor(
    private val store: LeaseStateStore,
    private val reader: LeaseFileReader,
    private val clock: () -> Long,
    private val firstReportTimeoutMs: Long = FIRST_REPORT_TIMEOUT_MS,
    private val reuseMs: Long = REUSE_MS
) {

    @Inject
    constructor(store: LeaseStateStore, reader: LeaseFileReader) :
        this(store, reader, clock = { System.nanoTime() / NANOS_PER_MS })

    private val mutex = Mutex()
    private var lastMatch: Match? = null
    private val waitingCount = MutableStateFlow(0)
    private val _waiting = MutableStateFlow(false)
    private val _stillLoading = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** A user-started command is waiting for the page's first `leaseState`: the loading bar shows. */
    val waiting: StateFlow<Boolean> = _waiting.asStateFlow()

    /** A user-started command was refused because the page never reported: "SillyTavern is still loading…". */
    val stillLoading: SharedFlow<Unit> = _stillLoading.asSharedFlow()

    suspend fun check(origin: CommandOrigin = CommandOrigin.User): LeaseCheck {
        val status = awaitFirstReport(showWaiting = origin == CommandOrigin.User)
        if (status == null) {
            if (origin == CommandOrigin.User) {
                Log.i(TAG, "Command refused: no leaseState within $firstReportTimeoutMs ms")
                _stillLoading.tryEmit(Unit)
            } else {
                Log.i(TAG, "App command refused quietly: no leaseState within $firstReportTimeoutMs ms")
            }
            return LeaseCheck.Refused
        }
        return when (status) {
            LeaseStatus.Unprotected -> LeaseCheck.Allowed
            is LeaseStatus.Holder -> checkPair()
            else -> {
                Log.d(TAG, "Command refused: ${status.label()}")
                LeaseCheck.Refused
            }
        }
    }

    /**
     * The current status, or the first reported one within the bound; null if none came.
     * [showWaiting]: count towards [waiting] meanwhile.
     */
    private suspend fun awaitFirstReport(showWaiting: Boolean): LeaseStatus? {
        val now = store.status.value
        if (now != LeaseStatus.NotReported) return now
        if (showWaiting) setWaiting(+1)
        try {
            return withTimeoutOrNull(firstReportTimeoutMs) {
                store.status.first { it != LeaseStatus.NotReported }
            }
        } finally {
            if (showWaiting) setWaiting(-1)
        }
    }

    private fun setWaiting(delta: Int) {
        waitingCount.update { it + delta }
        _waiting.value = waitingCount.value > 0
    }

    /** One check at a time, so commands sent together share one read. */
    private suspend fun checkPair(): LeaseCheck = mutex.withLock {
        var state = store.lastState.value ?: return LeaseCheck.Refused
        repeat(PAIR_READ_ATTEMPTS) {
            val pair = Pair(state.pageId, state.epoch)
            lastMatch?.let { if (it.pair == pair && clock() - it.at <= reuseMs) return LeaseCheck.Allowed }
            val read = reader.read(state.origin)
            val current = store.lastState.value
            if (store.status.value !is LeaseStatus.Holder || current == null) return LeaseCheck.Refused
            // The page reported again during the read (its own epoch bump): read once more.
            if (current.pageId != state.pageId || current.epoch != state.epoch) {
                state = current
                return@repeat
            }
            return decide(read, pair)
        }
        Log.w(TAG, "Lease state kept changing during the check; command refused")
        LeaseCheck.Refused
    }

    private fun decide(read: LeaseFileRead, pair: Pair<String, Long?>): LeaseCheck {
        val reason = when (read) {
            is LeaseFileRead.Found ->
                if (read.holder == pair.first && read.epoch == pair.second) {
                    lastMatch = Match(pair, clock())
                    return LeaseCheck.Allowed
                } else {
                    ParkReason.Lost
                }
            LeaseFileRead.Missing -> ParkReason.Lost
            LeaseFileRead.UnknownFormat -> ParkReason.UnknownFormat
            LeaseFileRead.Unreadable -> ParkReason.Unreadable
        }
        Log.w(TAG, "Pre-check failed (${read.label()}); parked, command refused")
        lastMatch = null
        store.onPreCheckFailed(reason)
        return LeaseCheck.RefusedPageMustCheck
    }

    private data class Match(val pair: Pair<String, Long?>, val at: Long)

    private fun LeaseStatus.label(): String = this::class.simpleName.orEmpty()

    private fun LeaseFileRead.label(): String = when (this) {
        is LeaseFileRead.Found -> "pair changed"
        LeaseFileRead.Missing -> "no lease file"
        LeaseFileRead.UnknownFormat -> "unknown format"
        LeaseFileRead.Unreadable -> "unreadable"
    }

    internal companion object {
        const val TAG = "LeaseGate"
        const val FIRST_REPORT_TIMEOUT_MS = 5_000L
        const val REUSE_MS = 2_000L
        const val PAIR_READ_ATTEMPTS = 2
        const val NANOS_PER_MS = 1_000_000L
    }
}
