package com.tiffles.travelingtavern.data.bridge

import android.util.Log
import com.tiffles.travelingtavern.data.lifecycle.AppForegroundTracker
import com.tiffles.travelingtavern.di.ApplicationScope
import com.tiffles.travelingtavern.domain.model.LeaseStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
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
 * Call [start] once, from `Application.onCreate`. Runs on the main thread (the app scope).
 */
@Singleton
class LeaseLifecycle internal constructor(
    private val isForeground: StateFlow<Boolean>,
    private val store: LeaseStateStore,
    private val uploader: ReleaseUploader,
    private val client: BridgeClient,
    private val scope: CoroutineScope,
    private val now: () -> Instant = Instant::now,
    private val backoffMs: () -> Long = { releaseBackoffMs() },
    private val returnWaitMs: Long = RELEASE_ATTEMPT_BUDGET_MS + RETURN_WAIT_MARGIN_MS
) {

    @Inject
    constructor(
        foreground: AppForegroundTracker,
        store: LeaseStateStore,
        uploader: ReleaseUploader,
        client: BridgeClient,
        @ApplicationScope scope: CoroutineScope
    ) : this(foreground.isForeground, store, uploader, client, scope)

    private var started = false
    private var release: Job? = null
    private var returned = false
    private var appState: Job? = null

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
    }

    /** Releases if this page is a holder that isn't generating. Starts the upload at once. */
    private fun onBackground() {
        val status = store.status.value
        val state = store.lastState.value
        val epoch = state?.epoch
        if (state == null || epoch == null || status !is LeaseStatus.Holder || state.state != HOLDER) {
            Log.d(TAG, "Background: not holder, nothing to release")
            return
        }
        if (status.busy) {
            onBackgroundWhileBusy()
            return
        }
        returned = false
        val body = ReleaseMarker.uploadBody(state.pageId, epoch, now().truncatedTo(ChronoUnit.MILLIS).toString())
        release = scope.launch { uploadWithRetry(state.origin, body) }
    }

    /**
     * A reply is generating (`busy`): no release in slice 6. Slice 7 (A6) decides here: on API 34+
     * the reply service keeps the page running and releases when `busy` ends in the background;
     * on API 31–33 it interrupts (stop, bounded wait for `busy: false`, then release).
     */
    private fun onBackgroundWhileBusy() {
        Log.i(TAG, "Background while a reply generates: release deferred")
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
    }
}

/** E3's wait before another release attempt: a random 100–200 ms. */
internal fun releaseBackoffMs(random: Random = Random): Long = random.nextLong(100L, 201L)
