package com.tiffles.travelingtavern.data.bridge

import android.util.Log
import com.tiffles.travelingtavern.BuildConfig
import com.tiffles.travelingtavern.domain.model.LeaseStatus
import com.tiffles.travelingtavern.domain.model.ModelBanner
import com.tiffles.travelingtavern.domain.model.ParkReason
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The session lease as the app sees it: the page's last `leaseState` and `leaseInfo` (reset on
 * every page load) and the app-only states. Fed by [BridgeRepository] and the WebView host.
 * Main thread only for writes; the flows can be read from anywhere.
 *
 * [status] precedence: no document start (unless continued without protection), then a lease
 * version mismatch, then a Use here waiting for the page's answer, then a failed pre-check (A4),
 * then the page's state, then [LeaseStatus.Unprotected]. Unprotected, an extension installed in SillyTavern can still run
 * its own lease (late, after SillyTavern's first requests): its state then wins, so a parked
 * page gets the scrim instead of hanging with its requests held.
 */
@Singleton
class LeaseStateStore internal constructor(private val appLeaseVersion: Int) {

    @Inject
    constructor() : this(BuildConfig.LEASE_VERSION)

    private var documentStartSupported = true
    private var useHereToken = 0
    private var useHerePending = false
    private var preCheckPark: ParkReason? = null
    private var connectToken = 0
    private val _lastState = MutableStateFlow<BridgeMessage.LeaseState?>(null)
    private val _info = MutableStateFlow<BridgeMessage.LeaseInfo?>(null)
    private val _status = MutableStateFlow<LeaseStatus>(LeaseStatus.NotReported)
    private val _unprotected = MutableStateFlow(false)
    private val _replyInterrupted = MutableStateFlow(false)
    private val _chatSaves = MutableStateFlow(0)
    private val _modelBanner = MutableStateFlow<ModelBanner?>(null)
    // No replay: a notice nobody is collecting at that moment (Chat not on screen) is dropped.
    private val _replyNotStarted = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** The current load's last `leaseState` (pair, origin, `busy`) for the pre-check and release. Null until reported. */
    val lastState: StateFlow<BridgeMessage.LeaseState?> = _lastState.asStateFlow()

    /** The current load's last `leaseInfo` (extension prompts). Null until reported. */
    val info: StateFlow<BridgeMessage.LeaseInfo?> = _info.asStateFlow()

    /** What the parked scrim and the pre-check go by. */
    val status: StateFlow<LeaseStatus> = _status.asStateFlow()

    /** Continue without protection was chosen (this process only): the warning banner shows. */
    val unprotected: StateFlow<Boolean> = _unprotected.asStateFlow()

    /** How many `chatSaved` reports the page has sent (the interrupt waits for the next one, A6). */
    val chatSaves: StateFlow<Int> = _chatSaves.asStateFlow()

    /** A reply was stopped because the app went to the background (A6), not yet shown in Chat. */
    val replyInterrupted: StateFlow<Boolean> = _replyInterrupted.asStateFlow()

    /** The Chat screen's "Not connected to a model" banner (A7), or null when it isn't shown. */
    val modelBanner: StateFlow<ModelBanner?> = _modelBanner.asStateFlow()

    /**
     * The page couldn't start a reply (`leaseNotice` `replyNotStarted`, A7). Not replayed: only a
     * collector present when it happens (the Chat screen on view) gets it.
     */
    val replyNotStarted: SharedFlow<Unit> = _replyNotStarted.asSharedFlow()

    /**
     * A takeover is reloading the page: the last `leaseState` reason is `claiming` or
     * `reloadPending` (A2), whatever [status] shows. Reset with the page, like [lastState].
     */
    val isTakeoverReload: Boolean
        get() = _lastState.value?.reason in TAKEOVER_REASONS

    fun onLeaseState(message: BridgeMessage.LeaseState) {
        _lastState.value = message
        // The banner lasts until SillyTavern connects or this page stops holding the lease (A7).
        if (message.connected || message.state != HOLDER) _modelBanner.value = null
        useHerePending = false
        preCheckPark = null
        publish()
    }

    fun onLeaseInfo(message: BridgeMessage.LeaseInfo) {
        if (message.leaseVersion != appLeaseVersion) {
            Log.w(TAG, "Page runs lease version ${message.leaseVersion}, the app bundles $appLeaseVersion")
        }
        _info.value = message
        publish()
    }

    /** A new page load: nothing the previous one reported still holds. */
    fun onPageStarted() {
        Log.d(TAG, "Page started: lease state reset")
        _lastState.value = null
        _info.value = null
        _modelBanner.value = null
        connectToken++
        useHerePending = false
        preCheckPark = null
        publish()
    }

    /** A `leaseNotice` (E11, A7). Unknown kinds are ignored. */
    fun onLeaseNotice(message: BridgeMessage.LeaseNotice) {
        when (message.kind) {
            NOTICE_NOT_CONNECTED -> onNotConnected()
            NOTICE_REPLY_NOT_STARTED -> _replyNotStarted.tryEmit(Unit)
            else -> Log.d(TAG, "Ignored unknown lease notice: ${message.kind}")
        }
    }

    /** Shows the banner, unless a later report already says connected or not holder. */
    private fun onNotConnected() {
        val state = _lastState.value
        if (state == null || state.state != HOLDER || state.connected) return
        if (_modelBanner.value == null) _modelBanner.value = ModelBanner()
    }

    /**
     * Connect was tapped on the banner. Returns a token for [onConnectFinished], or null when
     * there's nothing to do: no banner, or a Connect already running.
     */
    fun onConnectStarted(): Int? {
        val banner = _modelBanner.value ?: return null
        if (banner.connecting) return null
        _modelBanner.value = banner.copy(connecting = true)
        return ++connectToken
    }

    /**
     * Connect ended. [tried] false: it never reached the page (refused by the pre-check), so the
     * banner stays as it was. [connected] false after a try: the banner points to API Connections.
     * A banner already cleared (SillyTavern connected, parked, page load) stays cleared, and a
     * Connect from before a page load (an older [token]) leaves the new load's banner alone.
     */
    fun onConnectFinished(token: Int, tried: Boolean, connected: Boolean) {
        if (token != connectToken) return
        val banner = _modelBanner.value ?: return
        _modelBanner.value = when {
            tried && !connected -> ModelBanner(stillNotConnected = true)
            else -> banner.copy(connecting = false)
        }
    }

    /**
     * Use here was tapped: a parked page shows claiming at once (A3), until its next
     * `leaseState` or page load. Returns a token for [onUseHereUnanswered].
     */
    fun onUseHere(): Int {
        useHerePending = true
        publish()
        return ++useHereToken
    }

    /**
     * The page didn't answer Use here (it ignores a tap it can't act on and then reports
     * nothing): back to its last state, also dropping a failed pre-check's parked state (a
     * holder page that never reports again would otherwise stay covered). A token from an
     * earlier tap does nothing.
     */
    fun onUseHereUnanswered(token: Int) {
        if (!useHerePending || token != useHereToken) return
        useHerePending = false
        preCheckPark = null
        publish()
    }

    /**
     * The Kotlin pre-check (A4) found the lease's pair changed or the file unreadable: show
     * parked with [reason] at once, until the page's next `leaseState` (or a page load).
     */
    fun onPreCheckFailed(reason: ParkReason) {
        preCheckPark = reason
        publish()
    }

    /** The page reported that its chat save after the interrupt's Stop completed. */
    fun onChatSaved() {
        _chatSaves.value++
    }

    /** The interrupt (A6) stopped a reply: the Chat screen tells the user on return. */
    fun onReplyInterrupted() {
        _replyInterrupted.value = true
    }

    /** The Chat screen showed the interrupted-reply notice. */
    fun onReplyInterruptedShown() {
        _replyInterrupted.value = false
    }

    /** Continue without protection (A2): SillyTavern loads without the lease for this process. */
    fun onContinueWithoutProtection() {
        _unprotected.value = true
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
        val pageStatus = preCheckPark?.let(LeaseStatus::Parked) ?: state?.toLeaseStatus()
        _status.value = when {
            !documentStartSupported && !_unprotected.value -> LeaseStatus.NoDocumentStart
            info != null && info.leaseVersion != appLeaseVersion -> LeaseStatus.VersionMismatch
            useHerePending && pageStatus is LeaseStatus.Parked -> LeaseStatus.Parked(ParkReason.Claiming)
            pageStatus != null -> pageStatus
            _unprotected.value -> LeaseStatus.Unprotected
            else -> LeaseStatus.NotReported
        }
    }

    private companion object {
        const val TAG = "LeaseStateStore"
        const val HOLDER = "holder"
        const val NOTICE_NOT_CONNECTED = "notConnected"
        const val NOTICE_REPLY_NOT_STARTED = "replyNotStarted"
        val TAKEOVER_REASONS = setOf("claiming", "reloadPending")
    }
}
