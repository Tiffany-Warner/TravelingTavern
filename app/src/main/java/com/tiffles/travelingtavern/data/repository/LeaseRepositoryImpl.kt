package com.tiffles.travelingtavern.data.repository

import com.tiffles.travelingtavern.data.bridge.BridgeClient
import com.tiffles.travelingtavern.data.bridge.BridgeMessage
import com.tiffles.travelingtavern.data.bridge.BridgeRepository
import com.tiffles.travelingtavern.data.bridge.LeaseGate
import com.tiffles.travelingtavern.data.bridge.LeaseStateStore
import com.tiffles.travelingtavern.data.bridge.sendAndAwait
import com.tiffles.travelingtavern.data.webview.WebViewHolder
import com.tiffles.travelingtavern.di.ApplicationScope
import com.tiffles.travelingtavern.domain.model.LeaseStatus
import com.tiffles.travelingtavern.domain.model.ModelBanner
import com.tiffles.travelingtavern.domain.repository.LeaseRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** [LeaseRepository] over [LeaseStateStore], the bridge and the WebView. */
@Singleton
class LeaseRepositoryImpl @Inject constructor(
    private val store: LeaseStateStore,
    private val client: BridgeClient,
    private val bridge: BridgeRepository,
    private val holder: WebViewHolder,
    gate: LeaseGate,
    @ApplicationScope private val scope: CoroutineScope
) : LeaseRepository {

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

    override suspend fun useHere() {
        val token = withContext(Dispatchers.Main) { store.onUseHere() }
        client.useHere()
        // A claim reports `claiming` right away; without any answer the tap was ignored.
        scope.launch(Dispatchers.Main) {
            delay(USE_HERE_ANSWER_MS)
            store.onUseHereUnanswered(token)
        }
    }

    override suspend fun reload() = withContext(Dispatchers.Main) { holder.reload() }

    override suspend fun continueWithoutProtection() = withContext(Dispatchers.Main) { holder.continueWithoutLease() }

    private companion object {
        const val USE_HERE_ANSWER_MS = 5_000L

        /** The page answers within its own 10 s wait (E10); the rest is slack for a busy page. */
        const val CONNECT_ANSWER_MS = 12_000L
    }
}
