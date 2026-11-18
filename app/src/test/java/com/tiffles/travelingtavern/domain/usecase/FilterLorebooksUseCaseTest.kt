package com.tiffles.travelingtavern.domain.usecase

import com.tiffles.travelingtavern.domain.character
import com.tiffles.travelingtavern.domain.lorebook
import com.tiffles.travelingtavern.domain.model.LorebookCard
import com.tiffles.travelingtavern.domain.model.LorebookFilter
import org.junit.Assert.assertEquals
import org.junit.Test

class FilterLorebooksUseCaseTest {

    private val filter = FilterLorebooksUseCase()

    private val seraphina = character("Seraphina")
    private val bob = character("Bob")

    private val eldoria = LorebookCard(lorebook("Eldoria", characterIds = listOf(seraphina.id)), listOf(seraphina))
    private val rules = LorebookCard(lorebook("House rules", global = true), emptyList())
    private val magic = LorebookCard(lorebook("Magic system", global = true, characterIds = listOf(bob.id)), listOf(bob))
    private val persona = LorebookCard(lorebook("About me", personaNames = listOf("Me")), emptyList())
    private val old = LorebookCard(lorebook("Old notes"), emptyList())
    private val all = listOf(persona, eldoria, rules, magic, old)

    private fun names(cards: List<LorebookCard>) = cards.map { it.lorebook.name }

    @Test
    fun `All keeps everything in order`() {
        assertEquals(names(all), names(filter(all, LorebookFilter.All, "")))
    }

    @Test
    fun `Global keeps only global lorebooks`() {
        assertEquals(listOf("House rules", "Magic system"), names(filter(all, LorebookFilter.Global, "")))
    }

    @Test
    fun `Character keeps that character's lorebooks`() {
        assertEquals(listOf("Eldoria"), names(filter(all, LorebookFilter.Character(seraphina.id), "")))
        assertEquals(listOf("Magic system"), names(filter(all, LorebookFilter.Character(bob.id), "")))
    }

    @Test
    fun `Unused excludes global, character and persona lorebooks`() {
        assertEquals(listOf("Old notes"), names(filter(all, LorebookFilter.Unused, "")))
    }

    @Test
    fun `search matches lorebook names, case-insensitively and trimmed`() {
        assertEquals(listOf("Eldoria"), names(filter(all, LorebookFilter.All, "  eLDo ")))
    }

    @Test
    fun `search matches attached character names`() {
        assertEquals(listOf("Eldoria"), names(filter(all, LorebookFilter.All, "Sera")))
    }

    @Test
    fun `chip and search combine`() {
        assertEquals(listOf("Magic system"), names(filter(all, LorebookFilter.Global, "magic")))
        assertEquals(emptyList<String>(), names(filter(all, LorebookFilter.Unused, "magic")))
    }

    @Test
    fun `character choices are the characters with a lorebook, by name`() {
        assertEquals(listOf(bob, seraphina), filter.charactersWithLorebooks(all))
    }
}
