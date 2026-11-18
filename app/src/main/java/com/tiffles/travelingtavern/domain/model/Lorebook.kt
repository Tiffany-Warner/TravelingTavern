package com.tiffles.travelingtavern.domain.model

import java.text.Normalizer
import java.util.Locale

/**
 * A SillyTavern lorebook (World Info). [name] is its ID. [entryCount] is null if it couldn't
 * be read. [characterIds] are the characters it's attached to (main or extra lorebook);
 * [personaNames] the personas it's attached to (an empty name = the current persona).
 * Links from single chats are not included (they live inside each chat file).
 */
data class Lorebook(
    val name: String,
    val entryCount: Int?,
    val isGlobal: Boolean,
    val characterIds: List<String>,
    val personaNames: List<String>
) {
    /** Not global and not attached to any character or persona. A chat may still use it. */
    val isUnused: Boolean get() = !isGlobal && characterIds.isEmpty() && personaNames.isEmpty()
}

/**
 * One lorebook entry, limited to what the app shows and edits. [uid] is null for an entry
 * that hasn't been saved yet. [keywords] trigger it; [title] is SillyTavern's "comment".
 */
data class LorebookEntry(
    val uid: Int?,
    val title: String,
    val keywords: List<String>,
    val content: String,
    val enabled: Boolean
)

/** What a lorebook card says about where it's used. */
sealed interface LorebookStatus {
    /** On for every chat. Wins over character links. */
    data object Global : LorebookStatus

    /** Attached to characters: [first] by name, plus [others] more. */
    data class Characters(val first: CharacterSummary, val others: Int) : LorebookStatus

    data object Persona : LorebookStatus
    data object NotActive : LorebookStatus
}

/** A lorebook with its attached characters resolved, ready for the list. */
data class LorebookCard(
    val lorebook: Lorebook,
    /** Attached characters that exist in the library, sorted by name. */
    val characters: List<CharacterSummary>
) {
    val status: LorebookStatus
        get() = when {
            lorebook.isGlobal -> LorebookStatus.Global
            characters.isNotEmpty() -> LorebookStatus.Characters(characters.first(), characters.size - 1)
            lorebook.personaNames.isNotEmpty() -> LorebookStatus.Persona
            else -> LorebookStatus.NotActive
        }
}

/** The single-select filter chips on the Lorebooks tab. */
sealed interface LorebookFilter {
    data object All : LorebookFilter
    data object Global : LorebookFilter
    data class Character(val characterId: String) : LorebookFilter
    data object Unused : LorebookFilter
}

/**
 * True if two lorebook names would clash: SillyTavern compares them ignoring case and
 * accents (`checkOverwriteExistingData`), and file names on Windows ignore case too.
 */
fun isSameLorebookName(a: String, b: String): Boolean = a.foldForCompare() == b.foldForCompare()

private val COMBINING_MARKS = Regex("\\p{Mn}+")

private fun String.foldForCompare(): String =
    Normalizer.normalize(trim(), Normalizer.Form.NFD).replace(COMBINING_MARKS, "").lowercase(Locale.ROOT)
