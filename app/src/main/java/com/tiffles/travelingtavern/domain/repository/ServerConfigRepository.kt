package com.tiffles.travelingtavern.domain.repository

import com.tiffles.travelingtavern.domain.model.ServerConfig
import kotlinx.coroutines.flow.Flow

interface ServerConfigRepository {
    /** Emits the saved server, or null when none is configured. */
    fun observe(): Flow<ServerConfig?>

    /** Saves the server. A null [password] clears any stored password. */
    suspend fun save(config: ServerConfig, password: String?)

    suspend fun clear()
}
