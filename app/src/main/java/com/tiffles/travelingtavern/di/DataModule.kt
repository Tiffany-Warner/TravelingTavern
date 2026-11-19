package com.tiffles.travelingtavern.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.tiffles.travelingtavern.data.remote.BasicAuthInterceptor
import com.tiffles.travelingtavern.data.remote.OkHttpConnectionTester
import com.tiffles.travelingtavern.data.repository.CharacterRepositoryImpl
import com.tiffles.travelingtavern.data.repository.ChatInputRepositoryImpl
import com.tiffles.travelingtavern.data.repository.LorebookRepositoryImpl
import com.tiffles.travelingtavern.data.repository.ServerConfigRepositoryImpl
import com.tiffles.travelingtavern.data.settings.settingsDataStore
import com.tiffles.travelingtavern.data.speech.AndroidMicrophonePermission
import com.tiffles.travelingtavern.data.speech.OnDeviceSpeechInput
import com.tiffles.travelingtavern.domain.repository.CharacterRepository
import com.tiffles.travelingtavern.domain.repository.ChatInputRepository
import com.tiffles.travelingtavern.domain.repository.ConnectionTester
import com.tiffles.travelingtavern.domain.repository.LorebookRepository
import com.tiffles.travelingtavern.domain.repository.MicrophonePermission
import com.tiffles.travelingtavern.domain.repository.ServerConfigRepository
import com.tiffles.travelingtavern.domain.repository.SpeechInputRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    abstract fun bindServerConfigRepository(impl: ServerConfigRepositoryImpl): ServerConfigRepository

    @Binds
    abstract fun bindConnectionTester(impl: OkHttpConnectionTester): ConnectionTester

    @Binds
    abstract fun bindCharacterRepository(impl: CharacterRepositoryImpl): CharacterRepository

    @Binds
    abstract fun bindLorebookRepository(impl: LorebookRepositoryImpl): LorebookRepository

    @Binds
    abstract fun bindSpeechInputRepository(impl: OnDeviceSpeechInput): SpeechInputRepository

    @Binds
    abstract fun bindMicrophonePermission(impl: AndroidMicrophonePermission): MicrophonePermission

    @Binds
    abstract fun bindChatInputRepository(impl: ChatInputRepositoryImpl): ChatInputRepository

    companion object {
        @Provides
        @Singleton
        fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            context.settingsDataStore

        /** The one OkHttp client for the app. Coil and the bridge reuse it so auth applies everywhere. */
        @Provides
        @Singleton
        fun provideOkHttpClient(authInterceptor: BasicAuthInterceptor): OkHttpClient =
            OkHttpClient.Builder()
                .addInterceptor(authInterceptor)
                .build()
    }
}
