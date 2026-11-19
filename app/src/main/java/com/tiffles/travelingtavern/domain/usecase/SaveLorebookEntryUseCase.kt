package com.tiffles.travelingtavern.domain.usecase

import com.tiffles.travelingtavern.domain.model.LorebookEntry
import com.tiffles.travelingtavern.domain.repository.LorebookRepository
import javax.inject.Inject

/**
 * Saves an entry. Keywords are trimmed and blanks dropped; everything else is saved as
 * typed. Returns the entry's uid (new for a new entry).
 */
class SaveLorebookEntryUseCase @Inject constructor(private val repository: LorebookRepository) {
    suspend operator fun invoke(lorebook: String, entry: LorebookEntry): Result<Int> =
        repository.saveEntry(lorebook, entry.copy(keywords = entry.keywords.map { it.trim() }.filter { it.isNotEmpty() }))
}
