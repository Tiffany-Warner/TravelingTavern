package com.tiffles.travelingtavern.domain.usecase

import com.tiffles.travelingtavern.domain.model.ExtensionOutcome
import com.tiffles.travelingtavern.domain.repository.ExtensionRepository
import javax.inject.Inject

/** Installs the desktop extension (A8). Runs only from a tap; never retried. */
class InstallExtensionUseCase @Inject constructor(private val repository: ExtensionRepository) {
    suspend operator fun invoke(): ExtensionOutcome = repository.install()
}
