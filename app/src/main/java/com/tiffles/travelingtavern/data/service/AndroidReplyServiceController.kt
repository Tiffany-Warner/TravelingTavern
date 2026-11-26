package com.tiffles.travelingtavern.data.service

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.tiffles.travelingtavern.data.bridge.ReplyServiceController
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [ReplyServiceController] for [ReplyService]. [stop] never calls `stopService`: the service
 * watches [wanted] and stops itself, always after its own `startForeground`, so a reply that ends
 * before the service has started can't leave a start without `startForeground` (which Android
 * treats as an error). Main thread only.
 */
@Singleton
class AndroidReplyServiceController @Inject constructor(
    @ApplicationContext private val context: Context
) : ReplyServiceController {

    private val _wanted = MutableStateFlow(false)

    /** Whether the service should be running. */
    val wanted: StateFlow<Boolean> = _wanted.asStateFlow()

    override val supported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE

    override fun start(): Boolean {
        if (!supported) return false
        _wanted.value = true
        return try {
            context.startForegroundService(Intent(context, ReplyService::class.java))
            true
        } catch (e: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException (an IllegalStateException): not allowed now.
            _wanted.value = false
            Log.w(TAG, "Reply service not started: ${e.javaClass.simpleName}")
            false
        }
    }

    override fun stop() {
        _wanted.value = false
    }

    private companion object {
        const val TAG = "ReplyService"
    }
}
