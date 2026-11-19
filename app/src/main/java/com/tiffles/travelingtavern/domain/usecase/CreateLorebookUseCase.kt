package com.tiffles.travelingtavern.domain.usecase

import com.tiffles.travelingtavern.domain.model.isSameLorebookName
import com.tiffles.travelingtavern.domain.repository.LorebookRepository
import javax.inject.Inject

/** Whether a name can be used for a new lorebook. */
enum class LorebookNameCheck { Ok, Empty, Taken }

/** Creates an empty lorebook after checking the name the way SillyTavern does. */
class CreateLorebookUseCase @Inject constructor(private val repository: LorebookRepository) {

    /** [existing] are the current lorebook names. */
    fun check(name: String, existing: List<String>): LorebookNameCheck = when {
        name.isBlank() -> LorebookNameCheck.Empty
        existing.any { isSameLorebookName(it, name) } -> LorebookNameCheck.Taken
        else -> LorebookNameCheck.Ok
    }

    /** Returns the name SillyTavern saved it under. */
    suspend operator fun invoke(name: String): Result<String> = repository.create(name.trim())
}
