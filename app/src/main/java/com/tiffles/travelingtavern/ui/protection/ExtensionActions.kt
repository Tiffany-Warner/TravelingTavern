package com.tiffles.travelingtavern.ui.protection

import com.tiffles.travelingtavern.domain.model.ExtensionOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The extension install / update dialog on view (A8). */
sealed interface ExtensionDialog {
    data object ConfirmInstall : ExtensionDialog

    data object ConfirmUpdate : ExtensionDialog

    /** The call is running; the dialog can't be dismissed. */
    data class Working(val update: Boolean) : ExtensionDialog

    /** How it ended. Never [ExtensionOutcome.Refused]: the pre-check's own handling shows that. */
    data class Done(val outcome: ExtensionOutcome) : ExtensionDialog
}

/**
 * The install / update flow shared by the Me tab's Desktop protection row and the one-time
 * dialog (A8): confirmation → call → result. Each call runs only from a tap, never retried.
 * Main thread.
 */
class ExtensionActions(
    private val scope: CoroutineScope,
    private val install: suspend () -> ExtensionOutcome,
    private val update: suspend () -> ExtensionOutcome
) {
    private val _dialog = MutableStateFlow<ExtensionDialog?>(null)
    val dialog: StateFlow<ExtensionDialog?> = _dialog.asStateFlow()

    fun askInstall() = ask(ExtensionDialog.ConfirmInstall)

    fun askUpdate() = ask(ExtensionDialog.ConfirmUpdate)

    /** The confirmation's button: runs what it asked about. */
    fun confirm() {
        when (_dialog.value) {
            ExtensionDialog.ConfirmInstall -> runInstall()
            ExtensionDialog.ConfirmUpdate -> runUpdate()
            else -> Unit
        }
    }

    /** Install without a second confirmation: the one-time dialog already asked. */
    fun runInstall() = run(isUpdate = false)

    /** Update without a second confirmation: "Already installed" offers it as its button. */
    fun runUpdate() = run(isUpdate = true)

    /** Closes the dialog, except while the call runs. */
    fun dismiss() {
        if (_dialog.value !is ExtensionDialog.Working) _dialog.value = null
    }

    private fun ask(dialog: ExtensionDialog) {
        if (_dialog.value !is ExtensionDialog.Working) _dialog.value = dialog
    }

    private fun run(isUpdate: Boolean) {
        if (_dialog.value is ExtensionDialog.Working) return
        _dialog.value = ExtensionDialog.Working(isUpdate)
        scope.launch {
            val outcome = if (isUpdate) update() else install()
            _dialog.value = if (outcome == ExtensionOutcome.Refused) null else ExtensionDialog.Done(outcome)
        }
    }
}
