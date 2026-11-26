package com.tiffles.travelingtavern.domain.repository

import com.tiffles.travelingtavern.domain.model.ExtensionOutcome

/** Installs and updates the desktop extension in SillyTavern through the page (A8). */
interface ExtensionRepository {

    /** Installs it for this SillyTavern user from its default branch; reloads the page on success. */
    suspend fun install(): ExtensionOutcome

    /** Pulls its current branch; reloads the page when something was pulled. */
    suspend fun update(): ExtensionOutcome
}
