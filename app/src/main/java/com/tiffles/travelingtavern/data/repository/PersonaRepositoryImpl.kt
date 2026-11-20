package com.tiffles.travelingtavern.data.repository

import android.util.Log
import com.tiffles.travelingtavern.data.bridge.BridgeClient
import com.tiffles.travelingtavern.data.bridge.BridgeMessage
import com.tiffles.travelingtavern.data.bridge.BridgeRepository
import com.tiffles.travelingtavern.data.bridge.PersonaInput
import com.tiffles.travelingtavern.data.bridge.toDomain
import com.tiffles.travelingtavern.data.media.PictureEncoder
import com.tiffles.travelingtavern.data.webview.WebViewHolder
import com.tiffles.travelingtavern.domain.model.PageState
import com.tiffles.travelingtavern.domain.model.Persona
import com.tiffles.travelingtavern.domain.model.PersonaChanges
import com.tiffles.travelingtavern.domain.repository.PersonaException
import com.tiffles.travelingtavern.domain.repository.PersonaFailure
import com.tiffles.travelingtavern.domain.repository.PersonaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [PersonaRepository] backed by the JS bridge. Each action is tagged with a request ID and
 * waits for the page's answer carrying the same ID (including any wait for the page to
 * load), up to a timeout.
 */
@Singleton
class PersonaRepositoryImpl @Inject constructor(
    private val bridge: BridgeRepository,
    private val client: BridgeClient,
    private val holder: WebViewHolder,
    private val pictures: PictureEncoder
) : PersonaRepository {

    private val nextRequestId = AtomicLong()

    override val personas: Flow<List<Persona>?> = bridge.personas

    override val pageState: Flow<PageState> = holder.pageState

    override suspend fun refresh() {
        withTimeoutOrNull(REFRESH_TIMEOUT_MS) {
            bridge.isReady.first { it }
            bridge.messages
                .onSubscription { client.requestPersonas(refreshPictures = true) }
                .first { it is BridgeMessage.Personas || it is BridgeMessage.Error }
        }
    }

    override fun retry() = holder.retry()

    override suspend fun select(avatarId: String, lockToChat: Boolean): Result<Unit> =
        ask { client.selectPersona(it, avatarId, lockToChat) }.toResult()

    override suspend fun details(avatarId: String): Result<Persona> {
        val answer = ask { client.requestPersona(it, avatarId) }
        val persona = (answer as? BridgeMessage.PersonaResult)?.takeIf { it.ok }?.persona
        return if (persona != null) Result.success(persona.toDomain()) else failure(answer)
    }

    override suspend fun create(changes: PersonaChanges): Result<String> {
        val input = changes.toInput().getOrElse { return Result.failure(it) }
        val answer = ask { client.createPersona(it, input) }
        val avatarId = (answer as? BridgeMessage.PersonaResult)?.takeIf { it.ok }?.avatarId
        return if (avatarId != null) Result.success(avatarId) else failure(answer)
    }

    override suspend fun save(avatarId: String, changes: PersonaChanges): Result<Unit> {
        val input = changes.toInput().getOrElse { return Result.failure(it) }
        return ask { client.savePersona(it, avatarId, input) }.toResult()
    }

    override suspend fun delete(avatarId: String): Result<Unit> =
        ask { client.deletePersona(it, avatarId) }.toResult()

    override suspend fun setDefault(avatarId: String, isDefault: Boolean): Result<Unit> =
        ask { client.setDefaultPersona(it, avatarId, isDefault) }.toResult()

    override suspend fun countTokens(text: String): Int? =
        (ask(TOKEN_TIMEOUT_MS) { client.countTokens(it, text) } as? BridgeMessage.TokenCount)?.count

    /** The bridge's fields; a picked picture is read and encoded here. */
    private suspend fun PersonaChanges.toInput(): Result<PersonaInput> {
        val picture = pictureUri?.let { uri ->
            pictures.encode(uri) ?: return Result.failure(PersonaException(PersonaFailure.PictureUnreadable))
        }
        return Result.success(PersonaInput(name = name, title = title, description = description, picture = picture))
    }

    /** Sends a tagged request (once the page is ready) and returns its answer, or null on timeout. */
    private suspend fun ask(
        timeoutMs: Long = ACTION_TIMEOUT_MS,
        send: suspend (requestId: String) -> Unit
    ): BridgeMessage? {
        val requestId = "pe${nextRequestId.incrementAndGet()}"
        val answer = withTimeoutOrNull(timeoutMs) {
            bridge.isReady.first { it }
            bridge.messages
                .onSubscription { send(requestId) }
                .first { it.requestId() == requestId }
        }
        if (answer == null) Log.w(TAG, "No answer to a persona request within $timeoutMs ms")
        return answer
    }

    private fun BridgeMessage.requestId(): String? = when (this) {
        is BridgeMessage.PersonaResult -> requestId
        is BridgeMessage.TokenCount -> requestId
        else -> null
    }

    private fun BridgeMessage?.toResult(): Result<Unit> =
        if (this is BridgeMessage.PersonaResult && ok) Result.success(Unit) else failure(this)

    private fun <T> failure(answer: BridgeMessage?): Result<T> {
        val reason = when ((answer as? BridgeMessage.PersonaResult)?.error) {
            ERROR_NOT_FOUND -> PersonaFailure.NotFound
            else -> PersonaFailure.Failed
        }
        return Result.failure(PersonaException(reason))
    }

    internal companion object {
        private const val TAG = "PersonaRepository"
        private const val ERROR_NOT_FOUND = "notFound"

        /** Longer than lorebook actions: create and save may upload a picture first. */
        const val ACTION_TIMEOUT_MS = 20_000L
        const val REFRESH_TIMEOUT_MS = 15_000L
        const val TOKEN_TIMEOUT_MS = 5_000L
    }
}
