package com.tiffles.travelingtavern.ui.protection

import com.tiffles.travelingtavern.domain.model.ExtensionOutcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A8: confirmation → call → result, shared by the Me row and the one-time dialog. */
@OptIn(ExperimentalCoroutinesApi::class)
class ExtensionActionsTest {

    private var installs = 0
    private var updates = 0
    private var installOutcome = ExtensionOutcome.Installed
    private var updateOutcome = ExtensionOutcome.Updated

    private fun TestScope.actions(gate: CompletableDeferred<Unit>? = null) = ExtensionActions(
        scope = backgroundScope,
        install = { gate?.await(); installs++; installOutcome },
        update = { updates++; updateOutcome }
    )

    @Test
    fun `install asks first, then runs on confirm and shows the result`() = runTest {
        val actions = actions()

        actions.askInstall()
        assertEquals(ExtensionDialog.ConfirmInstall, actions.dialog.value)
        assertEquals(0, installs)

        actions.confirm()
        runCurrent()
        assertEquals(1, installs)
        assertEquals(ExtensionDialog.Done(ExtensionOutcome.Installed), actions.dialog.value)
    }

    @Test
    fun `cancelling the confirmation runs nothing`() = runTest {
        val actions = actions()

        actions.askUpdate()
        actions.dismiss()
        runCurrent()

        assertNull(actions.dialog.value)
        assertEquals(0, updates)
    }

    @Test
    fun `while working the dialog can't be dismissed or started again`() = runTest {
        val gate = CompletableDeferred<Unit>()
        val actions = actions(gate)

        actions.runInstall()
        runCurrent()
        assertEquals(ExtensionDialog.Working(update = false), actions.dialog.value)

        actions.dismiss()
        actions.runInstall()
        actions.askUpdate()
        assertEquals(ExtensionDialog.Working(update = false), actions.dialog.value)

        gate.complete(Unit)
        runCurrent()
        assertEquals(1, installs)
    }

    @Test
    fun `refused by the pre-check closes the dialog`() = runTest {
        installOutcome = ExtensionOutcome.Refused
        val actions = actions()

        actions.runInstall()
        runCurrent()

        assertNull(actions.dialog.value)
    }

    @Test
    fun `already installed offers Update, which runs without another confirmation`() = runTest {
        installOutcome = ExtensionOutcome.AlreadyInstalled
        updateOutcome = ExtensionOutcome.UpToDate
        val actions = actions()

        actions.runInstall()
        runCurrent()
        assertEquals(ExtensionDialog.Done(ExtensionOutcome.AlreadyInstalled), actions.dialog.value)

        actions.runUpdate()
        runCurrent()
        assertEquals(1, updates)
        assertEquals(ExtensionDialog.Done(ExtensionOutcome.UpToDate), actions.dialog.value)
    }
}
