package com.tiffles.travelingtavern.di

import android.content.Context
import com.google.crypto.tink.Aead
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.integration.android.AndroidKeysetManager
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
     * a master key that never leaves the Android Keystore.
     */
    @Provides
    @Singleton
    fun provideCredentialAead(@ApplicationContext context: Context): Aead {
        AeadConfig.register()
        val keysetHandle = AndroidKeysetManager.Builder()
            .withSharedPref(context, KEYSET_NAME, KEYSET_PREFS)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM"))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle
        return keysetHandle.getPrimitive(RegistryConfiguration.get(), Aead::class.java)
    }

    private const val KEYSET_NAME = "tt_credentials_keyset"
    private const val KEYSET_PREFS = "tt_credentials_keyset_prefs"
    private const val MASTER_KEY_URI = "android-keystore://tt_credentials_master_key"
}
