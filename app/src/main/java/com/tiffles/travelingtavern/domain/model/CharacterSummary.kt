package com.tiffles.travelingtavern.domain.model

import java.time.Instant

/**
 * One character in the user's SillyTavern library. [id] is SillyTavern's avatar file name
 * (e.g. `Seraphina.png`), which is stable across sessions. [avatarUrl] is the small
 * thumbnail (list rows); [portraitUrl] is the full-size image (grid cards).
 */
data class CharacterSummary(
    val id: String,
    val name: String,
    val avatarUrl: String,
    val portraitUrl: String,
    val lastChatAt: Instant?
)
