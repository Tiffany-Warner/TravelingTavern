package com.tiffles.travelingtavern.ui.characters

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tiffles.travelingtavern.R
import com.tiffles.travelingtavern.domain.model.CharacterSummary
import com.tiffles.travelingtavern.domain.model.PageError
import com.tiffles.travelingtavern.ui.theme.TravelingTavernTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CharactersContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun string(id: Int, vararg args: Any) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    // No image URLs: the initial shows instead, and nothing touches the network.
    private fun character(name: String) = CharacterSummary("$name.png", name, "", "", null)

    private fun show(
        state: CharactersUiState,
        initialQuery: String = "",
        onQueryChange: (String) -> Unit = {},
        onCharacterClick: (String) -> Unit = {},
        onOpenAdvanced: () -> Unit = {},
        onRetry: () -> Unit = {}
    ) {
        composeRule.setContent {
            var query by remember { mutableStateOf(initialQuery) }
            TravelingTavernTheme {
                CharactersContent(
                    uiState = state,
                    query = query,
                    onQueryChange = {
                        query = it
                        onQueryChange(it)
                    },
                    onCharacterClick = onCharacterClick,
                    onOpenAdvanced = onOpenAdvanced,
                    onRetry = onRetry,
                    onOpenServerSettings = {}
                )
            }
        }
    }

    @Test
    fun gridShowsNames_andTapOpensThatCharacter() {
        var opened: String? = null
        show(CharactersUiState.Success(listOf(character("Bob"), character("Seraphina")), ""), onCharacterClick = { opened = it })

        composeRule.onNodeWithText("Bob").assertIsDisplayed()
        composeRule.onNodeWithText("Seraphina").performClick()
        assertEquals("Seraphina.png", opened)
    }

    @Test
    fun searchOpensAField_typingReportsTheQuery_closingClearsIt() {
        val queries = mutableListOf<String>()
        show(CharactersUiState.Success(listOf(character("Bob")), ""), onQueryChange = { queries += it })

        composeRule.onNodeWithContentDescription(string(R.string.characters_search)).performClick()
        composeRule.onNodeWithText(string(R.string.characters_search)).performTextInput("bo")
        composeRule.onNodeWithContentDescription(string(R.string.characters_close_search)).performClick()

        assertEquals(listOf("bo", ""), queries)
        composeRule.onNodeWithText(string(R.string.tab_characters)).assertIsDisplayed()
    }

    @Test
    fun noMatchesSaysSo() {
        show(CharactersUiState.Success(emptyList(), "zed"), initialQuery = "zed")

        composeRule.onNodeWithText(string(R.string.characters_no_matches, "zed")).assertIsDisplayed()
    }

    @Test
    fun emptyLibraryOffersFullSillyTavern() {
        var opened = false
        show(CharactersUiState.Empty, onOpenAdvanced = { opened = true })

        composeRule.onNodeWithText(string(R.string.characters_empty)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.characters_open_full)).performClick()
        assertEquals(true, opened)
    }

    @Test
    fun fabOpensCreateAndImportAsComingSoon() {
        show(CharactersUiState.Success(listOf(character("Bob")), ""))

        composeRule.onNodeWithContentDescription(string(R.string.characters_add)).performClick()

        composeRule.onNodeWithText(string(R.string.characters_create)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.characters_import)).assertIsDisplayed()
    }

    @Test
    fun errorBannerRetries() {
        var retried = false
        show(CharactersUiState.Error(PageError.Unreachable), onRetry = { retried = true })

        composeRule.onNodeWithText(string(R.string.webview_retry)).performClick()
        assertEquals(true, retried)
    }
}
