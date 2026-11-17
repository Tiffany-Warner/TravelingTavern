package com.tiffles.travelingtavern.data.settings

import android.util.Base64
import com.google.crypto.tink.Aead
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypts credentials with a Tink AEAD whose keyset is wrapped by an Android Keystore
 * master key (see [com.tiffles.travelingtavern.di.SecurityModule]). Output is Base64 so it
 * can live in Preferences DataStore.
 */
@Singleton
class CredentialCipher @Inject constructor(private val aead: Aead) {

    fun encrypt(plaintext: String): String {
        val bytes = aead.encrypt(plaintext.toByteArray(Charsets.UTF_8), ASSOCIATED_DATA)
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /** Returns null if the ciphertext can't be decrypted (e.g. the keystore key was lost). */
    fun decrypt(ciphertext: String): String? = runCatching {
        val bytes = Base64.decode(ciphertext, Base64.NO_WRAP)
        String(aead.decrypt(bytes, ASSOCIATED_DATA), Charsets.UTF_8)
    }.getOrNull()

    private companion object {
        val ASSOCIATED_DATA: ByteArray = "tt-server-credentials".toByteArray(Charsets.UTF_8)
    }
}
