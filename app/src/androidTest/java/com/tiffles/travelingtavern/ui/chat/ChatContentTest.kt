package com.tiffles.travelingtavern.ui.chat

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tiffles.travelingtavern.R
import com.tiffles.travelingtavern.domain.model.CharacterSummary
import com.tiffles.travelingtavern.ui.theme.TravelingTavernTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChatContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun string(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    // No image URL: the initial shows instead, and nothing touches the network.
    private val ready = ChatUiState.Ready(CharacterSummary("Bob.png", "Bob", avatarUrl = "", portraitUrl = "", lastChatAt = null))

    private var settingsOpened = 0
    private var messagesShown = 0

    private fun show(message: VoiceMessage?) {
        composeRule.setContent {
            TravelingTavernTheme {
                ChatContent(
                    uiState = ready,
                    voiceMessage = message,
                    onBack = {},
                    onOpenAppSettings = { settingsOpened++ },
                    onVoiceMessageShown = { messagesShown++ }
                )
            }
        }
    }

    @Test
    fun permissionDeniedOffersSettings() {
        show(VoiceMessage.PermissionDenied)

        composeRule.onNodeWithText(string(R.string.chat_voice_permission_denied)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.chat_voice_settings)).performClick()
        composeRule.waitForIdle()

        assertEquals(1, settingsOpened)
        assertEquals(1, messagesShown)
    }

    @Test
    fun languagePackMissingIsShownWithoutAnAction() {
        show(VoiceMessage.LanguagePackMissing)

        composeRule.onNodeWithText(string(R.string.chat_voice_language_pack)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.chat_voice_settings)).assertDoesNotExist()
    }

    @Test
    fun noMatchIsShown() {
        show(VoiceMessage.NoMatch)

        composeRule.onNodeWithText(string(R.string.chat_voice_no_match)).assertIsDisplayed()
    }

    @Test
    fun noMessageShowsNoSnackbar() {
        show(null)

        composeRule.onNodeWithText(string(R.string.chat_voice_no_match)).assertDoesNotExist()
        composeRule.onNodeWithText("Bob").assertIsDisplayed()
    }
}
