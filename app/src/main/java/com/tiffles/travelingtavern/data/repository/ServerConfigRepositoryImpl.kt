package com.tiffles.travelingtavern.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.tiffles.travelingtavern.data.settings.CredentialCipher
import com.tiffles.travelingtavern.data.settings.ServerCredentials
import com.tiffles.travelingtavern.data.settings.SettingsKeys
import com.tiffles.travelingtavern.domain.model.ServerConfig
import com.tiffles.travelingtavern.domain.repository.ServerConfigRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServerConfigRepositoryImpl @Inject constructor(
    private val dataStore: DataStore<Preferences>,
    private val cipher: CredentialCipher
) : ServerConfigRepository {

    override fun observe(): Flow<ServerConfig?> = dataStore.data.map { prefs ->
        val baseUrl = prefs[SettingsKeys.SERVER_BASE_URL] ?: return@map null
        ServerConfig(
            baseUrl = baseUrl,
            username = prefs[SettingsKeys.SERVER_USERNAME_ENCRYPTED]?.let(cipher::decrypt),
            hasPassword = prefs[SettingsKeys.SERVER_PASSWORD_ENCRYPTED] != null
        )
    }

    override suspend fun save(config: ServerConfig, password: String?) {
        dataStore.edit { prefs ->
            prefs[SettingsKeys.SERVER_BASE_URL] = config.baseUrl
            prefs.putEncrypted(SettingsKeys.SERVER_USERNAME_ENCRYPTED, config.username)
            prefs.putEncrypted(SettingsKeys.SERVER_PASSWORD_ENCRYPTED, password)
        }
    }

    override suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(SettingsKeys.SERVER_BASE_URL)
            prefs.remove(SettingsKeys.SERVER_USERNAME_ENCRYPTED)
            prefs.remove(SettingsKeys.SERVER_PASSWORD_ENCRYPTED)
        }
    }

    /**
     * The saved login, decrypted, for the network layer (basic-auth interceptor, WebView).
     * Null when no server is saved or no username/password is stored.
     */
    suspend fun credentials(): ServerCredentials? {
        val prefs = dataStore.data.first()
        val username = prefs[SettingsKeys.SERVER_USERNAME_ENCRYPTED]?.let(cipher::decrypt) ?: return null
        val password = prefs[SettingsKeys.SERVER_PASSWORD_ENCRYPTED]?.let(cipher::decrypt) ?: return null
        return ServerCredentials(username, password)
    }

    /** The saved base URL, for host matching in the network layer. */
    suspend fun baseUrl(): String? = dataStore.data.first()[SettingsKeys.SERVER_BASE_URL]

    private fun MutablePreferences.putEncrypted(key: Preferences.Key<String>, value: String?) {
        if (value.isNullOrEmpty()) remove(key) else this[key] = cipher.encrypt(value)
    }
}
