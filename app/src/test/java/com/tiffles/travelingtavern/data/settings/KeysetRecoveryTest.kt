package com.tiffles.travelingtavern.data.settings

import java.security.InvalidKeyException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class KeysetRecoveryTest {

    @Test
    fun `keyset that loads is used without a reset`() {
        var resets = 0
        val result = loadKeysetOrReset(load = { "keyset" }, reset = { resets++ }, onReset = {})

        assertEquals("keyset", result)
        assertEquals(0, resets)
    }

    @Test
    fun `keyset that can't be unlocked is reset and loaded again`() {
        val stored = mutableListOf("restored keyset")
        val reported = mutableListOf<Exception>()
        val load = {
            if (stored.isNotEmpty()) throw InvalidKeyException("Keystore cannot load the key")
            "new keyset"
        }

        val result = loadKeysetOrReset(load = load, reset = { stored.clear() }, onReset = { reported += it })

        assertEquals("new keyset", result)
        assertTrue(stored.isEmpty())
        assertEquals(1, reported.size)
        assertTrue(reported.single() is InvalidKeyException)
    }

    @Test
    fun `failure after the reset is thrown`() {
        var resets = 0

        assertThrows(InvalidKeyException::class.java) {
            loadKeysetOrReset<String>(load = { throw InvalidKeyException("still broken") }, reset = { resets++ }, onReset = {})
        }
        assertEquals(1, resets)
    }

    @Test
    fun `unrelated errors are not treated as a lost key`() {
        var resets = 0

        assertThrows(IllegalStateException::class.java) {
            loadKeysetOrReset<String>(load = { throw IllegalStateException("bug") }, reset = { resets++ }, onReset = {})
        }
        assertEquals(0, resets)
    }
}
