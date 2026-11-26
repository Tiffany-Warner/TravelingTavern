package com.tiffles.travelingtavern.data.bridge

import android.util.Log
import com.tiffles.travelingtavern.data.lifecycle.AppForegroundTracker
import com.tiffles.travelingtavern.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Asks the page for its lease state again when the bridge is ready and no `leaseState` arrived
 * for this load (D48). A page with protection off reports once, at document start, which can land
 * before the app's page-start reset and be wiped with it; commands would then wait for a first
 * report that never comes. A protected page always reports before `ready` (SillyTavern's startup
 * requests wait for the start decision, E6), so this only fires when a report was lost.
 * `onForeground()` re-reports in every state (off included) and is a no-op without a lease.
 * Skipped while the app is in the background: there `onForeground()` could bump the epoch or claim
 * a released lease (E11), e.g. for an extension's own late copy while unprotected (D19).
 *
 * Call [start] once, from `Application.onCreate`.
 */
@Singleton
class LeaseReportRecovery internal constructor(
    private val bridgeReady: Flow<Boolean>,
    private val hasReport: () -> Boolean,
    private val inForeground: () -> Boolean,
    private val askAgain: suspend () -> Unit,
    private val scope: CoroutineScope
) {

    @Inject
    constructor(
        bridge: BridgeRepository,
        lease: LeaseStateStore,
        client: BridgeClient,
        foreground: AppForegroundTracker,
        @ApplicationScope scope: CoroutineScope
    ) : this(
        bridgeReady = bridge.isReady,
        hasReport = { lease.lastState.value != null },
        inForeground = { foreground.isForeground.value },
        askAgain = { client.onForeground() },
        scope = scope
    )

    fun start() {
        scope.launch {
            bridgeReady.distinctUntilChanged().collect { ready ->
                if (ready && !hasReport() && inForeground()) {
                    Log.i(TAG, "Bridge ready without a lease report: asking the page again")
                    askAgain()
                }
            }
        }
    }

    private companion object {
        const val TAG = "LeaseReportRecovery"
    }
}
