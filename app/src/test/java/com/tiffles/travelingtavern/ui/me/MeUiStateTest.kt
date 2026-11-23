package com.tiffles.travelingtavern.ui.me

import com.tiffles.travelingtavern.domain.model.DesktopProtection
import com.tiffles.travelingtavern.domain.model.ExtensionState
import com.tiffles.travelingtavern.domain.model.LeaseStatus
import com.tiffles.travelingtavern.domain.model.ParkReason
import com.tiffles.travelingtavern.ui.protection.ExtensionDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A8: the Me tab's row passes through; its install / update dialog hides while a scrim covers the app. */
class MeUiStateTest {

    private val row = DesktopProtection(ExtensionState.UpdateAvailable, untestedServer = false, stVersion = "1.19.1")
    private val dialog = ExtensionDialog.ConfirmUpdate

    @Test
    fun `a holder page shows the row and the dialog`() {
        val state = meUiState(row, dialog, LeaseStatus.Holder(busy = false, connected = true))

        assertEquals(row, state.protection)
        assertEquals(dialog, state.dialog)
    }

    @Test
    fun `the dialog hides under the scrim, the row stays`() {
        listOf(LeaseStatus.Parked(ParkReason.Lost), LeaseStatus.VersionMismatch(), LeaseStatus.NoDocumentStart).forEach {
            val state = meUiState(row, dialog, it)

            assertEquals(row, state.protection)
            assertNull(it.toString(), state.dialog)
        }
    }
}
