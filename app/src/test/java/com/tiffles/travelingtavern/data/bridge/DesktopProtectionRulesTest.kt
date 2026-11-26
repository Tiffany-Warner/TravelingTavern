package com.tiffles.travelingtavern.data.bridge

import com.tiffles.travelingtavern.domain.model.DesktopProtection
import com.tiffles.travelingtavern.domain.model.ExtensionState
import com.tiffles.travelingtavern.domain.model.LeaseStatus
import com.tiffles.travelingtavern.domain.model.ParkReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** A8: the Desktop protection row and the one-time dialog, from `leaseInfo`. */
class DesktopProtectionRulesTest {

    private fun info(extensionLeaseVersion: Int?, untested: Boolean = false, stVersion: String? = "1.19.1") =
        BridgeMessage.LeaseInfo(
            leaseVersion = 2,
            extensionVersion = extensionLeaseVersion?.let { "0.2.0" },
            extensionLeaseVersion = extensionLeaseVersion,
            stVersion = stVersion,
            untestedServer = untested
        )

    private fun protection(info: BridgeMessage.LeaseInfo) = toDesktopProtection(info, bridgeReady = true, appLeaseVersion = 2)

    @Test
    fun `each leaseInfo case maps to its row`() {
        assertEquals(ExtensionState.NotInstalled, protection(info(null))?.extension)
        assertEquals(ExtensionState.UpdateAvailable, protection(info(1))?.extension)
        assertEquals(ExtensionState.AppUpdateNeeded, protection(info(3))?.extension)
        assertEquals(ExtensionState.Installed, protection(info(2))?.extension)
    }

    @Test
    fun `untested server carries the SillyTavern version`() {
        val row = protection(info(2, untested = true, stVersion = "1.20.0"))

        assertEquals(DesktopProtection(ExtensionState.Installed, untestedServer = true, stVersion = "1.20.0"), row)
    }

    @Test
    fun `not known before leaseInfo or before the bridge is ready`() {
        assertNull(toDesktopProtection(null, bridgeReady = true, appLeaseVersion = 2))
        // Before APP_READY, "no extension" may only mean it hasn't announced itself yet (D37).
        assertNull(toDesktopProtection(info(null), bridgeReady = false, appLeaseVersion = 2))
    }

    private val notInstalled = DesktopProtection(ExtensionState.NotInstalled, untestedServer = false, stVersion = "1.19.1")
    private val holder = LeaseStatus.Holder(busy = false, connected = true)

    @Test
    fun `prompt is due on a holder page with no extension, once`() {
        assertTrue(isExtensionPromptDue(notInstalled, holder, unprotected = false, shown = false))
        assertFalse(isExtensionPromptDue(notInstalled, holder, unprotected = false, shown = true))
    }

    @Test
    fun `no prompt when an extension is reported or nothing is known yet`() {
        ExtensionState.entries.filter { it != ExtensionState.NotInstalled }.forEach {
            assertFalse(isExtensionPromptDue(notInstalled.copy(extension = it), holder, unprotected = false, shown = false))
        }
        assertFalse(isExtensionPromptDue(null, holder, unprotected = false, shown = false))
    }

    @Test
    fun `no prompt over the scrim, restart, no document start, unprotected or before a report`() {
        listOf(
            LeaseStatus.Parked(ParkReason.Held),
            LeaseStatus.VersionMismatch(),
            LeaseStatus.NoDocumentStart,
            LeaseStatus.Unprotected,
            LeaseStatus.NotReported
        ).forEach { assertFalse(it.toString(), isExtensionPromptDue(notInstalled, it, unprotected = false, shown = false)) }
        assertFalse(isExtensionPromptDue(notInstalled, holder, unprotected = true, shown = false))
    }
}
