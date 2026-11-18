package com.tiffles.travelingtavern.domain.usecase

import com.tiffles.travelingtavern.domain.model.LorebookEntry
import com.tiffles.travelingtavern.domain.repository.LorebookRepository
import javax.inject.Inject

/** A lorebook's entries, read fresh from the server, in SillyTavern's display order. */
class GetLorebookEntriesUseCase @Inject constructor(private val repository: LorebookRepository) {
    suspend operator fun invoke(name: String): Result<List<LorebookEntry>> = repository.entries(name)
}
