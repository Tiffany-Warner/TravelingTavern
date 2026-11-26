package com.tiffles.travelingtavern.ui.me

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tiffles.travelingtavern.R
import com.tiffles.travelingtavern.domain.model.DesktopProtection
import com.tiffles.travelingtavern.domain.model.ExtensionOutcome
import com.tiffles.travelingtavern.domain.model.ExtensionState
import com.tiffles.travelingtavern.domain.model.ProjectLinks
import com.tiffles.travelingtavern.ui.protection.ExtensionDialog
import com.tiffles.travelingtavern.ui.theme.TravelingTavernTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A8: the Desktop protection row's states and actions, and the install / update dialogs, in stateless content. */
@RunWith(AndroidJUnit4::class)
class MeContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun string(id: Int, vararg args: Any) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    private var installs = 0
    private var updates = 0
    private var appUpdates = 0
    private var confirms = 0
    private var updateInstead = 0
    private var dismisses = 0

    private fun show(protection: DesktopProtection?, dialog: ExtensionDialog? = null) {
        composeRule.setContent {
            TravelingTavernTheme {
                MeContent(
                    uiState = MeUiState(protection = protection, dialog = dialog),
                    onInstall = { installs++ },
                    onUpdate = { updates++ },
                    onGetAppUpdate = { appUpdates++ },
                    onConfirm = { confirms++ },
                    onUpdateInstead = { updateInstead++ },
                    onDismissDialog = { dismisses++ }
                )
            }
        }
    }

    private fun row(state: ExtensionState, untested: Boolean = false, stVersion: String? = "1.19.1") =
        DesktopProtection(state, untestedServer = untested, stVersion = stVersion)

    @Test
    fun checkingShowsNoAction() {
        show(protection = null)

        composeRule.onNodeWithText(string(R.string.protection_checking)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.protection_install)).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.protection_update)).assertDoesNotExist()
    }

    @Test
    fun notInstalledOffersInstall() {
        show(row(ExtensionState.NotInstalled))

        composeRule.onNodeWithText(string(R.string.protection_not_installed)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.protection_install)).performClick()
        assertEquals(1, installs)
    }

    @Test
    fun olderExtensionOffersUpdate() {
        show(row(ExtensionState.UpdateAvailable))

        composeRule.onNodeWithText(string(R.string.protection_update_available)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.protection_update)).performClick()
        assertEquals(1, updates)
    }

    @Test
    fun newerExtensionLinksToTheAppRelease() {
        show(row(ExtensionState.AppUpdateNeeded))

        composeRule.onNodeWithText(string(R.string.protection_update_app)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.protection_get_app_update)).performClick()
        assertEquals(1, appUpdates)
    }

    @Test
    fun installedHasNoAction() {
        show(row(ExtensionState.Installed))

        composeRule.onNodeWithText(string(R.string.protection_installed)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.protection_install)).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.protection_update)).assertDoesNotExist()
    }

    @Test
    fun untestedServerNamesTheVersion() {
        show(row(ExtensionState.Installed, untested = true, stVersion = "1.20.0"))

        composeRule.onNodeWithText(string(R.string.protection_untested, "1.20.0")).assertIsDisplayed()
    }

    @Test
    fun confirmationRunsOrCancels() {
        show(row(ExtensionState.NotInstalled), dialog = ExtensionDialog.ConfirmInstall)

        composeRule.onNodeWithText(string(R.string.protection_confirm_install_title)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.lorebook_cancel)).performClick()
        assertEquals(1, dismisses)
    }

    @Test
    fun installedResultAsksToReloadTheComputer() {
        show(row(ExtensionState.Installed), dialog = ExtensionDialog.Done(ExtensionOutcome.Installed))

        composeRule.onNodeWithText(string(R.string.protection_reload_computer)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.protection_ok)).performClick()
        assertEquals(1, dismisses)
    }

    @Test
    fun installFailureShowsTheRepoAddress() {
        show(row(ExtensionState.NotInstalled), dialog = ExtensionDialog.Done(ExtensionOutcome.InstallFailed))

        composeRule.onNodeWithText(ProjectLinks.EXTENSION_REPO_URL).assertIsDisplayed()
    }

    @Test
    fun updateFailureIsPlainText() {
        show(row(ExtensionState.UpdateAvailable), dialog = ExtensionDialog.Done(ExtensionOutcome.UpdateFailed))

        composeRule.onNodeWithText(string(R.string.protection_update_failed_text)).assertIsDisplayed()
        composeRule.onNodeWithText(ProjectLinks.EXTENSION_REPO_URL).assertDoesNotExist()
    }

    @Test
    fun alreadyInstalledOffersUpdate() {
        show(row(ExtensionState.NotInstalled), dialog = ExtensionDialog.Done(ExtensionOutcome.AlreadyInstalled))

        composeRule.onNodeWithText(string(R.string.protection_update)).performClick()
        assertEquals(1, updateInstead)
    }

    @Test
    fun upToDateSaysSo() {
        show(row(ExtensionState.UpdateAvailable), dialog = ExtensionDialog.Done(ExtensionOutcome.UpToDate))

        composeRule.onNodeWithText(string(R.string.protection_up_to_date)).assertIsDisplayed()
    }
}
