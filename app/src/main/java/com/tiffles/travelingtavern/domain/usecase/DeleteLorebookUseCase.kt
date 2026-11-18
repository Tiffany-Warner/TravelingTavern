package com.tiffles.travelingtavern.domain.usecase

import com.tiffles.travelingtavern.domain.repository.LorebookRepository
import javax.inject.Inject

/** Deletes a lorebook the way SillyTavern's own Delete button does. */
class DeleteLorebookUseCase @Inject constructor(private val repository: LorebookRepository) {
    suspend operator fun invoke(name: String): Result<Unit> = repository.delete(name)
}
