package com.tiffles.travelingtavern.data.bridge

import com.tiffles.travelingtavern.domain.model.DesktopProtection
import com.tiffles.travelingtavern.domain.model.ExtensionState
import com.tiffles.travelingtavern.domain.model.LeaseStatus

/**
 * The Desktop protection row from the current load's `leaseInfo` (A8), or null while it isn't
 * known. Known only once the bridge is ready (SillyTavern's `APP_READY`, D37): the extension
 * announces itself while SillyTavern loads its extensions, which finishes before `APP_READY`, so
 * an earlier `leaseInfo` with no extension may just mean "not loaded yet".
 */
internal fun toDesktopProtection(
    info: BridgeMessage.LeaseInfo?,
    bridgeReady: Boolean,
    appLeaseVersion: Int
): DesktopProtection? {
    if (info == null || !bridgeReady) return null
    val extensionVersion = info.extensionLeaseVersion
    val extension = when {
        extensionVersion == null -> ExtensionState.NotInstalled
        extensionVersion < appLeaseVersion -> ExtensionState.UpdateAvailable
        extensionVersion > appLeaseVersion -> ExtensionState.AppUpdateNeeded
        else -> ExtensionState.Installed
    }
    return DesktopProtection(extension = extension, untestedServer = info.untestedServer, stVersion = info.stVersion)
}

/**
 * The one-time extension dialog is due (A8): never shown before, no extension reported on a page
 * that holds the lease, and not running without protection. Never over the parked scrim, the
 * "Restart TravelingTavern" state or Continue without protection, and never before connecting
 * (those all lack a holder page).
 */
internal fun isExtensionPromptDue(
    protection: DesktopProtection?,
    status: LeaseStatus,
    unprotected: Boolean,
    shown: Boolean
): Boolean = !shown && !unprotected && status is LeaseStatus.Holder &&
    protection?.extension == ExtensionState.NotInstalled
