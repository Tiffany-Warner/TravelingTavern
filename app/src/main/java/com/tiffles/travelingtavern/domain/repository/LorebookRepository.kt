package com.tiffles.travelingtavern.domain.repository

import com.tiffles.travelingtavern.domain.model.Lorebook
import com.tiffles.travelingtavern.domain.model.LorebookEntry
import com.tiffles.travelingtavern.domain.model.PageState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow

/** Why a lorebook action failed. */
enum class LorebookFailure {
    /** The lorebook or entry no longer exists. */
    NotFound,

    /** A lorebook with that name (ignoring case and accents) already exists. */
    NameTaken,

    /** SillyTavern reported an error, or didn't answer in time. */
    Failed
}

/** Carries a [LorebookFailure] inside a [Result]. */
class LorebookException(val failure: LorebookFailure) : Exception(failure.name)

/**
 * The user's lorebooks (World Info) as SillyTavern reports them. Every read and write runs
 * SillyTavern's own World Info code through the JS bridge.
 */
interface LorebookRepository {

    /** Every lorebook, or null until SillyTavern has reported them this session. */
    val lorebooks: Flow<List<Lorebook>?>

    /** Whether the SillyTavern page is loading, ready or failed. */
    val pageState: Flow<PageState>

    /** Emits a lorebook's name after one of its entries was saved or deleted. */
    val entriesChanged: SharedFlow<String>

    /** Re-reads the list and every lorebook from the server. Returns once done, or after a timeout. */
    suspend fun refresh()

    /** Reloads the SillyTavern page after an error. */
    fun retry()

    /** The entries of [name], read fresh from the server, in SillyTavern's display order. */
    suspend fun entries(name: String): Result<List<LorebookEntry>>

    /** Creates an empty lorebook; returns the name SillyTavern saved it under. */
    suspend fun create(name: String): Result<String>

    /** Deletes a lorebook the way SillyTavern's own Delete button does. */
    suspend fun delete(name: String): Result<Unit>

    /** Turns [name] on or off for every chat. */
    suspend fun setGlobal(name: String, enabled: Boolean): Result<Unit>

    /**
     * Saves [entry] into [lorebook] (a new one if its uid is null); returns its uid. Fields
     * the app doesn't edit stay as SillyTavern has them.
     */
    suspend fun saveEntry(lorebook: String, entry: LorebookEntry): Result<Int>

    suspend fun deleteEntry(lorebook: String, uid: Int): Result<Unit>

    /** Tokens in [text] with SillyTavern's current tokenizer, or null if it didn't answer. */
    suspend fun countTokens(text: String): Int?
}
