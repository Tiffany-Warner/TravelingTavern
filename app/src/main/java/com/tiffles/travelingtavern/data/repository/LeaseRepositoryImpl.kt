package com.tiffles.travelingtavern.data.repository

import com.tiffles.travelingtavern.data.bridge.BridgeClient
import com.tiffles.travelingtavern.data.bridge.LeaseGate
import com.tiffles.travelingtavern.data.bridge.LeaseStateStore
import com.tiffles.travelingtavern.data.webview.WebViewHolder
import com.tiffles.travelingtavern.di.ApplicationScope
import com.tiffles.travelingtavern.domain.model.LeaseStatus
import com.tiffles.travelingtavern.domain.repository.LeaseRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** [LeaseRepository] over [LeaseStateStore], the bridge and the WebView. */
@Singleton
class LeaseRepositoryImpl @Inject constructor(
    private val store: LeaseStateStore,
    private val client: BridgeClient,
    private val holder: WebViewHolder,
    gate: LeaseGate,
    @ApplicationScope private val scope: CoroutineScope
) : LeaseRepository {

    override val status: StateFlow<LeaseStatus> = store.status

    override val unprotected: StateFlow<Boolean> = store.unprotected

    override val commandWaiting: StateFlow<Boolean> = gate.waiting

    override val stillLoading: Flow<Unit> = gate.stillLoading

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
    }
}
