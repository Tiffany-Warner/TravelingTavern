package com.tiffles.travelingtavern.data.bridge

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription

/**
 * Subscribes to the page's messages, then calls [send] (so a fast answer can't slip past), and
 * returns the first message matching [match]. Returns null at once when [send] reports the
 * command was refused by the lease pre-check (A4), instead of waiting for an answer that can't
 * come. Callers bound the wait themselves (`withTimeoutOrNull`).
 */
internal suspend fun SharedFlow<BridgeMessage>.sendAndAwait(
    send: suspend () -> Boolean,
    match: (BridgeMessage) -> Boolean
): BridgeMessage? = try {
    onSubscription { if (!send()) throw CommandRefused() }.first(match)
} catch (_: CommandRefused) {
    null
}

/** Ends [sendAndAwait]'s wait when the command wasn't sent. Never leaves this file. */
private class CommandRefused : Exception()
