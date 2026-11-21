package com.tiffles.travelingtavern.data.bridge

import com.tiffles.travelingtavern.BuildConfig
import com.tiffles.travelingtavern.domain.model.LeaseStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The session lease as the app sees it: the page's last `leaseState` and `leaseInfo` (reset on
 * every page load) and the app-only states. Fed by [BridgeRepository] and the WebView host.
 * Main thread only for writes; the flows can be read from anywhere.
 *
 * [status] precedence: no document start, then a lease version mismatch, then the page's state.
 */
@Singleton
class LeaseStateStore internal constructor(private val appLeaseVersion: Int) {

    @Inject
    constructor() : this(BuildConfig.LEASE_VERSION)

    private var documentStartSupported = true
    private val _lastState = MutableStateFlow<BridgeMessage.LeaseState?>(null)
    private val _info = MutableStateFlow<BridgeMessage.LeaseInfo?>(null)
    private val _status = MutableStateFlow<LeaseStatus>(LeaseStatus.NotReported)

    /** The current load's last `leaseState` (pair, origin, `busy`) for the pre-check and release. Null until reported. */
    val lastState: StateFlow<BridgeMessage.LeaseState?> = _lastState.asStateFlow()

    /** The current load's last `leaseInfo` (extension prompts). Null until reported. */
    val info: StateFlow<BridgeMessage.LeaseInfo?> = _info.asStateFlow()

    /** What the parked scrim and the pre-check go by. */
    val status: StateFlow<LeaseStatus> = _status.asStateFlow()

    fun onLeaseState(message: BridgeMessage.LeaseState) {
        _lastState.value = message
        publish()
    }

    fun onLeaseInfo(message: BridgeMessage.LeaseInfo) {
        _info.value = message
        publish()
    }

    /** A new page load: nothing the previous one reported still holds. */
    fun onPageStarted() {
        _lastState.value = null
        _info.value = null
        publish()
    }

    /** `WebViewFeature.DOCUMENT_START_SCRIPT` is missing: SillyTavern won't be loaded (A2). */
    fun onDocumentStartUnsupported() {
        documentStartSupported = false
        publish()
    }

    private fun publish() {
        val info = _info.value
        val state = _lastState.value
        _status.value = when {
            !documentStartSupported -> LeaseStatus.NoDocumentStart
            info != null && info.leaseVersion != appLeaseVersion -> LeaseStatus.VersionMismatch
            state != null -> state.toLeaseStatus()
            else -> LeaseStatus.NotReported
        }
    }
}
