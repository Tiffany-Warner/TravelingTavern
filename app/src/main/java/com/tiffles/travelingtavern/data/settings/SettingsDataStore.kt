package com.tiffles.travelingtavern.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

/** The app's single Preferences DataStore. */
val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Keys for everything stored in [settingsDataStore]. Credentials are stored as Tink ciphertext. */
object SettingsKeys {
    val SERVER_BASE_URL = stringPreferencesKey("server_base_url")
    val SERVER_USERNAME_ENCRYPTED = stringPreferencesKey("server_username_enc")
    val SERVER_PASSWORD_ENCRYPTED = stringPreferencesKey("server_password_enc")

    /** Chats hidden from the Chats tab (1.6c), as `avatar/chatFile` keys. */
    val HIDDEN_CHATS = stringSetPreferencesKey("hidden_chats")
}
