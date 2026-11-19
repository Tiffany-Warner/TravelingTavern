package com.tiffles.travelingtavern.domain.usecase

import com.tiffles.travelingtavern.domain.repository.LorebookRepository
import javax.inject.Inject

/** Turns a lorebook on or off for every chat (SillyTavern's global list). */
class SetLorebookActiveUseCase @Inject constructor(private val repository: LorebookRepository) {
    suspend operator fun invoke(name: String, active: Boolean): Result<Unit> = repository.setGlobal(name, active)
}
