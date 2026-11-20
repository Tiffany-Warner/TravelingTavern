package com.tiffles.travelingtavern.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import com.tiffles.travelingtavern.domain.model.ChatRef
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HiddenChatsStoreTest {

    /** File-backed DataStore can't overwrite its file on a Windows JVM, so the tests keep it in memory. */
    private class InMemoryDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }

    private fun store() = HiddenChatsStore(InMemoryDataStore())

    private val bob = ChatRef("Bob.png", "Bob - 2026-11-19@10h00m00s")
    private val oliver = ChatRef("Oliver.png", "Oliver - 2026-11-18@09h00m00s")

    @Test
    fun `hide, unhide and show all round trip`() = runTest {
        val store = store()
        assertEquals(emptySet<ChatRef>(), store.hiddenChats.first())

        store.hide(bob)
        store.hide(oliver)
        assertEquals(setOf(bob, oliver), store.hiddenChats.first())

        store.unhide(bob)
        assertEquals(setOf(oliver), store.hiddenChats.first())

        store.showAll()
        assertEquals(emptySet<ChatRef>(), store.hiddenChats.first())
    }

    @Test
    fun `keys split at the first slash, so chat names may contain one`() {
        val chat = ChatRef("Bob.png", "Bob / side story")
        assertEquals(chat, HiddenChatsStore.decode(HiddenChatsStore.encode(chat)))
    }

    @Test
    fun `malformed keys are dropped`() {
        assertNull(HiddenChatsStore.decode("no separator"))
        assertNull(HiddenChatsStore.decode("/Bob - 1"))
        assertNull(HiddenChatsStore.decode("Bob.png/"))
    }
}
