package com.tiffles.travelingtavern.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Whether the one-time "Keep your devices from overwriting each other" dialog (A8) has been shown. */
interface ExtensionPromptFlag {
    val shown: Flow<Boolean>

    suspend fun markShown()
}

/** [ExtensionPromptFlag] in the settings DataStore. Shown once ever: nothing clears it. */
@Singleton
class ExtensionPromptStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : ExtensionPromptFlag {

    override val shown: Flow<Boolean> = dataStore.data
        .map { it[SettingsKeys.EXTENSION_PROMPT_SHOWN] == true }
        .distinctUntilChanged()

    override suspend fun markShown() {
        dataStore.edit { it[SettingsKeys.EXTENSION_PROMPT_SHOWN] = true }
    }
}
