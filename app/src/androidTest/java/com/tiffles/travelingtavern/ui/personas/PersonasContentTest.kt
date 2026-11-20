package com.tiffles.travelingtavern.ui.personas

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tiffles.travelingtavern.R
import com.tiffles.travelingtavern.domain.model.PageError
import com.tiffles.travelingtavern.domain.model.Persona
import com.tiffles.travelingtavern.ui.theme.TravelingTavernTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersonasContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun string(id: Int, vararg args: Any) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    // No picture URL: the initial shows instead, and nothing touches the network.
    private val mira = Persona("1-Mira.png", "Mira", "", "A cheerful bard.", null, isActive = true, isDefault = true)
    private val rowan = Persona("2-Rowan.png", "Rowan", "", "Quiet.", null, isActive = false, isDefault = false)
    private val calls = mutableListOf<String>()

    private val actions = PersonasActions(
        onClose = { calls += "close" },
        onPick = { calls += "pick ${it.name}" },
        onMore = { calls += "more ${it.name}" },
        onCreate = { calls += "create" },
        onEdit = { calls += "edit ${it.name}" },
        onToggleDefault = { calls += "default ${it.name}" },
        onDelete = { calls += "delete ${it.name}" },
        onConfirmDelete = { calls += "confirmDelete" },
        onRetry = { calls += "retry" }
    )

    private val state = mutableStateOf<PersonasUiState>(PersonasUiState.Loading)
    private var shown = false

    private fun show(newState: PersonasUiState) {
        state.value = newState
        if (shown) return
        shown = true
        composeRule.setContent { TravelingTavernTheme { PersonasContent(uiState = state.value, actions = actions) } }
    }

    @Test
    fun cardsShowNamesAndBadges() {
        show(PersonasUiState.Success(listOf(mira, rowan), inChat = true))

        composeRule.onNodeWithText("Mira").assertIsDisplayed()
        composeRule.onNodeWithText("Rowan").assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.personas_badge_active)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.personas_badge_default)).assertIsDisplayed()
    }

    @Test
    fun tapsReachTheirCallbacks() {
        show(PersonasUiState.Success(listOf(mira, rowan), inChat = true))

        composeRule.onNodeWithText("Rowan").performClick()
        composeRule.onNodeWithContentDescription(string(R.string.personas_more_options, "Mira")).performClick()
        composeRule.onNodeWithText(string(R.string.personas_create)).performClick()
        composeRule.onNodeWithContentDescription(string(R.string.personas_close)).performClick()

        assertEquals(listOf("pick Rowan", "more Mira", "create", "close"), calls)
    }

    @Test
    fun menuOffersEditDefaultAndDelete() {
        show(PersonasUiState.Success(listOf(mira, rowan), inChat = false, actionsFor = mira))

        composeRule.onNodeWithText(string(R.string.personas_action_edit)).assertIsDisplayed()
        // Mira is already the default.
        composeRule.onNodeWithText(string(R.string.personas_action_remove_default)).performClick()
        composeRule.onNodeWithText(string(R.string.personas_action_delete)).performClick()

        assertEquals(listOf("default Mira", "delete Mira"), calls)
    }

    @Test
    fun menuIsClosedUntilOpened() {
        show(PersonasUiState.Success(listOf(mira, rowan), inChat = false))

        composeRule.onNodeWithText(string(R.string.personas_action_edit)).assertDoesNotExist()
    }

    @Test
    fun deleteAsksFirst() {
        show(PersonasUiState.Success(listOf(mira, rowan), inChat = false, confirmDelete = rowan))

        composeRule.onNodeWithText(string(R.string.personas_delete_title, "Rowan")).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.personas_delete_warning)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.personas_delete_message)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.personas_action_delete)).performClick()

        assertEquals(listOf("confirmDelete"), calls)
    }

    @Test
    fun emptyStateOffersCreate() {
        show(PersonasUiState.Empty)

        composeRule.onNodeWithText(string(R.string.personas_empty)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.personas_create)).performClick()

        assertEquals(listOf("create"), calls)
    }

    @Test
    fun unreachableShowsTheBanner() {
        show(PersonasUiState.Error(PageError.Unreachable))

        composeRule.onNodeWithText("Mira").assertDoesNotExist()
        composeRule.onNodeWithText(string(R.string.personas_create)).assertDoesNotExist()
    }
}
