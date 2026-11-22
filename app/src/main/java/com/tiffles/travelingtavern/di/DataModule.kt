package com.tiffles.travelingtavern.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.tiffles.travelingtavern.data.bridge.LeaseFileReader
import com.tiffles.travelingtavern.data.bridge.ReleaseUploader
import com.tiffles.travelingtavern.data.bridge.ReplyServiceController
import com.tiffles.travelingtavern.data.media.ContentPictureEncoder
import com.tiffles.travelingtavern.data.media.PictureEncoder
import com.tiffles.travelingtavern.data.remote.BasicAuthInterceptor
import com.tiffles.travelingtavern.data.remote.OkHttpConnectionTester
import com.tiffles.travelingtavern.data.remote.OkHttpLeaseFileReader
import com.tiffles.travelingtavern.data.remote.OkHttpReleaseUploader
import com.tiffles.travelingtavern.data.repository.CharacterRepositoryImpl
import com.tiffles.travelingtavern.data.repository.LeaseRepositoryImpl
import com.tiffles.travelingtavern.data.repository.ChatSessionRepositoryImpl
import com.tiffles.travelingtavern.data.repository.LorebookRepositoryImpl
import com.tiffles.travelingtavern.data.repository.PersonaRepositoryImpl
import com.tiffles.travelingtavern.data.repository.ServerConfigRepositoryImpl
import com.tiffles.travelingtavern.data.settings.HiddenChatsStore
import com.tiffles.travelingtavern.data.service.AndroidReplyServiceController
import com.tiffles.travelingtavern.data.settings.settingsDataStore
import com.tiffles.travelingtavern.domain.repository.CharacterRepository
import com.tiffles.travelingtavern.domain.repository.ChatSessionRepository
import com.tiffles.travelingtavern.domain.repository.ConnectionTester
import com.tiffles.travelingtavern.domain.repository.HiddenChatsRepository
import com.tiffles.travelingtavern.domain.repository.LeaseRepository
import com.tiffles.travelingtavern.domain.repository.LorebookRepository
import com.tiffles.travelingtavern.domain.repository.PersonaRepository
import com.tiffles.travelingtavern.domain.repository.ServerConfigRepository
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
    abstract fun bindPersonaRepository(impl: PersonaRepositoryImpl): PersonaRepository

    @Binds
    abstract fun bindPictureEncoder(impl: ContentPictureEncoder): PictureEncoder

    @Binds
    abstract fun bindChatSessionRepository(impl: ChatSessionRepositoryImpl): ChatSessionRepository

    @Binds
    abstract fun bindHiddenChatsRepository(impl: HiddenChatsStore): HiddenChatsRepository

    @Binds
    abstract fun bindLeaseRepository(impl: LeaseRepositoryImpl): LeaseRepository

    @Binds
    abstract fun bindLeaseFileReader(impl: OkHttpLeaseFileReader): LeaseFileReader

    @Binds
    abstract fun bindReleaseUploader(impl: OkHttpReleaseUploader): ReleaseUploader

    @Binds
    abstract fun bindReplyServiceController(impl: AndroidReplyServiceController): ReplyServiceController

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
