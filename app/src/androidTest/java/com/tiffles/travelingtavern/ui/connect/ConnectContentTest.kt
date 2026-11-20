package com.tiffles.travelingtavern.ui.connect

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tiffles.travelingtavern.R
import com.tiffles.travelingtavern.domain.model.ConnectionResult
import com.tiffles.travelingtavern.ui.theme.TravelingTavernTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConnectContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun string(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun show(
        state: ConnectUiState,
        onLoginEnabledChange: (Boolean) -> Unit = {},
        onConnect: () -> Unit = {}
    ) {
        composeRule.setContent {
            TravelingTavernTheme {
                ConnectContent(
                    uiState = state,
                    onUrlChange = {}, onLoginEnabledChange = onLoginEnabledChange,
                    onUsernameChange = {}, onPasswordChange = {}, onConnect = onConnect
                )
            }
        }
    }

    @Test
    fun connectDisabledWithoutAnAddress() {
        show(ConnectUiState())

        composeRule.onNodeWithText(string(R.string.connect_action)).assertIsNotEnabled()
    }

    @Test
    fun connectCallsBack() {
        var connected = false
        show(ConnectUiState(url = "100.64.0.2:8000"), onConnect = { connected = true })

        composeRule.onNodeWithText(string(R.string.connect_action)).performClick()
        assertTrue(connected)
    }

    @Test
    fun connectingShowsTheSpinnerTextInsteadOfTheButton() {
        show(ConnectUiState(url = "100.64.0.2:8000", status = ConnectStatus.Connecting))

        composeRule.onNodeWithText(string(R.string.connect_connecting)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.connect_action)).assertDoesNotExist()
    }

    @Test
    fun failureShowsTheErrorAndConnectAgain() {
        show(ConnectUiState(url = "100.64.0.2:8000", status = ConnectStatus.Failed(ConnectionResult.Unreachable)))

        composeRule.onNodeWithText(string(R.string.connect_result_unreachable)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.connect_action)).assertIsDisplayed()
    }

    @Test
    fun tappingTheAuthenticationRowTurnsItOn() {
        var toggled: Boolean? = null
        show(ConnectUiState(), onLoginEnabledChange = { toggled = it })

        composeRule.onNodeWithText(string(R.string.connect_username)).assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.connect_requires_auth)).performClick()
        assertEquals(true, toggled)
    }

    @Test
    fun loginFieldsShowWhenAuthenticationIsOn() {
        show(ConnectUiState(loginEnabled = true))

        composeRule.onNodeWithText(string(R.string.connect_username)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.connect_password)).assertIsDisplayed()
    }
}
