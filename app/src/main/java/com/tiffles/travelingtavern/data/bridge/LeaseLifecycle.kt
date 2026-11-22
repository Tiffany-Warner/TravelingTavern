package com.tiffles.travelingtavern.data.bridge

import android.util.Log
import com.tiffles.travelingtavern.data.lifecycle.AppForegroundTracker
import com.tiffles.travelingtavern.di.ApplicationScope
import com.tiffles.travelingtavern.domain.model.LeaseStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Foreground and background for the session lease (A5), and the page's `setAppState`.
 *
 * **Background** (`isForeground` → `false`): only a holder that isn't generating releases. The
 * marker names the last `leaseState`'s pair and is uploaded from Kotlin ([ReleaseUploader]) at
 * once, on the app scope (Android may freeze the process soon after Home), with E3's bounded
 * retry: a random 100–200 ms between attempts, at most [MAX_ATTEMPTS] in total. Then
 * `setAppState(false)`.
 *
 * **Return** (a `false` → `true` after the first start, what [AppForegroundTracker.returns]
 * emits): no further release attempt starts; an attempt still in flight is waited for, bounded
 * by [returnWaitMs], before `__ttLease.onForeground()`. Otherwise the page could confirm its
 * pair before the marker lands, and a marker landing afterwards would make a foreground holder
 * look released, so another device could claim without a click. Then `setAppState(true)`.
 *
 * **Reply in the background** (A6): when a holder's reply starts (`busy` turns `true`) with the
 * app in the foreground, the reply service starts (Android 14+, [ReplyServiceController]); it
 * stops once the reply and the release that follows it are done. Going to the background during
 * a reply with the service running defers the release until the reply ends and SillyTavern's save
 * of it is reported (`chatSaved`, bounded), for at most [backgroundReplyDeadlineMs]; then the
 * reply is interrupted, before Chromium freezes the hidden page (D30). Without the service (Android
 * 12–13, or it couldn't start) the reply is interrupted at once. The interrupt: Stop, then one
 * bounded wait ([interruptWaitMs]) for `busy: false` and the page's report that SillyTavern's save
 * of the partial reply completed (`chatSaved`), so the save lands first, then the release. At
 * the service's time limit in the background (a safety net: the deadline normally comes first),
 * the same interrupt runs before the service stops, bounded by [timeoutStopMs] so it fits the
 * time Android allows.
 *
 * Call [start] once, from `Application.onCreate`. Runs on the main thread (the app scope).
 */
@Singleton
class LeaseLifecycle internal constructor(
    private val isForeground: StateFlow<Boolean>,
    private val store: LeaseStateStore,
    private val uploader: ReleaseUploader,
    private val client: BridgeClient,
    private val replyService: ReplyServiceController,
    private val scope: CoroutineScope,
    private val now: () -> Instant = Instant::now,
    private val backoffMs: () -> Long = { releaseBackoffMs() },
    private val returnWaitMs: Long = RELEASE_ATTEMPT_BUDGET_MS + RETURN_WAIT_MARGIN_MS,
    private val interruptWaitMs: Long = INTERRUPT_WAIT_MS,
    private val timeoutStopMs: Long = TIMEOUT_STOP_MS,
    private val backgroundReplyDeadlineMs: Long = BACKGROUND_REPLY_DEADLINE_MS
) {

    @Inject
    constructor(
        foreground: AppForegroundTracker,
        store: LeaseStateStore,
        uploader: ReleaseUploader,
        client: BridgeClient,
        replyService: ReplyServiceController,
        @ApplicationScope scope: CoroutineScope
    ) : this(foreground.isForeground, store, uploader, client, replyService, scope)

    private var started = false
    private var release: Job? = null
    private var returned = false
    private var appState: Job? = null
    private var serviceRunning = false
    private var releaseWhenReplyEnds = false
    private var interrupt: Job? = null
    private var replyDeadline: Job? = null
    private var serviceToken = 0
    private var afterReply: Job? = null

    fun start() {
        scope.launch {
            var hasBeenInBackground = false
            isForeground.collect { foreground ->
                when {
                    !foreground -> {
                        if (started) {
                            hasBeenInBackground = true
                            onBackground()
                        }
                        sendAppState(false)
                    }
                    hasBeenInBackground -> {
                        hasBeenInBackground = false
                        onReturn()
                    }
                    else -> {
                        started = true
                        sendAppState(true)
                    }
                }
            }
        }
        scope.launch {
            store.status.map { it.isReplying() }.distinctUntilChanged().collect { replying ->
                if (replying) onReplyStarted() else onReplyEnded()
            }
        }
    }

    /**
     * The reply service reached its time limit (called by the service). In the foreground it just
     * stops. In the background the interrupt runs first (or a release after the reply that is still
     * pending finishes), then the service stops, at the latest after [timeoutStopMs] (Android
     * treats a service that outlives the grace period as not responding); release retries still
     * pending then go on without it.
     */
    fun onReplyServiceTimeout(stopService: () -> Unit) {
        serviceRunning = false
        cancelReplyDeadline()
        val stop = {
            replyService.stop()
            stopService()
        }
        val pending = afterReply?.takeIf { it.isActive } ?: interrupt?.takeIf { it.isActive }
        val job = when {
            isForeground.value -> null
            store.status.value.isReplying() -> {
                Log.i(TAG, "Reply service time limit in the background: interrupting the reply")
                releaseWhenReplyEnds = false
                startInterrupt()
            }
            else -> pending
        }
        if (job == null) {
            Log.i(TAG, "Reply service time limit: stopping it")
            stop()
            return
        }
        scope.launch {
            if (withTimeoutOrNull(timeoutStopMs) { job.join() } == null) {
                Log.w(TAG, "Release not finished after $timeoutStopMs ms; stopping the reply service anyway")
            }
            stop()
        }
    }

    /** Releases if this page is a holder that isn't generating; see [onBackgroundWhileBusy] otherwise. */
    private fun onBackground() {
        returned = false
        val status = store.status.value
        if (status !is LeaseStatus.Holder) {
            Log.d(TAG, "Background: not holder, nothing to release")
            return
        }
        if (status.busy) onBackgroundWhileBusy() else releaseIfHolder()
    }

    /**
     * A reply is generating (`busy`): with the reply service running, it finishes and the release
     * follows when it ends ([onReplyEnded]); without it (Android 12–13, or the service didn't
     * start), the reply is interrupted.
     */
    private fun onBackgroundWhileBusy() {
        if (serviceRunning) {
            Log.i(TAG, "Background while a reply generates: it may finish under the reply service, then releases")
            releaseWhenReplyEnds = true
            replyDeadline = scope.launch {
                delay(backgroundReplyDeadlineMs)
                Log.i(TAG, "Reply still running $backgroundReplyDeadlineMs ms after going to the background: interrupting it")
                releaseWhenReplyEnds = false
                startInterrupt()
            }
        } else {
            Log.i(TAG, "Background while a reply generates, no reply service: interrupting it")
            startInterrupt()
        }
    }

    /** A holder's reply started: the reply service starts, but only from the foreground (A6). */
    private fun onReplyStarted() {
        when {
            !isForeground.value -> Log.i(TAG, "Reply started in the background: no reply service")
            !replyService.supported || serviceRunning -> Unit
            else -> {
                serviceToken++
                serviceRunning = replyService.start()
            }
        }
    }

    /**
     * The reply ended (or the page stopped being holder). A deferred release waits for
     * SillyTavern's save first, as the interrupt does (D30). The service stops once an interrupt
     * or that release has finished, so the process keeps its importance for the save and upload.
     */
    private fun onReplyEnded() {
        cancelReplyDeadline()
        var finishing: Job? = interrupt?.takeIf { it.isActive }
        if (releaseWhenReplyEnds) {
            releaseWhenReplyEnds = false
            finishing = releaseAfterSave().also { afterReply = it }
        }
        stopServiceAfter(finishing)
    }

    /**
     * A reply that finished in the background: arm the page's save watch, wait (one bound,
     * [interruptWaitMs]) for the next `chatSaved`, then release. `busy: false` comes before
     * SillyTavern even sends the save (D30); arming now still comes first (the save follows
     * 0.2 s or more later). Nothing to wait for when the page is no longer holder.
     */
    private fun releaseAfterSave(): Job? {
        if (store.status.value !is LeaseStatus.Holder) {
            Log.d(TAG, "Reply ended in the background, not holder: nothing to release")
            return null
        }
        Log.i(TAG, "Reply ended in the background: releasing after SillyTavern's save")
        return scope.launch {
            val savesBefore = store.chatSaves.value
            client.watchChatSave()
            val saved = withTimeoutOrNull(interruptWaitMs) { store.chatSaves.first { it > savesBefore } } != null
            if (!saved) Log.w(TAG, "No chat save reported within $interruptWaitMs ms; releasing anyway")
            if (returned) {
                Log.i(TAG, "Returned before the release; no release")
                return@launch
            }
            releaseIfHolder()?.join()
        }
    }

    /** Stops the reply service now, or once [job] has finished, unless a new reply restarted it. */
    private fun stopServiceAfter(job: Job?) {
        if (!serviceRunning) return
        serviceRunning = false
        if (job == null || !job.isActive) {
            replyService.stop()
            return
        }
        val token = serviceToken
        job.invokeOnCompletion { if (token == serviceToken && !serviceRunning) replyService.stop() }
    }

    private fun cancelReplyDeadline() {
        replyDeadline?.cancel()
        replyDeadline = null
    }

    /**
     * Stop, then one bounded wait for `busy: false` and the next `chatSaved` (SillyTavern's save of
     * the partial reply lands first; `busy: false` alone comes before ST even sends it, D30), then
     * the release, which goes out even when the wait runs out (nothing generated, so no save; or
     * the save failed to report). Not after a return.
     */
    private fun startInterrupt(): Job {
        interrupt?.takeIf { it.isActive }?.let { return it }
        return scope.launch {
            val savesBefore = store.chatSaves.value
            client.interruptReply()
            store.onReplyInterrupted()
            val settled = withTimeoutOrNull(interruptWaitMs) {
                store.status.first { !it.isReplying() }
                store.chatSaves.first { it > savesBefore }
            } != null
            if (!settled) Log.w(TAG, "No busy: false and chat save within $interruptWaitMs ms; releasing anyway")
            if (returned) {
                Log.i(TAG, "Returned during the interrupt; no release")
                return@launch
            }
            releaseIfHolder()?.join()
        }.also { interrupt = it }
    }

    /**
     * Starts the release upload if this page is holder (busy or not: an interrupt that ran out
     * of time still releases; a released claim's `busy` is ignored, E5). Null when not holder.
     */
    private fun releaseIfHolder(): Job? {
        val state = store.lastState.value
        val epoch = state?.epoch
        if (state == null || epoch == null || store.status.value !is LeaseStatus.Holder || state.state != HOLDER) {
            Log.d(TAG, "Not holder, nothing to release")
            return null
        }
        val body = ReleaseMarker.uploadBody(state.pageId, epoch, now().truncatedTo(ChronoUnit.MILLIS).toString())
        return scope.launch { uploadWithRetry(state.origin, body) }.also { release = it }
    }

    private suspend fun uploadWithRetry(origin: String, body: String) {
        for (attempt in 1..MAX_ATTEMPTS) {
            if (returned) {
                Log.i(TAG, "Returned before the release was written; no more attempts")
                return
            }
            if (uploader.upload(origin, body)) {
                Log.i(TAG, "Release written (attempt $attempt)")
                return
            }
            if (attempt < MAX_ATTEMPTS) delay(backoffMs())
        }
        Log.w(TAG, "Release not written after $MAX_ATTEMPTS attempts; the other device falls back to Use here")
    }

    /** Waits (bounded) for a release in flight, then `onForeground()`, then `setAppState(true)`. */
    private fun onReturn() {
        returned = true
        releaseWhenReplyEnds = false
        cancelReplyDeadline()
        // Their waits end here: they would release nothing after a return anyway, and a later
        // background must start fresh (a new reply gets its own interrupt). An upload already in
        // flight runs on as [release], which is waited for below (D28).
        interrupt?.cancel()
        interrupt = null
        afterReply?.cancel()
        afterReply = null
        val pending = release
        release = null
        val previous = appState
        appState = scope.launch {
            if (pending != null && pending.isActive) {
                val finished = withTimeoutOrNull(returnWaitMs) { pending.join() } != null
                if (!finished) {
                    pending.cancel()
                    Log.w(TAG, "Release still in flight after $returnWaitMs ms; checking the lease anyway")
                }
            }
            client.onForeground()
            previous?.join()
            client.setAppState(true)
        }
    }

    /** One `setAppState` at a time, in order (each waits for the lease pre-check). */
    private fun sendAppState(foreground: Boolean) {
        val previous = appState
        appState = scope.launch {
            previous?.join()
            client.setAppState(foreground)
        }
    }

    private companion object {
        const val TAG = "LeaseLifecycle"
        const val HOLDER = "holder"
        const val MAX_ATTEMPTS = 3
        const val RETURN_WAIT_MARGIN_MS = 500L

        /**
         * The interrupt's one bound, shared by the wait for `busy: false` and the wait for the
         * page's `chatSaved` after Stop (D30); also the wait for `chatSaved` after a reply that
         * finished in the background. In the measured runs both had come within 1.2 s
         * after Stop (Notes, slice 7).
         */
        const val INTERRUPT_WAIT_MS = 3_000L

        /**
         * How long a reply may keep running under the reply service after the app goes to the
         * background before it is interrupted (D30). Chromium freezes a hidden page 60 s after it
         * is hidden (measured on WebView 157, no spread between runs: Notes, slice 7), and a
         * frozen page doesn't send SillyTavern's save of the partial reply until it is visible
         * again. The whole interrupt must therefore finish before 60 s: at most
         * [INTERRUPT_WAIT_MS] for `busy: false` and the save (one shared bound) plus one release
         * attempt ([RELEASE_ATTEMPT_BUDGET_MS]) ends 53 s after the deadline starts. The deadline
         * starts at `ProcessLifecycleOwner`'s stop, about 0.7 s after the page turns hidden, so
         * about 6 s are left before the freeze, as margin for other WebView versions.
         */
        const val BACKGROUND_REPLY_DEADLINE_MS = 45_000L

        /**
         * At the time limit, the interrupt gets this long before the service stops: the wait plus
         * one release attempt ([RELEASE_ATTEMPT_BUDGET_MS]), inside the 10 s Android allows
         * after `onTimeout` before it treats the app as not responding (Notes, slice 7).
         */
        const val TIMEOUT_STOP_MS = INTERRUPT_WAIT_MS + RELEASE_ATTEMPT_BUDGET_MS + 500L
    }
}

/** A holder generating a reply (`busy`). */
private fun LeaseStatus.isReplying(): Boolean = this is LeaseStatus.Holder && busy

/** E3's wait before another release attempt: a random 100–200 ms. */
internal fun releaseBackoffMs(random: Random = Random): Long = random.nextLong(100L, 201L)
