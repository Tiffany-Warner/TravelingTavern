package com.tiffles.travelingtavern.data.bridge

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.util.Base64

/**
 * Time one release upload attempt may take, CSRF fetches included (it covers a token fetch, the
 * upload, and the `403` path's second fetch and upload). Bounds the return path's wait (A5).
 */
const val RELEASE_ATTEMPT_BUDGET_MS = 5_000L

/**
 * Uploads the release marker (`tt-session-release.json`, E2) to the page's [origin] with
 * SillyTavern's `POST /api/files/upload`. One attempt: the caller retries (E3). True when
 * SillyTavern answered 2xx.
 */
fun interface ReleaseUploader {
    suspend fun upload(origin: String, body: String): Boolean
}

/** The release marker and its upload body, as `session-lease.js` writes them (`markerFor`, `uploadBody`). */
object ReleaseMarker {

    /** The lease file format this app writes (`LEASE_FORMAT` in `session-lease.js`). */
    const val FORMAT = 1

    const val FILE_NAME = "tt-session-release.json"

    /** The marker JSON: `format`, the exact pair given up, and an informational [releasedAt]. */
    fun json(holder: String, epoch: Long, releasedAt: String): String = buildJsonObject {
        put("format", JsonPrimitive(FORMAT))
        put("holder", JsonPrimitive(holder))
        put("epoch", JsonPrimitive(epoch))
        put("releasedAt", JsonPrimitive(releasedAt))
    }.toString()

    /** SillyTavern's upload body: `{ name, data }`, data the base64 of the marker's UTF-8 JSON. */
    fun uploadBody(holder: String, epoch: Long, releasedAt: String): String = buildJsonObject {
        put("name", JsonPrimitive(FILE_NAME))
        put("data", JsonPrimitive(Base64.getEncoder().encodeToString(json(holder, epoch, releasedAt).toByteArray(Charsets.UTF_8))))
    }.toString()
}
