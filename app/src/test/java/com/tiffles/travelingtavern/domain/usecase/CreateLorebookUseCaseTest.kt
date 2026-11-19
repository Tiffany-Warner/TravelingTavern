package com.tiffles.travelingtavern.domain.usecase

import com.tiffles.travelingtavern.domain.FakeLorebookRepository
import com.tiffles.travelingtavern.domain.entry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class CreateLorebookUseCaseTest {

    private val repository = FakeLorebookRepository()
    private val create = CreateLorebookUseCase(repository)

    @Test
    fun `blank names are Empty`() {
        assertEquals(LorebookNameCheck.Empty, create.check("", listOf("Eldoria")))
        assertEquals(LorebookNameCheck.Empty, create.check("   ", listOf("Eldoria")))
    }

    @Test
    fun `a name matching an existing one ignoring case, accents and spaces is Taken`() {
        val existing = listOf("Eldoria", "Café notes")
        assertEquals(LorebookNameCheck.Taken, create.check("eldoria", existing))
        assertEquals(LorebookNameCheck.Taken, create.check(" ELDORIA ", existing))
        assertEquals(LorebookNameCheck.Taken, create.check("cafe NOTES", existing))
    }

    @Test
    fun `a new name is Ok`() {
        assertEquals(LorebookNameCheck.Ok, create.check("Eldoria 2", listOf("Eldoria")))
    }

    @Test
    fun `creates with the trimmed name`() = runTest {
        create("  Kingdoms  ")
        assertEquals(listOf("Kingdoms"), repository.created)
    }

    @Test
    fun `saving an entry trims keywords and drops blanks`() = runTest {
        SaveLorebookEntryUseCase(repository)("Eldoria", entry(uid = 1, keywords = listOf(" wood ", "", "  ", "forest")))

        assertEquals(listOf("wood", "forest"), repository.savedEntries.single().second.keywords)
    }
}
