package com.tiffles.travelingtavern.data.bridge

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/** What one read of the lease file (`tt-session-lease.json`, extension spec E2) found. */
sealed interface LeaseFileRead {

    /** A readable lease in a known format, held by [holder] at [epoch]. */
    data class Found(val holder: String, val epoch: Long) : LeaseFileRead

    /** `404`: there is no lease file. */
    data object Missing : LeaseFileRead

    /** A lease in a format this app doesn't know: its bundled lease is outdated (E2 `format`). */
    data object UnknownFormat : LeaseFileRead

    /** Any other status, a network error, or a body that isn't a valid lease. Never "missing" or "free". */
    data object Unreadable : LeaseFileRead
}

/**
 * Parses the lease file the way `session-lease.js` does (`parseLease`, E2): a JSON object with
 * a known `format`, a non-empty `holder`, an integer `epoch` ≥ 1, a string `claimedAt`, a
 * boolean `busy` and an `active` that is an object or null. Unknown fields are ignored.
 */
object LeaseFileParser {

    /** The formats this app's lease reads (`KNOWN_FORMATS` in `session-lease.js`). */
    private val KNOWN_FORMATS = setOf(1L)

    private val json = Json

    fun parse(statusCode: Int, body: String?): LeaseFileRead = when {
        statusCode == HTTP_NOT_FOUND -> LeaseFileRead.Missing
        statusCode !in HTTP_OK_RANGE || body == null -> LeaseFileRead.Unreadable
        else -> parseBody(body)
    }

    private fun parseBody(body: String): LeaseFileRead {
        val lease = try {
            json.parseToJsonElement(body) as? JsonObject
        } catch (_: SerializationException) {
            null
        } ?: return LeaseFileRead.Unreadable
        if (lease.integer("format") !in KNOWN_FORMATS) return LeaseFileRead.UnknownFormat
        val holder = lease.text("holder")
        val epoch = lease.integer("epoch")
        val valid = holder != null && epoch != null && epoch >= 1 &&
            lease.primitive("claimedAt")?.isString == true &&
            lease.primitive("busy")?.takeUnless { it.isString }?.booleanOrNull != null &&
            lease["active"].let { it is JsonObject || it is JsonNull }
        return if (valid) LeaseFileRead.Found(holder, epoch) else LeaseFileRead.Unreadable
    }

    private fun JsonObject.primitive(key: String): JsonPrimitive? =
        (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }

    private fun JsonObject.text(key: String): String? =
        primitive(key)?.takeIf { it.isString }?.content?.takeIf { it.isNotEmpty() }

    /** A whole JSON number, as JavaScript's `Number.isInteger` sees it (`1.0` counts, `"1"` doesn't). */
    private fun JsonObject.integer(key: String): Long? {
        val number = primitive(key)?.takeUnless { it.isString }?.doubleOrNull ?: return null
        return if (number == Math.floor(number) && kotlin.math.abs(number) <= MAX_SAFE_INTEGER) number.toLong() else null
    }

    private const val MAX_SAFE_INTEGER = 9_007_199_254_740_991.0
    private const val HTTP_NOT_FOUND = 404
    private val HTTP_OK_RANGE = 200..299
}
