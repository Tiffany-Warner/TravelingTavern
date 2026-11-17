package com.tiffles.travelingtavern.domain.usecase

import com.tiffles.travelingtavern.domain.model.ConnectionResult
import com.tiffles.travelingtavern.domain.model.normalizeServerUrl
import com.tiffles.travelingtavern.domain.repository.ConnectionTester
import javax.inject.Inject

/** Normalizes the typed address, then asks the server whether it is SillyTavern. */
class TestConnectionUseCase @Inject constructor(
    private val connectionTester: ConnectionTester
) {
    suspend operator fun invoke(
        rawUrl: String,
        username: String?,
        password: String?
    ): ConnectionResult {
        val baseUrl = normalizeServerUrl(rawUrl) ?: return ConnectionResult.InvalidUrl
        return connectionTester.test(
            baseUrl = baseUrl,
            username = username?.takeIf { it.isNotBlank() },
            password = password?.takeIf { it.isNotEmpty() }
        )
    }
}
