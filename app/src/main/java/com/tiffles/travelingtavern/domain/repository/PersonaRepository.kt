package com.tiffles.travelingtavern.domain.repository

import com.tiffles.travelingtavern.domain.model.PageState
import com.tiffles.travelingtavern.domain.model.Persona
import com.tiffles.travelingtavern.domain.model.PersonaChanges
import kotlinx.coroutines.flow.Flow

/** Why a persona action failed. */
enum class PersonaFailure {
    /** The persona no longer exists. */
    NotFound,

    /** The picked picture couldn't be read. */
    PictureUnreadable,

    /** SillyTavern reported an error, or didn't answer in time. */
    Failed
}

/** Carries a [PersonaFailure] inside a [Result]. */
class PersonaException(val failure: PersonaFailure) : Exception(failure.name)

/**
 * The user's personas as SillyTavern has them. Every read and write runs SillyTavern's own
 * persona code through the JS bridge.
 */
interface PersonaRepository {

    /** Every persona (unsorted), or null until SillyTavern has reported them this session. */
    val personas: Flow<List<Persona>?>

    /** Whether the SillyTavern page is loading, ready or failed. */
    val pageState: Flow<PageState>

    /** Asks for the list again and reloads every picture. Returns once done, or after a timeout. */
    suspend fun refresh()

    /** Reloads the SillyTavern page after an error. */
    fun retry()

    /**
     * Makes [avatarId] the active persona. With [lockToChat] it is also locked to the open chat;
     * without, the open chat's lock is left alone.
     */
    suspend fun select(avatarId: String, lockToChat: Boolean): Result<Unit>

    /** One persona with its full description. */
    suspend fun details(avatarId: String): Result<Persona>

    /** Creates a persona (name required) without selecting it; returns its avatar ID. */
    suspend fun create(changes: PersonaChanges): Result<String>

    /** Writes the non-null [changes]; every other field stays as SillyTavern has it. */
    suspend fun save(avatarId: String, changes: PersonaChanges): Result<Unit>

    suspend fun delete(avatarId: String): Result<Unit>

    /** Makes [avatarId] the default persona for new chats, or ([isDefault] false) removes that. */
    suspend fun setDefault(avatarId: String, isDefault: Boolean): Result<Unit>

    /** Tokens in [text] with SillyTavern's current tokenizer, or null if it didn't answer. */
    suspend fun countTokens(text: String): Int?
}
