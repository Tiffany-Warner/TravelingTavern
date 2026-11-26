package com.tiffles.travelingtavern.ui.protection

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tiffles.travelingtavern.R
import com.tiffles.travelingtavern.ui.theme.TravelingTavernTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** A8: the one-time "Keep your devices from overwriting each other" dialog's buttons. */
@RunWith(AndroidJUnit4::class)
class ExtensionPromptContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun string(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private var installs = 0
    private var notNow = 0

    private fun show(uiState: ExtensionPromptUiState) {
        composeRule.setContent {
            TravelingTavernTheme {
                ExtensionPromptContent(
                    uiState = uiState,
                    onInstall = { installs++ },
                    onNotNow = { notNow++ },
                    onConfirm = {},
                    onUpdateInstead = {},
                    onDismissDialog = {}
                )
            }
        }
    }

    @Test
    fun installButton() {
        show(ExtensionPromptUiState(prompt = true))

        composeRule.onNodeWithText(string(R.string.protection_prompt_title)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.protection_install)).performClick()
        assertEquals(1, installs)
    }

    @Test
    fun notNowButton() {
        show(ExtensionPromptUiState(prompt = true))

        composeRule.onNodeWithText(string(R.string.protection_not_now)).performClick()
        assertEquals(1, notNow)
    }

    @Test
    fun nothingWhenNotDue() {
        show(ExtensionPromptUiState())

        composeRule.onNodeWithText(string(R.string.protection_prompt_title)).assertDoesNotExist()
    }
}
