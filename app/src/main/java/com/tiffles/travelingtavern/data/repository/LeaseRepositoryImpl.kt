package com.tiffles.travelingtavern.data.repository

import android.util.Log
import com.tiffles.travelingtavern.BuildConfig
import com.tiffles.travelingtavern.data.bridge.BridgeClient
import com.tiffles.travelingtavern.data.bridge.BridgeMessage
import com.tiffles.travelingtavern.data.bridge.BridgeRepository
import com.tiffles.travelingtavern.data.bridge.LeaseGate
import com.tiffles.travelingtavern.data.bridge.LeaseStateStore
import com.tiffles.travelingtavern.data.bridge.isExtensionPromptDue
import com.tiffles.travelingtavern.data.bridge.sendAndAwait
import com.tiffles.travelingtavern.data.bridge.toDesktopProtection
import com.tiffles.travelingtavern.data.settings.ExtensionPromptFlag
import com.tiffles.travelingtavern.data.webview.WebViewHolder
import com.tiffles.travelingtavern.di.ApplicationScope
import com.tiffles.travelingtavern.domain.model.DesktopProtection
import com.tiffles.travelingtavern.domain.model.LeaseStatus
import com.tiffles.travelingtavern.domain.model.ModelBanner
import com.tiffles.travelingtavern.domain.repository.LeaseRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** [LeaseRepository] over [LeaseStateStore], the bridge and the WebView. */
@Singleton
class LeaseRepositoryImpl internal constructor(
    private val store: LeaseStateStore,
    private val client: BridgeClient,
    private val bridge: BridgeRepository,
    private val holder: WebViewHolder,
    gate: LeaseGate,
    private val promptFlag: ExtensionPromptFlag,
    private val scope: CoroutineScope,
    appLeaseVersion: Int
) : LeaseRepository {

    @Inject
    constructor(
        store: LeaseStateStore,
        client: BridgeClient,
        bridge: BridgeRepository,
        holder: WebViewHolder,
        gate: LeaseGate,
        promptFlag: ExtensionPromptFlag,
        @ApplicationScope scope: CoroutineScope
    ) : this(store, client, bridge, holder, gate, promptFlag, scope, BuildConfig.LEASE_VERSION)

    override val status: StateFlow<LeaseStatus> = store.status

    override val unprotected: StateFlow<Boolean> = store.unprotected

    override val commandWaiting: StateFlow<Boolean> = gate.waiting

    override val stillLoading: Flow<Unit> = gate.stillLoading

    override val replyInterrupted: StateFlow<Boolean> = store.replyInterrupted

    override fun onReplyInterruptedShown() = store.onReplyInterruptedShown()

    override val modelBanner: StateFlow<ModelBanner?> = store.modelBanner

    override val replyNotStarted: Flow<Unit> = store.replyNotStarted

    /**
     * Runs on the app scope and is joined, so leaving the Chat screen mid-Connect still ends it
     * (the banner's `connecting` would otherwise stay set).
     */
    override suspend fun connectModel() {
        scope.launch { connect() }.join()
    }

    private suspend fun connect() {
        val token = withContext(Dispatchers.Main) { store.onConnectStarted() } ?: return
        val requestId = UUID.randomUUID().toString()
        var sent = false
        val answer = withTimeoutOrNull(CONNECT_ANSWER_MS) {
            bridge.messages.sendAndAwait({ client.connectModel(requestId).also { sent = it } }) {
                it is BridgeMessage.ConnectResult && it.requestId == requestId
            }
        } as? BridgeMessage.ConnectResult
        // No answer after it was sent counts as still not connected: the page's own wait is over.
        withContext(Dispatchers.Main) { store.onConnectFinished(token, tried = sent, connected = answer?.connected == true) }
    }

    override suspend fun useHere() = claim { client.useHere() }

    override suspend fun takeOverAnyway() {
        withContext(Dispatchers.Main) { store.onParkedReloadRequested() }
        claim { client.leaseAction(TAKE_OVER_ANYWAY) }
    }

    /** Shows claiming at once and calls [call]; a claim reports `claiming` right away, so no answer means it was ignored. */
    private suspend fun claim(call: suspend () -> Unit) {
        val token = withContext(Dispatchers.Main) { store.onUseHere() }
        call()
        scope.launch(Dispatchers.Main) {
            delay(USE_HERE_ANSWER_MS)
            store.onUseHereUnanswered(token)
        }
    }

    override suspend fun refresh() {
        withContext(Dispatchers.Main) { store.onParkedReloadRequested() }
        val answer = client.leaseAction(REFRESH)
        if (answer != true) {
            // Not offered (e.g. a holder page behind the app's own pre-check scrim), an older page, or no answer.
            Log.i(TAG, "Refresh not taken by the page (${answer ?: "no answer"}): reloading")
            withContext(Dispatchers.Main) { holder.reload() }
        }
    }

    override suspend fun turnOffProtection() {
        withContext(Dispatchers.Main) { store.onParkedReloadRequested() }
        if (client.leaseAction(TURN_OFF_PROTECTION) != true) Log.w(TAG, "Turn off protection not taken by the page")
    }

    override suspend fun turnProtectionOn() {
        // No reload fallback: the off flag survives a plain reload, so only the page can turn it on.
        if (client.leaseAction(TURN_PROTECTION_ON) != true) Log.w(TAG, "Turn protection on not taken by the page")
    }

    override suspend fun continueWithoutProtection() = withContext(Dispatchers.Main) { holder.continueWithoutLease() }

    override val desktopProtection: Flow<DesktopProtection?> =
        combine(store.info, bridge.isReady) { info, ready -> toDesktopProtection(info, ready, appLeaseVersion) }
            .distinctUntilChanged()

    override val extensionPromptDue: Flow<Boolean> =
        combine(desktopProtection, store.status, store.unprotected, promptFlag.shown, ::isExtensionPromptDue)
            .distinctUntilChanged()

    override suspend fun onExtensionPromptShown() = promptFlag.markShown()

    private companion object {
        const val TAG = "LeaseRepository"
        const val TAKE_OVER_ANYWAY = "takeOverAnyway"
        const val REFRESH = "refresh"
        const val TURN_OFF_PROTECTION = "turnOffProtection"
        const val TURN_PROTECTION_ON = "turnProtectionOn"
        const val USE_HERE_ANSWER_MS = 5_000L

        /** The page answers within its own 10 s wait (E10); the rest is slack for a busy page. */
        const val CONNECT_ANSWER_MS = 12_000L
    }
}
