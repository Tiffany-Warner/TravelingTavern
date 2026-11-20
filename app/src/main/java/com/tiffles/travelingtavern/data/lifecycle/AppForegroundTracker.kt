package com.tiffles.travelingtavern.data.lifecycle

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether the whole app is in the foreground, from [ProcessLifecycleOwner]. Unlike Activity
 * events it doesn't fire on rotation (the Activity is recreated, the app never leaves).
 */
@Singleton
class AppForegroundTracker @Inject constructor() : DefaultLifecycleObserver {

    private val _isForeground = MutableStateFlow(false)
    private val _returns = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var hasBeenInBackground = false

    val isForeground: StateFlow<Boolean> = _isForeground.asStateFlow()

    /** Emits each time the app comes back from the background (not on the first start). */
    val returns: SharedFlow<Unit> = _returns.asSharedFlow()

    /** Call once, on the main thread, from `Application.onCreate`. */
    fun start() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        _isForeground.value = true
        if (hasBeenInBackground) {
            hasBeenInBackground = false
            _returns.tryEmit(Unit)
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        _isForeground.value = false
        hasBeenInBackground = true
    }
}
