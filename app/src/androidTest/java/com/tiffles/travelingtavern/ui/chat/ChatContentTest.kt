package com.tiffles.travelingtavern.ui.chat

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tiffles.travelingtavern.R
import com.tiffles.travelingtavern.domain.model.CharacterSummary
import com.tiffles.travelingtavern.domain.model.MessageAction
import com.tiffles.travelingtavern.domain.model.Persona
import com.tiffles.travelingtavern.domain.model.TappedMessage
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
    private val bob = CharacterSummary("Bob.png", "Bob", avatarUrl = "", portraitUrl = "", lastChatAt = null)
    private val calls = mutableListOf<String>()
    private val actionsRun = mutableListOf<MessageAction>()

    private val actions = ChatActions(
        onSend = { calls += "send" },
        onWriteForMe = { calls += "writeForMe" },
        onStop = { calls += "stop" },
        onMessageAction = { actionsRun += it },
        onStartNewChat = { calls += "startNewChat" },
        onRestartChat = { calls += "restartChat" },
        onConfirm = { calls += "confirm" },
        onPersonaClick = { calls += "persona" }
    )

    private val state = mutableStateOf<ChatUiState>(ChatUiState.Loading())
    private val input = mutableStateOf("")
    private var shown = false

    /** Shows [newState]; later calls in the same test just change the state. */
    private fun show(newState: ChatUiState.Ready, text: String = "") {
        state.value = newState
        input.value = text
        if (shown) return
        shown = true
        composeRule.setContent { TravelingTavernTheme { ChatContent(uiState = state.value, input = input.value, actions = actions) } }
    }

    private fun message(isUser: Boolean, isLatest: Boolean) = TappedMessage(
        messageId = 3,
        sendDate = "2026-11-19T15:04:00.000Z",
        senderName = if (isUser) "User" else "Bob",
        isUser = isUser,
        isLatest = isLatest,
        isHidden = false,
        canRegenerate = isLatest,
        canContinue = isLatest && !isUser
    )

    private fun sheet(message: TappedMessage, enabled: (MessageAction) -> Boolean = { true }): MessageSheet {
        val list = buildList {
            if (message.canRegenerate) add(MessageAction.Regenerate)
            if (message.canContinue) add(MessageAction.Continue)
            addAll(listOf(MessageAction.Copy, MessageAction.Edit, MessageAction.Branch, MessageAction.Hide, MessageAction.Delete))
        }
        return MessageSheet(message, list, list.filter(enabled).toSet())
    }

    // -- Input bar

    @Test
    fun sendIsDisabledWhenEmptyAndSendsWithText() {
        show(ChatUiState.Ready(bob))
        composeRule.onNodeWithContentDescription(string(R.string.chat_send)).assertIsNotEnabled()

        show(ChatUiState.Ready(bob), text = "Hi")
        composeRule.onNodeWithContentDescription(string(R.string.chat_send)).assertIsEnabled().performClick()
        assertEquals(listOf("send"), calls)
    }

    @Test
    fun writeForMeCallsBackAndThereIsNoMic() {
        show(ChatUiState.Ready(bob))
        composeRule.onNodeWithContentDescription(string(R.string.chat_write_for_me)).performClick()
        assertEquals(listOf("writeForMe"), calls)
        composeRule.onNodeWithContentDescription("Voice input").assertDoesNotExist()
    }

    @Test
    fun whileGeneratingStopReplacesSendAndWriteForMeIsDisabled() {
        show(ChatUiState.Ready(bob, generating = true), text = "Hi")
        composeRule.onNodeWithContentDescription(string(R.string.chat_send)).assertDoesNotExist()
        composeRule.onNodeWithContentDescription(string(R.string.chat_write_for_me)).assertIsNotEnabled()
        composeRule.onNodeWithContentDescription(string(R.string.chat_stop)).performClick()
        assertEquals(listOf("stop"), calls)
    }

    // -- Message actions

    @Test
    fun latestCharacterMessageShowsRegenerateAndContinue() {
        show(ChatUiState.Ready(bob, sheet = sheet(message(isUser = false, isLatest = true))))
        composeRule.onNodeWithText(string(R.string.chat_action_regenerate)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.chat_action_continue)).assertIsDisplayed()
        composeRule.onNodeWithText("Sent by Bob").assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.chat_action_branch)).performClick()
        assertEquals(listOf(MessageAction.Branch), actionsRun)
    }

    @Test
    fun latestUserMessageShowsRegenerateButNotContinue() {
        show(ChatUiState.Ready(bob, sheet = sheet(message(isUser = true, isLatest = true))))
        composeRule.onNodeWithText(string(R.string.chat_action_regenerate)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.chat_action_continue)).assertDoesNotExist()
    }

    @Test
    fun whileGeneratingOnlyCopyCanBeTapped() {
        val sheet = sheet(message(isUser = false, isLatest = false)) { it == MessageAction.Copy }
        show(ChatUiState.Ready(bob, generating = true, sheet = sheet))
        composeRule.onNodeWithText(string(R.string.chat_action_edit)).performClick()
        composeRule.onNodeWithText(string(R.string.chat_action_copy)).performClick()
        assertEquals(listOf(MessageAction.Copy), actionsRun)
    }

    @Test
    fun deleteConfirmationCallsBackOnlyWhenConfirmed() {
        show(ChatUiState.Ready(bob, confirmation = ChatConfirmation.DeleteMessage(message(isUser = false, isLatest = true))))
        composeRule.onNodeWithText(string(R.string.chat_delete_message_title)).assertIsDisplayed()
        assertEquals(emptyList<String>(), calls)
        composeRule.onNodeWithText(string(R.string.chat_action_delete)).performClick()
        assertEquals(listOf("confirm"), calls)
    }

    // -- ⋮ menu

    @Test
    fun menuItemsCallBackAndRestartAsksWithItsOwnButton() {
        show(ChatUiState.Ready(bob))
        composeRule.onNodeWithContentDescription(string(R.string.chat_more)).performClick()
        composeRule.onNodeWithText(string(R.string.chat_restart)).performClick()
        assertEquals(listOf("restartChat"), calls)

        show(ChatUiState.Ready(bob, confirmation = ChatConfirmation.RestartChat))
        composeRule.onNodeWithText(string(R.string.chat_restart_title)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.chat_restart_action)).performClick()
        assertEquals(listOf("restartChat", "confirm"), calls)
    }

    @Test
    fun menuItemsAreDisabledWhileGenerating() {
        show(ChatUiState.Ready(bob, generating = true))
        composeRule.onNodeWithContentDescription(string(R.string.chat_more)).performClick()
        composeRule.onNodeWithText(string(R.string.chat_start_new)).assertIsNotEnabled()
        composeRule.onNodeWithText(string(R.string.chat_restart)).assertIsNotEnabled()
    }

    @Test
    fun personaChipShowsTheNameAndOpensPersonas() {
        val mira = Persona("1-Mira.png", "Mira", "", "", null, isActive = true, isDefault = false)
        show(ChatUiState.Ready(bob, persona = mira))

        composeRule.onNodeWithText(context.getString(R.string.personas_chip, "Mira")).assertIsDisplayed().performClick()

        assertEquals(listOf("persona"), calls)
    }

    @Test
    fun noPersonaChipUntilThePersonaIsKnown() {
        show(ChatUiState.Ready(bob))

        composeRule.onNodeWithText(context.getString(R.string.personas_chip, "Mira")).assertDoesNotExist()
    }

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
}
