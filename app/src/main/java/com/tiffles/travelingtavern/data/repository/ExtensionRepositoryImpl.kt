package com.tiffles.travelingtavern.data.repository

import android.util.Log
import com.tiffles.travelingtavern.data.bridge.BridgeClient
import com.tiffles.travelingtavern.data.bridge.BridgeMessage
import com.tiffles.travelingtavern.data.bridge.BridgeRepository
import com.tiffles.travelingtavern.data.bridge.sendAndAwait
import com.tiffles.travelingtavern.data.webview.PageReloader
import com.tiffles.travelingtavern.domain.model.ExtensionOutcome
import com.tiffles.travelingtavern.domain.model.ProjectLinks
import com.tiffles.travelingtavern.domain.repository.ExtensionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ExtensionRepository] through the page (A8, slice 9 Notes): a `bridge.js` command posts to
 * SillyTavern's own extension endpoints with SillyTavern's request headers, gated by the Kotlin
 * pre-check (user-started) and the page's lease. After an install, or an update that pulled
 * something, the WebView's HTTP cache is cleared and the page reloaded ([PageReloader]), so the
 * new extension files load and `leaseInfo` reports the new version.
 */
@Singleton
class ExtensionRepositoryImpl internal constructor(
    private val client: BridgeClient,
    private val bridge: BridgeRepository,
    private val page: PageReloader,
    private val answerTimeoutMs: Long
) : ExtensionRepository {

    @Inject
    constructor(client: BridgeClient, bridge: BridgeRepository, page: PageReloader) :
        this(client, bridge, page, ANSWER_TIMEOUT_MS)

    override suspend fun install(): ExtensionOutcome {
        val (sent, answer) = request { id -> client.installExtension(id, installBody()) }
        val outcome = when {
            !sent -> ExtensionOutcome.Refused
            answer?.ok == true -> ExtensionOutcome.Installed
            answer?.status == HTTP_CONFLICT -> ExtensionOutcome.AlreadyInstalled
            else -> ExtensionOutcome.InstallFailed
        }
        return finish("Install", outcome, answer)
    }

    override suspend fun update(): ExtensionOutcome {
        val (sent, answer) = request { id -> client.updateExtension(id, updateBody()) }
        val outcome = when {
            !sent -> ExtensionOutcome.Refused
            answer?.ok != true -> ExtensionOutcome.UpdateFailed
            answer.upToDate == true -> ExtensionOutcome.UpToDate
            else -> ExtensionOutcome.Updated
        }
        return finish("Update", outcome, answer)
    }

    /** Sends a command tagged with a new request id and waits for its `extensionResult` (null: no answer in time). */
    private suspend fun request(send: suspend (String) -> Boolean): Pair<Boolean, BridgeMessage.ExtensionResult?> {
        val requestId = UUID.randomUUID().toString()
        var sent = false
        val answer = withTimeoutOrNull(answerTimeoutMs) {
            bridge.messages.sendAndAwait({ send(requestId).also { sent = it } }) {
                it is BridgeMessage.ExtensionResult && it.requestId == requestId
            }
        } as? BridgeMessage.ExtensionResult
        return sent to answer
    }

    private suspend fun finish(
        action: String,
        outcome: ExtensionOutcome,
        answer: BridgeMessage.ExtensionResult?
    ): ExtensionOutcome {
        Log.i(TAG, "$action: $outcome (status ${answer?.status ?: "none"})")
        if (outcome == ExtensionOutcome.Installed || outcome == ExtensionOutcome.Updated) {
            withContext(Dispatchers.Main) { page.clearCacheAndReload() }
        }
        return outcome
    }

    internal companion object {
        /** Per user (no `global`, which needs admin) and **no `branch`**: SillyTavern clones the default branch (A8). */
        fun installBody(): JsonObject = buildJsonObject { put("url", JsonPrimitive(ProjectLinks.EXTENSION_REPO_URL)) }

        /** Per user; SillyTavern pulls the branch it has checked out. */
        fun updateBody(): JsonObject = buildJsonObject { put("extensionName", JsonPrimitive(ProjectLinks.EXTENSION_FOLDER)) }

        private const val TAG = "ExtensionRepository"
        private const val HTTP_CONFLICT = 409

        /** SillyTavern answers after its `git clone` or `pull` from GitHub, which can take a while. */
        private const val ANSWER_TIMEOUT_MS = 90_000L
    }
}
