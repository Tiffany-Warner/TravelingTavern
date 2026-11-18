package com.tiffles.travelingtavern.di

import android.content.Context
import android.util.Log
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.tiffles.travelingtavern.data.settings.loadKeysetOrReset
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SecurityModule {

    /**
     * AES-GCM AEAD for stored credentials. The keyset lives in SharedPreferences, wrapped by
     * a master key that never leaves the Android Keystore. The keyset file is excluded from
     * backups (see `res/xml/data_extraction_rules.xml`); if it still can't be unlocked, a new
     * keyset replaces it instead of crashing the app.
     */
    @Provides
    @Singleton
    fun provideCredentialAead(@ApplicationContext context: Context): Aead {
        AeadConfig.register()
        val keysetHandle = loadKeysetOrReset(
            load = { loadKeyset(context) },
            reset = { context.deleteSharedPreferences(KEYSET_PREFS) },
            onReset = { e -> Log.w(TAG, "Credential keyset can't be unlocked; creating a new one", e) },
        )
        return keysetHandle.getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }

    private fun loadKeyset(context: Context): KeysetHandle =
        AndroidKeysetManager.Builder()
            .withSharedPref(context, KEYSET_NAME, KEYSET_PREFS)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle

    private const val TAG = "SecurityModule"
    private const val KEYSET_NAME = "tt_credentials_keyset"
    private const val KEYSET_PREFS = "tt_credentials_keyset_prefs"
    private const val MASTER_KEY_URI = "android-keystore://tt_credentials_master_key"
}
