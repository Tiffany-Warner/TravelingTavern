package com.tiffles.travelingtavern.ui.personas

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
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

@RunWith(AndroidJUnit4::class)
class PersonaEditorContentTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val calls = mutableListOf<String>()
    private val mira = PersonaForm("Mira", "Bard", "Sings.")

    private fun show(state: PersonaEditorUiState) {
        composeRule.setContent {
            TravelingTavernTheme {
                PersonaEditorContent(
                    uiState = state,
                    onBack = { calls += "back" },
                    onFormChange = { calls += "change" },
                    onPickPicture = { calls += "pick" },
                    onSave = { calls += "save" },
                    onRetry = { calls += "retry" }
                )
            }
        }
    }

    private fun saveButton() = composeRule.onNodeWithText(context.getString(R.string.persona_save))

    @Test
    fun saveIsDisabledWithoutAName() {
        show(PersonaEditorUiState.Editing(PersonaForm(), PersonaForm(), isNew = true))

        composeRule.onNodeWithText(context.getString(R.string.persona_new_title)).assertIsDisplayed()
        saveButton().assertIsNotEnabled()
    }

    @Test
    fun saveIsDisabledUntilSomethingChanges() {
        show(PersonaEditorUiState.Editing(mira, mira, isNew = false))

        saveButton().assertIsNotEnabled()
    }

    @Test
    fun saveIsEnabledAfterAChange() {
        show(PersonaEditorUiState.Editing(mira.copy(title = "Singer"), mira, isNew = false))

        saveButton().assertIsEnabled().performClick()

        assertEquals(listOf("save"), calls)
    }

    @Test
    fun tokenCountShowsUnderTheDescription() {
        show(PersonaEditorUiState.Editing(mira, mira, isNew = false, tokenCount = 12))

        composeRule.onNodeWithText(context.resources.getQuantityString(R.plurals.entry_tokens, 12, 12)).assertIsDisplayed()
    }

    @Test
    fun changePictureCallsBack() {
        show(PersonaEditorUiState.Editing(mira, mira, isNew = false))

        composeRule.onNodeWithText(context.getString(R.string.persona_picture)).performClick()

        assertEquals(listOf("pick"), calls)
    }

    @Test
    fun notFoundSaysSo() {
        show(PersonaEditorUiState.NotFound)

        composeRule.onNodeWithText(context.getString(R.string.persona_not_found)).assertIsDisplayed()
    }
}
