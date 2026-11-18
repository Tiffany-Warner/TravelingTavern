package com.tiffles.travelingtavern.domain.usecase

import com.tiffles.travelingtavern.domain.model.CharacterSummary
import com.tiffles.travelingtavern.domain.model.LorebookCard
import com.tiffles.travelingtavern.domain.repository.CharacterRepository
import com.tiffles.travelingtavern.domain.repository.LorebookRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/**
 * Every lorebook with its attached characters resolved (unknown IDs dropped, sorted by
 * name), sorted by lorebook name. Null until SillyTavern has reported both lists.
 */
class GetLorebooksUseCase @Inject constructor(
    private val lorebooks: LorebookRepository,
    private val characters: CharacterRepository
) {
    operator fun invoke(): Flow<List<LorebookCard>?> =
        combine(lorebooks.lorebooks, characters.characters) { books, library ->
            if (books == null || library == null) return@combine null
            val byId = library.associateBy { it.id }
            books
                .map { book ->
                    LorebookCard(
                        lorebook = book,
                        characters = book.characterIds.mapNotNull(byId::get).distinct().sortedWith(characterByName)
                    )
                }
                .sortedWith(cardByName)
        }

    private companion object {
        val characterByName = compareBy<CharacterSummary, String>(String.CASE_INSENSITIVE_ORDER) { it.name }
            .thenBy { it.id }
        val cardByName = compareBy<LorebookCard, String>(String.CASE_INSENSITIVE_ORDER) { it.lorebook.name }
    }
}
