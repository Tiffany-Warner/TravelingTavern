package com.tiffles.travelingtavern.domain.model

import java.time.Instant

/**
 * One character in the user's SillyTavern library. [id] is SillyTavern's avatar file name
 * (e.g. `Seraphina.png`), which is stable across sessions.
 */
data class CharacterSummary(
    val id: String,
    val name: String,
    val avatarUrl: String,
    val lastChatAt: Instant?
)
