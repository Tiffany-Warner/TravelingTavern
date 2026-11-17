package com.tiffles.travelingtavern.domain.model

/** Outcome of testing a candidate server address. */
sealed class ConnectionResult {
    data object Success : ConnectionResult()
    data object Unreachable : ConnectionResult()
    data object AuthRequired : ConnectionResult()
    data object AuthFailed : ConnectionResult()
    data object NotSillyTavern : ConnectionResult()
    data object InvalidUrl : ConnectionResult()
}
