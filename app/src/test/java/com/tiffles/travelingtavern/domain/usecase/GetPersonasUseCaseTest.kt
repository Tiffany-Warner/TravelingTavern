package com.tiffles.travelingtavern.domain.usecase

import com.tiffles.travelingtavern.domain.FakePersonaRepository
import com.tiffles.travelingtavern.domain.persona
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GetPersonasUseCaseTest {

    private val repository = FakePersonaRepository()
    private val useCase = GetPersonasUseCase(repository)

    @Test
    fun `null until reported`() = runTest {
        assertNull(useCase().first())
    }

    @Test
    fun `active first, then by name ignoring case`() = runTest {
        repository.personas.value = listOf(
            persona("c.png", name = "zed"),
            persona("a.png", name = "Mira", isActive = true),
            persona("b.png", name = "anna"),
            persona("d.png", name = "Bram")
        )

        assertEquals(listOf("Mira", "anna", "Bram", "zed"), useCase().first()!!.map { it.name })
    }
}
