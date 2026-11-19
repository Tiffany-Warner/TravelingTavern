package com.tiffles.travelingtavern.domain.usecase

import com.tiffles.travelingtavern.domain.repository.LorebookRepository
import javax.inject.Inject

class DeleteLorebookEntryUseCase @Inject constructor(private val repository: LorebookRepository) {
    suspend operator fun invoke(lorebook: String, uid: Int): Result<Unit> = repository.deleteEntry(lorebook, uid)
}
