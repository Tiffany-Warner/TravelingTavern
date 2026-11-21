package com.tiffles.travelingtavern.data.remote

import com.tiffles.travelingtavern.domain.model.normalizeServerUrl
import com.tiffles.travelingtavern.domain.repository.ServerPolicy

/**
 * Allows only [allowedUrl], compared after the same normalization the Connect screen uses.
 * An address that can't be normalized allows nothing. Bound only in the `leaseTest` build
 * type (its `ServerPolicyModule`); debug and release bind [AnyServerPolicy].
 */
class SingleServerPolicy(allowedUrl: String) : ServerPolicy {
    private val allowed: String? = normalizeServerUrl(allowedUrl)

    override fun allows(baseUrl: String): Boolean = allowed != null && baseUrl == allowed
}
