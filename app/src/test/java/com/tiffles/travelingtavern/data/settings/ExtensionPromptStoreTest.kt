package com.tiffles.travelingtavern.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A8: the one-time extension dialog's flag. */
class ExtensionPromptStoreTest {

    /** File-backed DataStore can't overwrite its file on a Windows JVM, so the test keeps it in memory. */
    private class InMemoryDataStore : DataStore<Preferences> {
        private val state = MutableStateFlow(emptyPreferences())
        override val data: Flow<Preferences> = state

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            transform(state.value).also { state.value = it }
    }

    @Test
    fun `not shown until marked, then shown for good`() = runTest {
        val store = ExtensionPromptStore(InMemoryDataStore())
        assertFalse(store.shown.first())

        store.markShown()
        assertTrue(store.shown.first())

        store.markShown()
        assertTrue(store.shown.first())
    }
}
