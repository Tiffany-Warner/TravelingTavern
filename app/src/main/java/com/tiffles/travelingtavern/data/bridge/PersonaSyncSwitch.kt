package com.tiffles.travelingtavern.data.bridge

import android.util.Log
import com.tiffles.travelingtavern.di.ApplicationScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns off the page's persona re-read (1.9's `syncPersonaSettings`) once `leaseInfo` reports the
 * desktop extension (`extensionLeaseVersion` not null; app spec, Changes to Existing Features).
 * With the extension, every page that writes loaded while holding the lease, so the page's copy
 * of persona settings can't be stale.
 *
 * `leaseInfo` is the one source. `bridge.js` starts every page load with the re-read on, so
 * "not reported yet" (before the first `leaseInfo`, or before the bridge is loaded) and
 * "no extension" both keep it. Sent once per page load, when the bridge is ready and an
 * extension is reported, in either order. A reload is seen as the combined value dropping to
 * false: page start, `ready` and `leaseInfo` are separate main-thread callbacks and the app scope
 * collects on `Main.immediate`, so the in-between false isn't conflated away.
 *
 * Call [start] once, from `Application.onCreate`.
 */
@Singleton
class PersonaSyncSwitch internal constructor(
    private val bridgeReady: Flow<Boolean>,
    private val extensionReported: Flow<Boolean>,
    private val sendExtensionReported: suspend () -> Unit,
    private val scope: CoroutineScope
) {

    @Inject
    constructor(
        bridge: BridgeRepository,
        lease: LeaseStateStore,
        client: BridgeClient,
        @ApplicationScope scope: CoroutineScope
    ) : this(
        bridgeReady = bridge.isReady,
        extensionReported = lease.extensionReported,
        sendExtensionReported = { client.setExtensionReported() },
        scope = scope
    )

    fun start() {
        scope.launch {
            combine(bridgeReady, extensionReported) { ready, reported -> ready && reported }
                .distinctUntilChanged()
                .collect { skip ->
                    if (skip) {
                        Log.d(TAG, "Extension reported: persona re-read off for this page")
                        sendExtensionReported()
                    }
                }
        }
    }

    private companion object {
        const val TAG = "PersonaSyncSwitch"
    }
}
