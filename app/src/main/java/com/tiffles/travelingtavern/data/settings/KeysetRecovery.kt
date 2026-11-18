package com.tiffles.travelingtavern.data.settings

import java.io.IOException
import java.security.GeneralSecurityException
import java.security.ProviderException

/**
 * Loads the credential keyset with [load]. If it can't be unlocked — typically because Android
 * restored the keyset file from a backup but the Keystore master key that wraps it was deleted
 * with the old install (Keystore keys are never backed up) — calls [onReset] with the error,
 * throws the old keyset away with [reset] and loads again, which creates a new keyset.
 *
 * Credentials encrypted with the old keyset can't be decrypted afterwards and read as empty
 * (see [CredentialCipher.decrypt]). Errors other than key or storage failures are rethrown, and
 * so is a failure of the second attempt.
 */
fun <T> loadKeysetOrReset(
    load: () -> T,
    reset: () -> Unit,
    onReset: (Exception) -> Unit,
): T = try {
    load()
} catch (e: Exception) {
    if (!e.isKeysetFailure()) throw e
    onReset(e)
    reset()
    load()
}

private fun Exception.isKeysetFailure(): Boolean =
    this is GeneralSecurityException || this is IOException || this is ProviderException
