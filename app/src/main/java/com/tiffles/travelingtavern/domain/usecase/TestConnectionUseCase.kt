package com.tiffles.travelingtavern.domain.usecase

import com.tiffles.travelingtavern.domain.model.ConnectionResult
import com.tiffles.travelingtavern.domain.model.normalizeServerUrl
import com.tiffles.travelingtavern.domain.repository.ConnectionTester
import com.tiffles.travelingtavern.domain.repository.ServerPolicy
import javax.inject.Inject

/**
 * Normalizes the typed address, checks this build may use it, then asks the server whether
 * it is SillyTavern.
 */
class TestConnectionUseCase @Inject constructor(
    private val connectionTester: ConnectionTester,
    private val serverPolicy: ServerPolicy
) {
    suspend operator fun invoke(
        rawUrl: String,
        username: String?,
        password: String?
    ): ConnectionResult {
        val baseUrl = normalizeServerUrl(rawUrl) ?: return ConnectionResult.InvalidUrl
        if (!serverPolicy.allows(baseUrl)) return ConnectionResult.ServerNotAllowed
        return connectionTester.test(
            baseUrl = baseUrl,
            username = username?.takeIf { it.isNotBlank() },
            password = password?.takeIf { it.isNotEmpty() }
        )
    }
}
