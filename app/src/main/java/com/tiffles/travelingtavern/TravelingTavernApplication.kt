package com.tiffles.travelingtavern

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.tiffles.travelingtavern.data.bridge.LeaseLifecycle
import com.tiffles.travelingtavern.data.lifecycle.AppForegroundTracker
import dagger.Lazy
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class TravelingTavernApplication : Application(), SingletonImageLoader.Factory {

    /** The app's one client, so avatar requests get the server's login like everything else. */
    @Inject
    lateinit var okHttpClient: Lazy<OkHttpClient>

    @Inject
    lateinit var foregroundTracker: AppForegroundTracker

    /** Foreground and background for the session lease (A5) and the page's `setAppState`. */
    @Inject
    lateinit var leaseLifecycle: LeaseLifecycle

    override fun onCreate() {
        super.onCreate()
        foregroundTracker.start()
        leaseLifecycle.start()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient.get() })) }
            .crossfade(true)
            .build()
}
