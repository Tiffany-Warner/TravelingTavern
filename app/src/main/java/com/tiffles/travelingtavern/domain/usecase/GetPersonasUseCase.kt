package com.tiffles.travelingtavern.domain.usecase

import com.tiffles.travelingtavern.domain.model.Persona
import com.tiffles.travelingtavern.domain.repository.PersonaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.Collator
import javax.inject.Inject

/**
 * Every persona in list order: the active one first, then by name as SillyTavern sorts its own
 * list (locale-aware, A to Z). Null until SillyTavern has reported them.
 */
class GetPersonasUseCase @Inject constructor(private val repository: PersonaRepository) {

    operator fun invoke(): Flow<List<Persona>?> = repository.personas.map { it?.let(::sorted) }

    fun sorted(personas: List<Persona>): List<Persona> {
        val collator = Collator.getInstance()
        return personas.sortedWith(
            compareBy<Persona> { !it.isActive }.thenComparator { a, b -> collator.compare(a.name, b.name) }
        )
    }
}
