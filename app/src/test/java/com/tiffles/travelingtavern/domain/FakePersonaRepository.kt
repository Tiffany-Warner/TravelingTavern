package com.tiffles.travelingtavern.domain

import com.tiffles.travelingtavern.domain.model.PageState
import com.tiffles.travelingtavern.domain.model.Persona
import com.tiffles.travelingtavern.domain.model.PersonaChanges
import com.tiffles.travelingtavern.domain.repository.PersonaException
import com.tiffles.travelingtavern.domain.repository.PersonaFailure
import com.tiffles.travelingtavern.domain.repository.PersonaRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [PersonaRepository] for tests. Set the flows and results directly. */
class FakePersonaRepository : PersonaRepository {
    override val personas = MutableStateFlow<List<Persona>?>(null)
    override val pageState = MutableStateFlow<PageState>(PageState.Ready)

    /** When set, every action fails with this. */
    var failure: PersonaFailure? = null

    /** When set, actions suspend until it completes. */
    var actionGate: CompletableDeferred<Unit>? = null

    var refreshCalls = 0
    var retryCalls = 0
    val selected = mutableListOf<Pair<String, Boolean>>()
    val created = mutableListOf<PersonaChanges>()
    val saved = mutableListOf<Pair<String, PersonaChanges>>()
    val deleted = mutableListOf<String>()
    val defaults = mutableListOf<Pair<String, Boolean>>()
    val countedTexts = mutableListOf<String>()
    var tokenCount: Int? = 7

    override suspend fun refresh() {
        refreshCalls++
    }

    override fun retry() {
        retryCalls++
    }

    override suspend fun select(avatarId: String, lockToChat: Boolean): Result<Unit> = act { selected += avatarId to lockToChat }

    override suspend fun details(avatarId: String): Result<Persona> {
        failure?.let { return fail(it) }
        return personas.value?.firstOrNull { it.avatarId == avatarId }?.let { Result.success(it) }
            ?: fail(PersonaFailure.NotFound)
    }

    override suspend fun create(changes: PersonaChanges): Result<String> = act { created += changes; NEW_ID }

    override suspend fun save(avatarId: String, changes: PersonaChanges): Result<Unit> = act { saved += avatarId to changes }

    override suspend fun delete(avatarId: String): Result<Unit> = act { deleted += avatarId }

    override suspend fun setDefault(avatarId: String, isDefault: Boolean): Result<Unit> = act { defaults += avatarId to isDefault }

    override suspend fun countTokens(text: String): Int? {
        countedTexts += text
        return tokenCount
    }

    private suspend fun <T> act(block: () -> T): Result<T> {
        actionGate?.await()
        failure?.let { return fail(it) }
        return Result.success(block())
    }

    private fun <T> fail(reason: PersonaFailure): Result<T> = Result.failure(PersonaException(reason))

    companion object {
        const val NEW_ID = "123-New.png"
    }
}

fun persona(
    avatarId: String,
    name: String = avatarId.substringBefore('.'),
    title: String = "",
    description: String = "",
    isActive: Boolean = false,
    isDefault: Boolean = false
) = Persona(avatarId, name, title, description, "http://192.0.2.1:8000/thumbnail?file=$avatarId", isActive, isDefault)
