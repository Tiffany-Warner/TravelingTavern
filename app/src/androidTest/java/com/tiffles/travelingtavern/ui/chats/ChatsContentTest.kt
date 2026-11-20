package com.tiffles.travelingtavern.ui.chats

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tiffles.travelingtavern.R
import com.tiffles.travelingtavern.domain.model.CharacterSummary
import com.tiffles.travelingtavern.domain.model.PageError
import com.tiffles.travelingtavern.domain.model.RecentChat
import com.tiffles.travelingtavern.ui.chats.components.ChatOptionsContent
import com.tiffles.travelingtavern.ui.theme.TravelingTavernTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class ChatsContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val now = Instant.parse("2026-11-17T18:00:00Z")
    private fun string(id: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(id)

    private fun chat(name: String, message: String?, ago: Duration) = RecentChat(
        // No image URLs: the initial shows instead, and nothing touches the network.
        character = CharacterSummary("$name.png", name, avatarUrl = "", portraitUrl = "", lastChatAt = null),
        lastMessage = message,
        lastMessageAt = now.minus(ago),
        chatFile = "$name - 1"
    )

    private fun show(
        state: ChatsUiState,
        onChatClick: (String, String?) -> Unit = { _, _ -> },
        onBrowseCharacters: () -> Unit = {},
        onRetry: () -> Unit = {},
        onOpenServerSettings: () -> Unit = {},
        options: ChatOptionsState = ChatOptionsState(),
        onChatLongPress: (RecentChat) -> Unit = {},
        onShowHiddenChats: () -> Unit = {}
    ) {
        composeRule.setContent {
            TravelingTavernTheme {
                ChatsContent(
                    uiState = state, isRefreshing = false, now = now,
                    onRefresh = {}, onChatClick = onChatClick, onBrowseCharacters = onBrowseCharacters,
                    onRetry = onRetry, onOpenServerSettings = onOpenServerSettings,
                    zone = ZoneId.of("UTC"), options = options,
                    onChatLongPress = onChatLongPress, onShowHiddenChats = onShowHiddenChats
                )
            }
        }
    }

    @Test
    fun rowsShowNamePreviewAndTime_andTapOpensThatChat() {
        var opened: String? = null
        show(
            ChatsUiState.Success(
                listOf(
                    chat("Seraphina", "*She smiles.*\nWelcome back.", Duration.ofMinutes(5)),
                    chat("Bob", "Test?", Duration.ofHours(26))
                )
            ),
            onChatClick = { id, _ -> opened = id }
        )

        composeRule.onNodeWithText("Seraphina").assertIsDisplayed()
        composeRule.onNodeWithText("She smiles. Welcome back.").assertIsDisplayed()
        composeRule.onNodeWithText("5m").assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.time_yesterday)).assertIsDisplayed()

        composeRule.onNodeWithText("Bob").performClick()
        assertEquals("Bob.png", opened)
    }

    @Test
    fun emptyChatHasNoPreview() {
        show(ChatsUiState.Success(listOf(chat("Oliver", null, Duration.ofMinutes(2)))))

        composeRule.onNodeWithText("Oliver").assertIsDisplayed()
        composeRule.onNodeWithText("2m").assertIsDisplayed()
    }

    @Test
    fun emptyStateOffersCharacters() {
        var browsed = false
        show(ChatsUiState.Empty, onBrowseCharacters = { browsed = true })

        composeRule.onNodeWithText(string(R.string.chats_empty)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.chats_browse_characters)).performClick()
        assertEquals(true, browsed)
    }

    @Test
    fun unreachableBannerRetriesOrOpensServerSettings() {
        var retried = false
        var settingsOpened = false
        show(
            ChatsUiState.Error(PageError.Unreachable),
            onRetry = { retried = true },
            onOpenServerSettings = { settingsOpened = true }
        )

        composeRule.onNodeWithText(string(R.string.banner_unreachable)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.webview_retry)).performClick()
        assertEquals(true, retried)
        // A wrong saved address (e.g. the server's IP changed) can only be fixed from here.
        composeRule.onNodeWithText(string(R.string.webview_server_settings)).performClick()
        assertEquals(true, settingsOpened)
    }

    @Test
    fun loginBannerOpensServerSettings() {
        var opened = false
        show(ChatsUiState.Error(PageError.AuthFailed), onOpenServerSettings = { opened = true })

        composeRule.onNodeWithText(string(R.string.banner_auth_failed)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.webview_server_settings)).performClick()
        assertEquals(true, opened)
    }

    @Test
    fun longPressAsksForThatChatsOptions_andTapStillOpensIt() {
        var pressed: RecentChat? = null
        var opened: String? = null
        val bob = chat("Bob", "Test?", Duration.ofHours(1))
        show(ChatsUiState.Success(listOf(bob)), onChatClick = { id, _ -> opened = id }, onChatLongPress = { pressed = it })

        composeRule.onNodeWithText("Bob").performTouchInput { longClick() }
        assertEquals(bob, pressed)
        assertEquals(null, opened)

        composeRule.onNodeWithText("Bob").performClick()
        assertEquals("Bob.png", opened)
    }

    @Test
    fun menuOnlyWithHiddenChats() {
        show(ChatsUiState.Success(listOf(chat("Bob", "Hi", Duration.ofHours(1)))))

        composeRule.onNodeWithContentDescription(string(R.string.chats_more)).assertDoesNotExist()
    }

    @Test
    fun menuShowsHiddenChats() {
        var shown = false
        show(ChatsUiState.Empty, options = ChatOptionsState(hasHiddenChats = true), onShowHiddenChats = { shown = true })

        composeRule.onNodeWithContentDescription(string(R.string.chats_more)).performClick()
        composeRule.onNodeWithText(string(R.string.chats_show_hidden)).performClick()
        assertTrue(shown)
    }

    private fun showOptions(canDelete: Boolean, onHide: () -> Unit = {}, onDelete: () -> Unit = {}) {
        composeRule.setContent {
            TravelingTavernTheme {
                ChatOptionsContent(
                    chat = chat("Seraphina", "Hi", Duration.ofMinutes(5)), now = now, zone = ZoneId.of("UTC"),
                    canDelete = canDelete, onHide = onHide, onDelete = onDelete
                )
            }
        }
    }

    @Test
    fun optionsSheetShowsTheChat_andHideAndDeleteCallBack() {
        var hidden = false
        var deleted = false
        showOptions(canDelete = true, onHide = { hidden = true }, onDelete = { deleted = true })

        composeRule.onNodeWithText("Seraphina").assertIsDisplayed()
        composeRule.onNodeWithText("5m").assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.chats_option_hide)).performClick()
        assertTrue(hidden)
        composeRule.onNodeWithText(string(R.string.chats_option_delete)).performClick()
        assertTrue(deleted)
    }

    @Test
    fun deleteDisabledWhileThatChatGenerates() {
        var deleted = false
        showOptions(canDelete = false, onDelete = { deleted = true })

        composeRule.onNode(hasClickAction() and hasText(string(R.string.chats_option_delete))).assertIsNotEnabled()
        composeRule.onNode(hasClickAction() and hasText(string(R.string.chats_option_hide))).assertIsEnabled()
        composeRule.onNodeWithText(string(R.string.chats_option_delete)).performClick()
        assertFalse(deleted)
    }

    @Test
    fun deleteConfirmationCancelsOrDeletes() {
        var confirmed = false
        var dismissed = false
        composeRule.setContent {
            TravelingTavernTheme {
                ChatsDialogs(
                    state = ChatOptionsState(confirmDeleteFor = chat("Bob", "Hi", Duration.ofHours(1))),
                    now = now, onHide = {}, onDelete = {}, onDismissOptions = {},
                    onConfirmDelete = { confirmed = true }, onDismissDelete = { dismissed = true }
                )
            }
        }

        composeRule.onNodeWithText(string(R.string.chats_delete_title)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.lorebook_cancel)).performClick()
        assertTrue(dismissed)
        composeRule.onNodeWithText(string(R.string.lorebook_delete_action)).performClick()
        assertTrue(confirmed)
    }
}
