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
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.matcher.RootMatchers
import androidx.test.espresso.matcher.ViewMatchers
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tiffles.travelingtavern.R
import com.tiffles.travelingtavern.domain.model.LeaseAction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
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

    private val actions = mutableListOf<LeaseAction>()
    private var confirms = 0
    private var cancels = 0
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
                    onAction = { actions += it },
                    onConfirmWarning = { confirms++ },
                    onCancelWarning = { cancels++ },
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
        show(LeaseGuardUiState(scrim = ScrimState.InUse, actions = listOf(LeaseAction.UseHere)))

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
        show(LeaseGuardUiState(scrim = ScrimState.InUse, actions = listOf(LeaseAction.UseHere)))

        composeRule.waitForIdle()
        // Into the scrim's window, which has the focus (the app's own window doesn't).
        Espresso.onView(ViewMatchers.isRoot()).inRoot(RootMatchers.isDialog()).perform(ViewActions.pressBack())
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

        setState(LeaseGuardUiState(scrim = ScrimState.InUse, actions = listOf(LeaseAction.UseHere)))
        composeRule.waitForIdle()
        setState(LeaseGuardUiState())

        composeRule.onNodeWithTag(FIELD).assertIsNotFocused()
    }

    private fun text(id: Int) = composeRule.onNodeWithText(string(id))

    @Test
    fun useHereRowsShowTheirTextAndCallUseHere() {
        val rows = mapOf(
            ScrimState.InUse to (R.string.lease_in_use_title to R.string.lease_in_use_line),
            ScrimState.LostRace to (R.string.lease_lost_race_title to R.string.lease_lost_race_line)
        )
        val setState = show(LeaseGuardUiState(scrim = ScrimState.InUse, actions = listOf(LeaseAction.UseHere)))
        rows.forEach { (state, rowText) ->
            setState(LeaseGuardUiState(scrim = state, actions = listOf(LeaseAction.UseHere)))
            text(rowText.first).assertIsDisplayed()
            text(rowText.second).assertIsDisplayed()
            text(R.string.lease_use_here).performClick()
        }

        assertEquals(listOf(LeaseAction.UseHere, LeaseAction.UseHere), actions)
    }

    @Test
    fun progressRowsHaveNoButtonUntilOffered() {
        val setState = show(LeaseGuardUiState(scrim = ScrimState.Claiming))
        text(R.string.lease_claiming_title).assertIsDisplayed()
        text(R.string.lease_claiming_line).assertIsDisplayed()
        text(R.string.lease_use_here).assertDoesNotExist()

        setState(LeaseGuardUiState(scrim = ScrimState.WaitingForReply))
        text(R.string.lease_waiting_title).assertIsDisplayed()
        text(R.string.lease_waiting_line).assertIsDisplayed()
        text(R.string.lease_take_over_anyway).assertDoesNotExist()

        // After 10 s the page offers Take over anyway (E4), below the spinner.
        setState(LeaseGuardUiState(scrim = ScrimState.WaitingForReply, actions = listOf(LeaseAction.TakeOverAnyway)))
        text(R.string.lease_waiting_line_stuck).assertIsDisplayed()
        text(R.string.lease_take_over_anyway).performClick()

        assertEquals(listOf(LeaseAction.TakeOverAnyway), actions)
    }

    /** Every error row: its title and line, and each offered button, top to bottom in the given order. */
    @Test
    fun errorRowsShowTheirTextAndButtonsInOrder() {
        val rows = listOf(
            Row(ScrimState.Unreadable, R.string.lease_unreadable_title, R.string.lease_unreadable_line, UNREADABLE),
            Row(ScrimState.Damaged, R.string.lease_damaged_title, R.string.lease_damaged_line, DAMAGED),
            Row(ScrimState.Error, R.string.lease_error_title, R.string.lease_error_line, ERROR),
            Row(ScrimState.UpdateApp, R.string.lease_update_title, R.string.lease_update_app_line, ERROR),
            Row(ScrimState.ReloadNeeded, R.string.lease_reload_title, R.string.lease_reload_line, listOf(LeaseAction.Refresh)),
            Row(
                ScrimState.RestartNeeded, R.string.lease_restart_title, R.string.lease_restart_line,
                listOf(LeaseAction.Refresh, LeaseAction.ContinueWithoutProtection)
            ),
            Row(
                ScrimState.UnknownState, R.string.lease_error_title, R.string.lease_unknown_line,
                listOf(LeaseAction.Refresh, LeaseAction.ContinueWithoutProtection)
            )
        )
        val setState = show(LeaseGuardUiState())
        rows.forEach { row ->
            setState(LeaseGuardUiState(scrim = row.state, actions = row.actions))
            text(row.title).assertIsDisplayed()
            text(row.line).assertIsDisplayed()
            val tops = row.actions.map { action ->
                text(action.label()).assertIsDisplayed().getUnclippedBoundsInRoot().top
            }
            assertEquals("${row.state} order", tops.sorted(), tops)
        }
    }

    @Test
    fun eachButtonCallsItsAction() {
        show(LeaseGuardUiState(scrim = ScrimState.Unreadable, actions = UNREADABLE))

        UNREADABLE.forEach { text(it.label()).performClick() }

        assertEquals(UNREADABLE, actions)
    }

    @Test
    fun warningStepReplacesTheScrimWithItsTextConfirmAndCancel() {
        val warnings = mapOf(
            LeaseAction.TakeOverAnyway to (R.string.lease_warn_take_over_title to R.string.lease_warn_take_over_line),
            LeaseAction.TurnOffProtection to (R.string.lease_warn_turn_off_title to R.string.lease_warn_turn_off_line),
            LeaseAction.ContinueWithoutProtection to (R.string.lease_warn_continue_title to R.string.lease_warn_turn_off_line)
        )
        val setState = show(LeaseGuardUiState())
        warnings.forEach { (action, warningText) ->
            setState(LeaseGuardUiState(scrim = ScrimState.Damaged, actions = DAMAGED, warning = action))
            text(warningText.first).assertIsDisplayed()
            text(warningText.second).assertIsDisplayed()
            text(R.string.lease_damaged_title).assertDoesNotExist()
            text(R.string.lease_refresh).assertDoesNotExist()
            text(action.label()).performClick()
            text(R.string.lease_cancel).performClick()
        }

        assertEquals(warnings.size, confirms)
        assertEquals(warnings.size, cancels)
        assertEquals(emptyList<LeaseAction>(), actions)
    }

    @Test
    fun protectionOffShowsTheBannerOverAUsableAppAndTurnsProtectionOn() {
        show(LeaseGuardUiState(protectionOff = true))
        val bannerText = InstrumentationRegistry.getInstrumentation().targetContext
            .getString(R.string.lease_off_banner, string(R.string.lease_off_banner_link))

        composeRule.onNodeWithText(bannerText).assertIsDisplayed()
        composeRule.onNode(isDialog()).assertDoesNotExist()
        composeRule.onNodeWithText(TAB).performClick()
        text(R.string.lease_turn_protection_on).performClick()

        assertEquals(1, tabTaps)
        assertEquals(listOf(LeaseAction.TurnProtectionOn), actions)
    }

    private data class Row(val state: ScrimState, val title: Int, val line: Int, val actions: List<LeaseAction>)

    private fun LeaseAction.label(): Int = when (this) {
        LeaseAction.UseHere -> R.string.lease_use_here
        LeaseAction.Refresh -> R.string.lease_refresh
        LeaseAction.TakeOverAnyway -> R.string.lease_take_over_anyway
        LeaseAction.TurnOffProtection -> R.string.lease_turn_off_protection
        LeaseAction.TurnProtectionOn -> R.string.lease_turn_protection_on
        LeaseAction.ContinueWithoutProtection -> R.string.lease_continue_unprotected
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
        val UNREADABLE = listOf(LeaseAction.UseHere, LeaseAction.Refresh, LeaseAction.TakeOverAnyway, LeaseAction.TurnOffProtection)
        val DAMAGED = listOf(LeaseAction.TakeOverAnyway, LeaseAction.Refresh, LeaseAction.TurnOffProtection)
        val ERROR = listOf(LeaseAction.Refresh, LeaseAction.TakeOverAnyway, LeaseAction.TurnOffProtection)
    }
}
