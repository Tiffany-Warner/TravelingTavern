package com.tiffles.travelingtavern.data.remote

import android.util.Log
import android.webkit.CookieManager
import com.tiffles.travelingtavern.data.bridge.RELEASE_ATTEMPT_BUDGET_MS
import com.tiffles.travelingtavern.data.bridge.ReleaseUploader
import com.tiffles.travelingtavern.data.repository.ServerConfigRepositoryImpl
import com.tiffles.travelingtavern.data.webview.isSameServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes the release marker from Kotlin (A5), so it works with the page's JavaScript paused or
 * frozen: `POST /api/files/upload` through the app's [OkHttpClient] (basic auth for the saved
 * server), with the WebView's cookies (SillyTavern's session) and SillyTavern's CSRF header.
 *
 * **CSRF token** (R42, E11): fetched from `GET /csrf-token` with the same cookies (SillyTavern
 * returns the session's existing token, so the page's keeps working), kept in memory only,
 * reused, fetched again once after a `403`. Never logged or stored.
 *
 * One upload attempt, CSRF fetches included, finishes within [attemptBudgetMs]: the return path
 * waits for an attempt in flight (LeaseLifecycle), so it must be bounded. Logs carry outcomes
 * only, never the token, cookies, the URL or the marker.
 */
@Singleton
class OkHttpReleaseUploader internal constructor(
    private val client: OkHttpClient,
    private val baseUrl: suspend () -> String?,
    private val cookies: suspend (String) -> String?,
    private val attemptBudgetMs: Long = RELEASE_ATTEMPT_BUDGET_MS
) : ReleaseUploader {

    @Inject
    constructor(client: OkHttpClient, serverConfig: ServerConfigRepositoryImpl) : this(
        client = client,
        baseUrl = serverConfig::baseUrl,
        cookies = { url -> withContext(Dispatchers.Main) { CookieManager.getInstance().getCookie(url) } }
    )

    /** In memory only; never logged. */
    @Volatile private var token: String? = null

    override suspend fun upload(origin: String, body: String): Boolean {
        val saved = baseUrl()
        if (saved == null || !isSameServer(origin, saved)) {
            Log.w(TAG, "Lease origin isn't the saved server; release not sent")
            return false
        }
        val base = origin.trimEnd('/')
        val cookie = cookies(base + UPLOAD_PATH)
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(attemptBudgetMs)
        return withContext(Dispatchers.IO) {
            try {
                send(base, cookie, body, deadline)
            } catch (e: IOException) {
                Log.w(TAG, "Release upload failed: ${e.javaClass.simpleName}")
                false
            }
        }
    }

    private fun send(base: String, cookie: String?, body: String, deadline: Long): Boolean {
        val first = token ?: fetchToken(base, cookie, deadline) ?: return false
        val code = post(base, cookie, body, first, deadline)
        if (code != HTTP_FORBIDDEN) return code.isSuccess()
        Log.w(TAG, "Release refused (403): fetching the CSRF token again")
        token = null
        val fresh = fetchToken(base, cookie, deadline) ?: return false
        return post(base, cookie, body, fresh, deadline).isSuccess()
    }

    private fun post(base: String, cookie: String?, body: String, csrf: String, deadline: Long): Int {
        val request = Request.Builder()
            .url(base + UPLOAD_PATH)
            .header("X-CSRF-Token", csrf)
            .withCookie(cookie)
            .post(body.toRequestBody(JSON))
            .build()
        return execute(request, deadline) { it.code }
    }

    /** SillyTavern's `GET /csrf-token` answers `{ "token": "…" }`. Null (logged) when it can't be read. */
    private fun fetchToken(base: String, cookie: String?, deadline: Long): String? {
        val request = Request.Builder()
            .url(base + CSRF_PATH)
            .header("Cache-Control", "no-cache")
            .withCookie(cookie)
            .build()
        val fetched = execute(request, deadline) { response ->
            if (response.isSuccessful) parseToken(response.body?.string()) else null
        }
        if (fetched == null) Log.w(TAG, "CSRF token not available; release not sent")
        token = fetched
        return fetched
    }

    /** Runs one call with what is left of this attempt's budget. */
    private fun <T> execute(request: Request, deadline: Long, read: (Response) -> T): T {
        val left = deadline - System.nanoTime()
        if (left <= 0) throw IOException("attempt budget used up")
        val call = client.newCall(request)
        call.timeout().timeout(left, TimeUnit.NANOSECONDS)
        return call.execute().use(read)
    }

    private fun Request.Builder.withCookie(cookie: String?) =
        apply { if (!cookie.isNullOrEmpty()) header("Cookie", cookie) }

    private fun Int.isSuccess() = this in HTTP_OK_RANGE

    private companion object {
        const val TAG = "ReleaseUploader"
        const val UPLOAD_PATH = "/api/files/upload"
        const val CSRF_PATH = "/csrf-token"
        const val HTTP_FORBIDDEN = 403
        val HTTP_OK_RANGE = 200..299
        val JSON = "application/json".toMediaType()

        fun parseToken(body: String?): String? = try {
            val value = (body?.let(Json::parseToJsonElement) as? JsonObject)?.get("token") as? JsonPrimitive
            value?.takeIf { it.isString }?.content?.takeIf { it.isNotEmpty() }
        } catch (_: SerializationException) {
            null
        }
    }
}
