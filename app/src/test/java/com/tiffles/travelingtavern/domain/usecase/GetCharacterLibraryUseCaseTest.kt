package com.tiffles.travelingtavern.domain.usecase

import com.tiffles.travelingtavern.domain.FakeCharacterRepository
import com.tiffles.travelingtavern.domain.character
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GetCharacterLibraryUseCaseTest {

    private val repository = FakeCharacterRepository()
    private val useCase = GetCharacterLibraryUseCase(repository)

    private suspend fun names(query: String) = useCase(flowOf(query)).first()!!.map { it.name }

    @Test
    fun `null until the library has arrived`() = runTest {
        assertNull(useCase(flowOf("")).first())
    }

    @Test
    fun `sorted by name, ignoring case`() = runTest {
        repository.characters.value = listOf(character("tide Pods"), character("Bob"), character("alice"))

        assertEquals(listOf("alice", "Bob", "tide Pods"), names(""))
    }

    @Test
    fun `query matches anywhere in the name, ignoring case and surrounding spaces`() = runTest {
        repository.characters.value = listOf(character("Seraphina"), character("Tide Pods"), character("Bob"))

        assertEquals(listOf("Seraphina"), names("APH"))
        assertEquals(listOf("Tide Pods"), names("  pods "))
        assertEquals(emptyList<String>(), names("zed"))
    }

    @Test
    fun `blank query shows everything`() = runTest {
        repository.characters.value = listOf(character("Bob"), character("Amy"))

        assertEquals(listOf("Amy", "Bob"), names("   "))
    }
}
