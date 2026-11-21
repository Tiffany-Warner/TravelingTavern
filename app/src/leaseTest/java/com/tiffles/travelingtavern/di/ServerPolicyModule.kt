package com.tiffles.travelingtavern.di

import com.tiffles.travelingtavern.BuildConfig
import com.tiffles.travelingtavern.data.remote.SingleServerPolicy
import com.tiffles.travelingtavern.domain.repository.ServerPolicy
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** The `leaseTest` build connects only to the test install set in `local.properties` (A9). */
@Module
@InstallIn(SingletonComponent::class)
object ServerPolicyModule {

    @Provides
    fun provideServerPolicy(): ServerPolicy = SingleServerPolicy(BuildConfig.LEASE_TEST_SERVER_URL)
}
