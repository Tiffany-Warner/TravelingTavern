package com.tiffles.travelingtavern.ui.lease

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasProgressBarRangeInfo
import androidx.compose.ui.test.click
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onFirst
import androidx.activity.compose.BackHandler
import androidx.test.espresso.Espresso
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tiffles.travelingtavern.R
import com.tiffles.travelingtavern.ui.theme.TravelingTavernTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LeaseGuardContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun string(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private var useHere = 0
    private var reload = 0
    private var update = 0
    private var continueUnprotected = 0
    private var tabTaps = 0
    private var backPresses = 0
    private val snackbarHostState = SnackbarHostState()
    private lateinit var scope: CoroutineScope

    /** A stand-in app shell: a "tab bar" button, a text field and the guard's snackbar host. */
    private fun show(initial: LeaseGuardUiState): (LeaseGuardUiState) -> Unit {
        var uiState by mutableStateOf(initial)
        composeRule.setContent {
            scope = rememberCoroutineScope()
            TravelingTavernTheme {
                LeaseGuardContent(
                    uiState = uiState,
                    onUseHere = { useHere++ },
                    onReload = { reload++ },
                    onUpdateWebView = { update++ },
                    onContinueWithoutProtection = { continueUnprotected++ },
                    snackbarHostState = snackbarHostState
                ) { snackbarHost ->
                    BackHandler { backPresses++ }
                    Column {
                        TextField(value = "", onValueChange = {}, modifier = Modifier.testTag(FIELD))
                        Button(onClick = { tabTaps++ }) { Text(TAB) }
                        snackbarHost()
                    }
                }
            }
        }
        return { uiState = it }
    }

    private val loadingBar = hasProgressBarRangeInfo(ProgressBarRangeInfo.Indeterminate)

    @Test
    fun noScrimLeavesTheAppUsable() {
        show(LeaseGuardUiState())

        composeRule.onNodeWithText(TAB).performClick()

        assertEquals(1, tabTaps)
        composeRule.onNodeWithText(string(R.string.lease_use_here)).assertDoesNotExist()
    }

    @Test
    fun scrimCoversTheShellAndSwallowsTaps() {
        show(LeaseGuardUiState(scrim = ScrimState.InUse))

        // The scrim is a window of its own (D42), above any sheet or dialog the app has open.
        composeRule.onNode(isDialog()).assertExists()
        // The tab button is out of the accessibility tree; tap everywhere it could be in the app's
        // own window (the first root; the dialog's semantics sit below its own root, so isDialog()
        // can't tell the roots apart), which the test reaches past the scrim's window.
        composeRule.onNodeWithText(TAB).assertDoesNotExist()
        composeRule.onAllNodes(isRoot()).onFirst().performTouchInput {
            click(topLeft)
            click(center)
            click(bottomCenter)
            click(centerLeft)
        }

        assertEquals(0, tabTaps)
    }

    @Test
    fun backUnderTheScrimReachesTheAppAndKeepsTheScrim() {
        show(LeaseGuardUiState(scrim = ScrimState.InUse))

        Espresso.pressBack()
        composeRule.waitForIdle()

        // D16: Back navigates under the scrim (the scrim's window hands it to the app, D42).
        assertEquals(1, backPresses)
        composeRule.onNodeWithText(string(R.string.lease_use_here)).assertIsDisplayed()
    }

    @Test
    fun scrimClearsTextFocus() {
        val setState = show(LeaseGuardUiState())
        composeRule.onNodeWithTag(FIELD).performClick()
        composeRule.onNodeWithTag(FIELD).assertIsFocused()

        setState(LeaseGuardUiState(scrim = ScrimState.InUse))
        composeRule.waitForIdle()
        setState(LeaseGuardUiState())

        composeRule.onNodeWithTag(FIELD).assertIsNotFocused()
    }

    @Test
    fun useHereRowsShowTheirTextAndCallUseHere() {
        val rows = mapOf(
            ScrimState.InUse to (R.string.lease_in_use_title to R.string.lease_in_use_line),
            ScrimState.LostRace to (R.string.lease_lost_race_title to R.string.lease_lost_race_line),
            ScrimState.Unreadable to (R.string.lease_unreadable_title to R.string.lease_unreadable_line)
        )
        val setState = show(LeaseGuardUiState(scrim = ScrimState.InUse))
        rows.forEach { (state, text) ->
            setState(LeaseGuardUiState(scrim = state))
            composeRule.onNodeWithText(string(text.first)).assertIsDisplayed()
            composeRule.onNodeWithText(string(text.second)).assertIsDisplayed()
            composeRule.onNodeWithText(string(R.string.lease_use_here)).performClick()
        }

        assertEquals(rows.size, useHere)
    }

    @Test
    fun progressRowsHaveNoButton() {
        val setState = show(LeaseGuardUiState(scrim = ScrimState.Claiming))
        composeRule.onNodeWithText(string(R.string.lease_claiming_title)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.lease_claiming_line)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.lease_use_here)).assertDoesNotExist()

        setState(LeaseGuardUiState(scrim = ScrimState.WaitingForReply))
        composeRule.onNodeWithText(string(R.string.lease_waiting_title)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.lease_waiting_line)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.lease_use_here)).assertDoesNotExist()
    }

    @Test
    fun deadEndRowsHaveNoControl() {
        val setState = show(LeaseGuardUiState(scrim = ScrimState.UpdateApp))
        composeRule.onNodeWithText(string(R.string.lease_update_title)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.lease_update_app_line)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.lease_update)).assertDoesNotExist()

        setState(LeaseGuardUiState(scrim = ScrimState.RestartNeeded))
        composeRule.onNodeWithText(string(R.string.lease_restart_title)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.lease_restart_line)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.lease_use_here)).assertDoesNotExist()
    }

    @Test
    fun reloadNeededCallsReload() {
        show(LeaseGuardUiState(scrim = ScrimState.ReloadNeeded))
        composeRule.onNodeWithText(string(R.string.lease_reload_title)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.lease_reload_line)).assertIsDisplayed()

        composeRule.onNodeWithText(string(R.string.lease_reload)).performClick()

        assertEquals(1, reload)
        assertEquals(0, useHere)
    }

    @Test
    fun noDocumentStartOffersUpdateAndContinue() {
        show(LeaseGuardUiState(scrim = ScrimState.UpdateWebView))
        composeRule.onNodeWithText(string(R.string.lease_update_title)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.lease_update_webview_line)).assertIsDisplayed()

        composeRule.onNodeWithText(string(R.string.lease_update)).performClick()
        composeRule.onNodeWithText(string(R.string.lease_continue_unprotected)).performClick()

        assertEquals(1, update)
        assertEquals(1, continueUnprotected)
    }

    @Test
    fun unprotectedShowsTheBannerOverAUsableApp() {
        show(LeaseGuardUiState(unprotected = true))

        composeRule.onNodeWithText(string(R.string.lease_unprotected_banner)).assertIsDisplayed()
        composeRule.onNodeWithText(TAB).performClick()

        assertEquals(1, tabTaps)
    }

    // -- A4 / D25: only a user-started command's wait sets `waiting`

    @Test
    fun userStartedWaitShowsTheLoadingBarOverAUsableApp() {
        show(LeaseGuardUiState(waiting = true))

        composeRule.onNode(loadingBar).assertIsDisplayed()
        composeRule.onNodeWithText(TAB).performClick()

        assertEquals(1, tabTaps)
    }

    @Test
    fun noUserStartedWaitShowsNoLoadingBar() {
        val update = show(LeaseGuardUiState(waiting = true))

        update(LeaseGuardUiState(waiting = false))

        composeRule.onNode(loadingBar).assertDoesNotExist()
    }

    @Test
    fun stillLoadingMessageShowsThroughTheShellsSnackbarHost() {
        show(LeaseGuardUiState())
        val message = string(R.string.lease_still_loading)

        composeRule.runOnIdle { scope.launch { snackbarHostState.showSnackbar(message) } }

        composeRule.onNodeWithText(message).assertIsDisplayed()
    }

    private companion object {
        const val TAB = "Chats tab"
        const val FIELD = "field"
    }
}
