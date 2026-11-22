package com.tiffles.travelingtavern.domain.model

/** The desktop extension as the lease's `leaseInfo` reports it (A8), compared with the app's own lease version. */
enum class ExtensionState {
    /** No extension reported (`extensionLeaseVersion` null). */
    NotInstalled,

    /** The extension's lease version is older than the app's: offer Update. */
    UpdateAvailable,

    /** The extension's lease version is newer than the app's: the app is the outdated side. */
    AppUpdateNeeded,

    /** Same lease version. */
    Installed
}

/**
 * The Desktop protection row (A8). [untestedServer]: SillyTavern [stVersion] (null if it couldn't
 * be read) isn't in the lease's tested list.
 */
data class DesktopProtection(
    val extension: ExtensionState,
    val untestedServer: Boolean,
    val stVersion: String?
)

/** How an extension install or update from the app ended (A8). */
enum class ExtensionOutcome {
    /** Installed; the page was reloaded with a cleared cache. */
    Installed,

    /** `409`: already installed, so Update is offered instead. */
    AlreadyInstalled,

    /** It was behind and has pulled; the page was reloaded with a cleared cache. */
    Updated,

    /** Nothing to pull; no reload. */
    UpToDate,

    /** Install failed (or no answer): the user installs it from the repo address. */
    InstallFailed,

    /** Update failed: `404` (not in the user's folder, installed for all users, extensions off) or another error. */
    UpdateFailed,

    /** Refused by the lease pre-check (A4); its own handling (scrim, loading message) applies. */
    Refused
}

/** Public addresses and names for the desktop extension and the app (A8). */
object ProjectLinks {
    /** What SillyTavern's Install extension takes; installs into [EXTENSION_FOLDER]. */
    const val EXTENSION_REPO_URL = "https://github.com/Tiffany-Warner/TravelingTavern-Extension"

    /** The extension's folder in SillyTavern (E11 Names), the name the update call takes. */
    const val EXTENSION_FOLDER = "TravelingTavern-Extension"

    /** Where app releases are published (none before 1.0.0). */
    const val APP_RELEASES_URL = "https://github.com/Tiffany-Warner/TravelingTavern/releases"
}
