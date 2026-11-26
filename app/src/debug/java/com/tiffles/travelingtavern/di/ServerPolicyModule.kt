package com.tiffles.travelingtavern.di

import com.tiffles.travelingtavern.data.remote.AnyServerPolicy
import com.tiffles.travelingtavern.domain.repository.ServerPolicy
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Any server is allowed. The `leaseTest` build type binds its own single-server policy instead. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ServerPolicyModule {

    @Binds
    abstract fun bindServerPolicy(impl: AnyServerPolicy): ServerPolicy
}
