package com.tiffles.travelingtavern.domain.usecase

import com.tiffles.travelingtavern.domain.model.CharacterSummary
import com.tiffles.travelingtavern.domain.model.LorebookCard
import com.tiffles.travelingtavern.domain.model.LorebookFilter
import javax.inject.Inject

/**
 * Applies the Lorebooks tab's chip and search to [cards] (order kept). The search text
 * matches lorebook names and the names of attached characters, case-insensitively.
 */
class FilterLorebooksUseCase @Inject constructor() {

    operator fun invoke(cards: List<LorebookCard>, filter: LorebookFilter, query: String): List<LorebookCard> {
        val text = query.trim()
        return cards.filter { it.passes(filter) && it.matches(text) }
    }

    /** Characters with at least one lorebook, by name — the Character chip's choices. */
    fun charactersWithLorebooks(cards: List<LorebookCard>): List<CharacterSummary> =
        cards.flatMap { it.characters }
            .distinctBy { it.id }
            .sortedWith(compareBy<CharacterSummary, String>(String.CASE_INSENSITIVE_ORDER) { it.name }.thenBy { it.id })

    private fun LorebookCard.passes(filter: LorebookFilter): Boolean = when (filter) {
        LorebookFilter.All -> true
        LorebookFilter.Global -> lorebook.isGlobal
        is LorebookFilter.Character -> characters.any { it.id == filter.characterId }
        LorebookFilter.Unused -> lorebook.isUnused
    }

    private fun LorebookCard.matches(text: String): Boolean =
        text.isEmpty() ||
            lorebook.name.contains(text, ignoreCase = true) ||
            characters.any { it.name.contains(text, ignoreCase = true) }
}
