package com.tiffles.travelingtavern.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.tiffles.travelingtavern.data.remote.BasicAuthInterceptor
import com.tiffles.travelingtavern.data.remote.OkHttpConnectionTester
import com.tiffles.travelingtavern.data.repository.ServerConfigRepositoryImpl
import com.tiffles.travelingtavern.data.settings.settingsDataStore
import com.tiffles.travelingtavern.domain.repository.ConnectionTester
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
