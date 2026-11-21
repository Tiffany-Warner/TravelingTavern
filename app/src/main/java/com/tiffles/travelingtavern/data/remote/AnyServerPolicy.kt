package com.tiffles.travelingtavern.data.remote

import com.tiffles.travelingtavern.domain.repository.ServerPolicy
import javax.inject.Inject

/** The debug and release policy: every server is allowed. */
class AnyServerPolicy @Inject constructor() : ServerPolicy {
    override fun allows(baseUrl: String): Boolean = true
}
