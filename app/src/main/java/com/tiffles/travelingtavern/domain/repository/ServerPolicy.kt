package com.tiffles.travelingtavern.domain.repository

/**
 * Which servers this build may connect to. Debug and release builds allow any server; the
 * `leaseTest` build allows only its test install (session lease spec, A9).
 */
fun interface ServerPolicy {
    /** [baseUrl] is a normalized `scheme://host[:port]`. */
    fun allows(baseUrl: String): Boolean
}
