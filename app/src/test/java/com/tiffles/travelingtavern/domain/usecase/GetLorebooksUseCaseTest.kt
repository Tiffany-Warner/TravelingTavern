package com.tiffles.travelingtavern.domain.usecase

import com.tiffles.travelingtavern.domain.FakeCharacterRepository
import com.tiffles.travelingtavern.domain.FakeLorebookRepository
import com.tiffles.travelingtavern.domain.character
import com.tiffles.travelingtavern.domain.lorebook
import com.tiffles.travelingtavern.domain.model.LorebookStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GetLorebooksUseCaseTest {

    private val lorebooks = FakeLorebookRepository()
    private val characters = FakeCharacterRepository()
    private val getLorebooks = GetLorebooksUseCase(lorebooks, characters)

    @Test
    fun `null until both lists are known`() = runTest {
        lorebooks.lorebooks.value = listOf(lorebook("Eldoria"))
        assertNull(getLorebooks().first())

        lorebooks.lorebooks.value = null
        characters.characters.value = emptyList()
        assertNull(getLorebooks().first())
    }

    @Test
    fun `sorted by lorebook name`() = runTest {
        lorebooks.lorebooks.value = listOf(lorebook("zeta"), lorebook("Alpha"), lorebook("beta"))
        characters.characters.value = emptyList()

        assertEquals(listOf("Alpha", "beta", "zeta"), getLorebooks().first()!!.map { it.lorebook.name })
    }

    @Test
    fun `attached characters are resolved by name and unknown IDs dropped`() = runTest {
        val seraphina = character("Seraphina")
        val bob = character("Bob")
        characters.characters.value = listOf(seraphina, bob)
        lorebooks.lorebooks.value = listOf(lorebook("Eldoria", characterIds = listOf(seraphina.id, "gone.png", bob.id)))

        val card = getLorebooks().first()!!.single()

        assertEquals(listOf(bob, seraphina), card.characters)
        assertEquals(LorebookStatus.Characters(first = bob, others = 1), card.status)
    }

    @Test
    fun `status - global wins, then characters, then persona, else not active`() = runTest {
        val bob = character("Bob")
        characters.characters.value = listOf(bob)
        lorebooks.lorebooks.value = listOf(
            lorebook("A", global = true, characterIds = listOf(bob.id)),
            lorebook("B", characterIds = listOf(bob.id), personaNames = listOf("Me")),
            lorebook("C", personaNames = listOf("Me")),
            lorebook("D")
        )

        val statuses = getLorebooks().first()!!.map { it.status }

        assertEquals(
            listOf(LorebookStatus.Global, LorebookStatus.Characters(bob, 0), LorebookStatus.Persona, LorebookStatus.NotActive),
            statuses
        )
    }
}
