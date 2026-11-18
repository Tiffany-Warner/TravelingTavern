package com.tiffles.travelingtavern.domain

import com.tiffles.travelingtavern.domain.model.Lorebook
import com.tiffles.travelingtavern.domain.model.LorebookEntry
import com.tiffles.travelingtavern.domain.model.PageState
import com.tiffles.travelingtavern.domain.repository.LorebookException
import com.tiffles.travelingtavern.domain.repository.LorebookFailure
import com.tiffles.travelingtavern.domain.repository.LorebookRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

/** In-memory [LorebookRepository] for tests. Set the flows and results directly. */
class FakeLorebookRepository : LorebookRepository {
    override val lorebooks = MutableStateFlow<List<Lorebook>?>(null)
    override val pageState = MutableStateFlow<PageState>(PageState.Ready)
    override val entriesChanged = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** Entries per lorebook; a missing name answers NotFound. */
    val entriesByName = mutableMapOf<String, List<LorebookEntry>>()

    /** When set, every action fails with this. */
    var failure: LorebookFailure? = null

    /** When set, actions suspend until it completes. */
    var actionGate: CompletableDeferred<Unit>? = null

    var refreshCalls = 0
    var retryCalls = 0
    var entriesCalls = 0
    val created = mutableListOf<String>()
    val deleted = mutableListOf<String>()
    val globalChanges = mutableListOf<Pair<String, Boolean>>()
    val savedEntries = mutableListOf<Pair<String, LorebookEntry>>()
    val deletedEntries = mutableListOf<Pair<String, Int>>()
    val countedTexts = mutableListOf<String>()
    var tokenCount: Int? = 7

    override suspend fun refresh() {
        refreshCalls++
    }

    override fun retry() {
        retryCalls++
    }

    override suspend fun entries(name: String): Result<List<LorebookEntry>> {
        entriesCalls++
        failure?.let { return fail(it) }
        return entriesByName[name]?.let { Result.success(it) } ?: fail(LorebookFailure.NotFound)
    }

    override suspend fun create(name: String): Result<String> = act { created += name; name }

    override suspend fun delete(name: String): Result<Unit> = act { deleted += name }

    override suspend fun setGlobal(name: String, enabled: Boolean): Result<Unit> =
        act { globalChanges += name to enabled }

    override suspend fun saveEntry(lorebook: String, entry: LorebookEntry): Result<Int> =
        act { savedEntries += lorebook to entry; entry.uid ?: NEW_UID }

    override suspend fun deleteEntry(lorebook: String, uid: Int): Result<Unit> = act { deletedEntries += lorebook to uid }

    override suspend fun countTokens(text: String): Int? {
        countedTexts += text
        return tokenCount
    }

    private suspend fun <T> act(block: () -> T): Result<T> {
        actionGate?.await()
        failure?.let { return fail(it) }
        return Result.success(block())
    }

    private fun <T> fail(reason: LorebookFailure): Result<T> = Result.failure(LorebookException(reason))

    companion object {
        const val NEW_UID = 99
    }
}

fun lorebook(
    name: String,
    global: Boolean = false,
    characterIds: List<String> = emptyList(),
    personaNames: List<String> = emptyList(),
    entryCount: Int? = 3
) = Lorebook(name, entryCount, global, characterIds, personaNames)

fun entry(uid: Int?, title: String = "Entry $uid", keywords: List<String> = listOf("k"), content: String = "c", enabled: Boolean = true) =
    LorebookEntry(uid, title, keywords, content, enabled)
