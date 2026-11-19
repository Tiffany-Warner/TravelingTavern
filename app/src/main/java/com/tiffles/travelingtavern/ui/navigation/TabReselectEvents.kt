package com.tiffles.travelingtavern.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Broadcasts when the user re-selects the tab that is already showing. Tab screens
 * collect [events] to scroll their list back to the top (lists arrive in 1.6).
 */
class TabReselectEvents {
    private val _events = MutableSharedFlow<TopLevelTab>(extraBufferCapacity = 1)
    val events: SharedFlow<TopLevelTab> = _events.asSharedFlow()

    fun reselect(tab: TopLevelTab) {
        _events.tryEmit(tab)
    }
}

val LocalTabReselectEvents = compositionLocalOf<TabReselectEvents> {
    error("No TabReselectEvents provided")
}

/** Runs [action] each time the user re-selects [tab] while it is already showing. */
@Composable
fun OnTabReselected(tab: TopLevelTab, action: suspend () -> Unit) {
    val events = LocalTabReselectEvents.current
    val currentAction by rememberUpdatedState(action)
    LaunchedEffect(events, tab) {
        events.events.filter { it == tab }.collect { currentAction() }
    }
}