package com.tiffles.travelingtavern.domain.usecase

import com.tiffles.travelingtavern.domain.model.CharacterSummary
import com.tiffles.travelingtavern.domain.repository.CharacterRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/**
 * The Characters tab's grid: the whole library sorted by name, filtered by [query]
 * (case-insensitive, matches anywhere in the name). Null until SillyTavern has reported it.
 */
class GetCharacterLibraryUseCase @Inject constructor(
    private val repository: CharacterRepository
) {
    operator fun invoke(query: Flow<String>): Flow<List<CharacterSummary>?> =
        combine(repository.characters, query) { characters, text ->
            characters?.filter { it.matches(text.trim()) }?.sortedWith(byName)
        }

    private fun CharacterSummary.matches(text: String): Boolean =
        text.isEmpty() || name.contains(text, ignoreCase = true)

    private companion object {
        val byName = compareBy<CharacterSummary, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
            .thenBy { it.id }
    }
}
