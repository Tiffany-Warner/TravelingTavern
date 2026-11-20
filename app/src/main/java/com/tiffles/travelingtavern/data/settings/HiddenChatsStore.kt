package com.tiffles.travelingtavern.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.tiffles.travelingtavern.domain.model.ChatRef
import com.tiffles.travelingtavern.domain.repository.HiddenChatsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [HiddenChatsRepository] in the settings DataStore: a string set of `avatar/chatFile` keys.
 * SillyTavern's avatar file names never contain `/`, so the first `/` splits the key.
 */
@Singleton
class HiddenChatsStore @Inject constructor(
    private val dataStore: DataStore<Preferences>
) : HiddenChatsRepository {

    override val hiddenChats: Flow<Set<ChatRef>> = dataStore.data
        .map { prefs -> prefs[SettingsKeys.HIDDEN_CHATS].orEmpty().mapNotNull(::decode).toSet() }
        .distinctUntilChanged()

    override suspend fun hide(chat: ChatRef) = update { it + encode(chat) }

    override suspend fun unhide(chat: ChatRef) = update { it - encode(chat) }

    override suspend fun showAll() {
        dataStore.edit { it.remove(SettingsKeys.HIDDEN_CHATS) }
    }

    private suspend fun update(change: (Set<String>) -> Set<String>) {
        dataStore.edit { prefs -> prefs[SettingsKeys.HIDDEN_CHATS] = change(prefs[SettingsKeys.HIDDEN_CHATS].orEmpty()) }
    }

    internal companion object {
        fun encode(chat: ChatRef): String = chat.characterId + SEPARATOR + chat.chatFile

        fun decode(key: String): ChatRef? {
            val split = key.indexOf(SEPARATOR)
            if (split <= 0 || split == key.lastIndex) return null
            return ChatRef(characterId = key.substring(0, split), chatFile = key.substring(split + 1))
        }

        private const val SEPARATOR = '/'
    }
}
