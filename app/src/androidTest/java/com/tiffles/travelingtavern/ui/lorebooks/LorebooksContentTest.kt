package com.tiffles.travelingtavern.ui.lorebooks

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tiffles.travelingtavern.R
import com.tiffles.travelingtavern.domain.model.CharacterSummary
import com.tiffles.travelingtavern.domain.model.Lorebook
import com.tiffles.travelingtavern.domain.model.LorebookCard
import com.tiffles.travelingtavern.domain.model.LorebookFilter
import com.tiffles.travelingtavern.ui.theme.TravelingTavernTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LorebooksContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun string(id: Int, vararg args: Any) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    /** Filter chips report the Checkbox role; a card's status line with the same text doesn't. */
    private val isChip = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox)

    private fun chip(labelRes: Int) = composeRule.onNode(hasText(string(labelRes)) and isChip)

    /** Text on a card, not a chip with the same label ("Global" is both). */
    private fun cardText(text: String) = composeRule.onNode(hasText(text) and !isChip)

    // No image URLs: the initial shows instead, and nothing touches the network.
    private fun character(name: String) = CharacterSummary("$name.png", name, "", "", null)

    private val seraphina = character("Seraphina")
    private val bob = character("Bob")
    private val oliver = character("Oliver")

    private fun card(name: String, global: Boolean = false, characters: List<CharacterSummary> = emptyList(), persona: Boolean = false) =
        LorebookCard(
            lorebook = Lorebook(name, 4, global, characters.map { it.id }, if (persona) listOf("Me") else emptyList()),
            characters = characters
        )

    private val cards = listOf(
        card("Eldoria", characters = listOf(seraphina)),
        card("Kingdoms", characters = listOf(bob, oliver, seraphina)),
        card("Rules", global = true, characters = listOf(bob)),
        card("About me", persona = true),
        card("Old notes")
    )

    /** Shows the list with a real filter state, so chips and the sheet act like in the app. */
    private fun show(cardsShown: List<LorebookCard> = cards, onFilter: (LorebookFilter) -> Unit = {}, onClearFilters: () -> Unit = {}, onClick: (String) -> Unit = {}) {
        composeRule.setContent {
            var filter by remember { mutableStateOf<LorebookFilter>(LorebookFilter.All) }
            val selected = (filter as? LorebookFilter.Character)?.let { f -> listOf(seraphina, bob, oliver).first { it.id == f.characterId } }
            TravelingTavernTheme {
                LorebooksContent(
                    uiState = LorebooksUiState.Success(cardsShown, filter, listOf(bob, oliver, seraphina), selected),
                    query = "",
                    isRefreshing = false,
                    callbacks = LorebooksCallbacks(
                        onFilterChange = {
                            filter = it
                            onFilter(it)
                        },
                        onClearFilters = onClearFilters,
                        onLorebookClick = onClick
                    )
                )
            }
        }
    }

    @Test
    fun eachStatusLineVariantRenders() {
        show()
        composeRule.onNodeWithText("Seraphina").assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.lorebooks_status_more, "Bob", 2)).assertIsDisplayed()
        cardText(string(R.string.lorebooks_status_global)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.lorebooks_status_persona)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.lorebooks_status_not_active)).assertIsDisplayed()
    }

    @Test
    fun chipsSelectAndReportTheFilter() {
        val chosen = mutableListOf<LorebookFilter>()
        show(onFilter = { chosen += it })

        chip(R.string.lorebooks_filter_unused).performClick()
        chip(R.string.lorebooks_filter_unused).assertIsSelected()
        chip(R.string.lorebooks_filter_global).performClick()

        assertEquals(listOf(LorebookFilter.Unused, LorebookFilter.Global), chosen)
    }

    @Test
    fun characterSheetAppliesTheFilterAndTheCrossClearsIt() {
        val chosen = mutableListOf<LorebookFilter>()
        show(onFilter = { chosen += it })

        composeRule.onNodeWithText(string(R.string.lorebooks_filter_character)).performClick()
        composeRule.onNodeWithText(string(R.string.lorebooks_pick_character)).assertIsDisplayed()
        composeRule.onNodeWithText("Oliver").performClick()
        composeRule.onNodeWithContentDescription(string(R.string.lorebooks_clear_character)).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(string(R.string.lorebooks_clear_character)).performClick()

        assertEquals(listOf(LorebookFilter.Character(oliver.id), LorebookFilter.All), chosen)
        composeRule.onNodeWithText(string(R.string.lorebooks_filter_character)).assertIsDisplayed()
    }

    @Test
    fun anEmptyFilterResultOffersClearFilters() {
        var cleared = 0
        show(cardsShown = emptyList(), onClearFilters = { cleared++ })

        composeRule.onNodeWithText(string(R.string.lorebooks_no_matches)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.lorebooks_clear_filters)).performClick()

        assertEquals(1, cleared)
    }

    @Test
    fun tappingACardOpensThatLorebook() {
        val opened = mutableListOf<String>()
        show(onClick = { opened += it })

        composeRule.onNodeWithText("Old notes").performClick()

        assertEquals(listOf("Old notes"), opened)
    }

    @Test
    fun theCharacterChipIsHiddenWhenNoCharacterHasALorebook() {
        composeRule.setContent {
            TravelingTavernTheme {
                LorebooksContent(
                    uiState = LorebooksUiState.Success(listOf(card("Old notes")), LorebookFilter.All, emptyList(), null),
                    query = "",
                    isRefreshing = false,
                    callbacks = LorebooksCallbacks()
                )
            }
        }
        composeRule.onNodeWithText(string(R.string.lorebooks_filter_character)).assertDoesNotExist()
    }
}
