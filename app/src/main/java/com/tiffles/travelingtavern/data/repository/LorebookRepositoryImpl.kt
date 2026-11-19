package com.tiffles.travelingtavern.data.repository

import android.util.Log
import com.tiffles.travelingtavern.data.bridge.BridgeClient
import com.tiffles.travelingtavern.data.bridge.BridgeMessage
import com.tiffles.travelingtavern.data.bridge.BridgeRepository
import com.tiffles.travelingtavern.data.bridge.LorebookEntryDto
import com.tiffles.travelingtavern.data.bridge.LorebookEntryInput
import com.tiffles.travelingtavern.data.webview.WebViewHolder
import com.tiffles.travelingtavern.domain.model.Lorebook
import com.tiffles.travelingtavern.domain.model.LorebookEntry
import com.tiffles.travelingtavern.domain.model.PageState
import com.tiffles.travelingtavern.domain.repository.LorebookException
import com.tiffles.travelingtavern.domain.repository.LorebookFailure
import com.tiffles.travelingtavern.domain.repository.LorebookRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [LorebookRepository] backed by the JS bridge. Each action is tagged with a request ID and
 * waits for the page's answer carrying the same ID (including any wait for the page to
 * load), up to a timeout.
 */
@Singleton
class LorebookRepositoryImpl @Inject constructor(
    private val bridge: BridgeRepository,
    private val client: BridgeClient,
    private val holder: WebViewHolder
) : LorebookRepository {

    private val nextRequestId = AtomicLong()
    private val _entriesChanged = MutableSharedFlow<String>(extraBufferCapacity = EVENT_BUFFER)

    override val lorebooks: Flow<List<Lorebook>?> = bridge.lorebooks

    override val pageState: Flow<PageState> = holder.pageState

    override val entriesChanged: SharedFlow<String> = _entriesChanged.asSharedFlow()

    override suspend fun refresh() {
        withTimeoutOrNull(REFRESH_TIMEOUT_MS) {
            bridge.isReady.first { it }
            bridge.messages
                .onSubscription { client.requestLorebooks(fromServer = true) }
                .first { it is BridgeMessage.Lorebooks || it is BridgeMessage.Error }
        }
    }

    override fun retry() = holder.retry()

    override suspend fun entries(name: String): Result<List<LorebookEntry>> =
        when (val answer = ask { client.requestLorebook(it, name) }) {
            is BridgeMessage.LorebookEntries -> Result.success(answer.entries.map { it.toDomain() })
            else -> failure(answer)
        }

    override suspend fun create(name: String): Result<String> = ask { client.createLorebook(it, name) }
        .toResult { it.name ?: name }

    override suspend fun delete(name: String): Result<Unit> = ask { client.deleteLorebook(it, name) }
        .toResult { }

    override suspend fun setGlobal(name: String, enabled: Boolean): Result<Unit> =
        ask { client.setLorebookGlobal(it, name, enabled) }.toResult { }

    override suspend fun saveEntry(lorebook: String, entry: LorebookEntry): Result<Int> {
        val input = LorebookEntryInput(entry.uid, entry.title, entry.keywords, entry.content, entry.enabled)
        val result = ask { client.saveLorebookEntry(it, lorebook, input) }.toResult { it.uid ?: entry.uid ?: -1 }
        if (result.isSuccess) _entriesChanged.tryEmit(lorebook)
        return result
    }

    override suspend fun deleteEntry(lorebook: String, uid: Int): Result<Unit> {
        val result = ask { client.deleteLorebookEntry(it, lorebook, uid) }.toResult { }
        if (result.isSuccess) _entriesChanged.tryEmit(lorebook)
        return result
    }

    override suspend fun countTokens(text: String): Int? =
        (ask(TOKEN_TIMEOUT_MS) { client.countTokens(it, text) } as? BridgeMessage.TokenCount)?.count

    /** Sends a tagged request (once the page is ready) and returns its answer, or null on timeout. */
    private suspend fun ask(
        timeoutMs: Long = ACTION_TIMEOUT_MS,
        send: suspend (requestId: String) -> Unit
    ): BridgeMessage? {
        val requestId = "lb${nextRequestId.incrementAndGet()}"
        val answer = withTimeoutOrNull(timeoutMs) {
            bridge.isReady.first { it }
            bridge.messages
                .onSubscription { send(requestId) }
                .first { it.requestId() == requestId }
        }
        if (answer == null) Log.w(TAG, "No answer to a lorebook request within $timeoutMs ms")
        return answer
    }

    private fun BridgeMessage.requestId(): String? = when (this) {
        is BridgeMessage.LorebookEntries -> requestId
        is BridgeMessage.LorebookResult -> requestId
        is BridgeMessage.TokenCount -> requestId
        else -> null
    }

    private inline fun <T> BridgeMessage?.toResult(value: (BridgeMessage.LorebookResult) -> T): Result<T> =
        if (this is BridgeMessage.LorebookResult && ok) Result.success(value(this)) else failure(this)

    private fun <T> failure(answer: BridgeMessage?): Result<T> {
        val reason = when ((answer as? BridgeMessage.LorebookResult)?.error) {
            ERROR_NOT_FOUND -> LorebookFailure.NotFound
            ERROR_EXISTS -> LorebookFailure.NameTaken
            else -> LorebookFailure.Failed
        }
        return Result.failure(LorebookException(reason))
    }

    private fun LorebookEntryDto.toDomain() = LorebookEntry(
        uid = uid,
        title = title,
        keywords = keys,
        content = content,
        enabled = enabled
    )

    internal companion object {
        private const val TAG = "LorebookRepository"
        private const val EVENT_BUFFER = 8
        private const val ERROR_NOT_FOUND = "notFound"
        private const val ERROR_EXISTS = "exists"
        const val ACTION_TIMEOUT_MS = 10_000L
        const val REFRESH_TIMEOUT_MS = 15_000L
        const val TOKEN_TIMEOUT_MS = 5_000L
    }
}
