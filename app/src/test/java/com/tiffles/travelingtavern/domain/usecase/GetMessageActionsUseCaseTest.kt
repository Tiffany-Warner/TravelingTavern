package com.tiffles.travelingtavern.domain.usecase

import com.tiffles.travelingtavern.domain.model.MessageAction
import com.tiffles.travelingtavern.domain.tappedMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GetMessageActionsUseCaseTest {

    private val getActions = GetMessageActionsUseCase()

    @Test
    fun `latest character message gets every action`() {
        assertEquals(
            listOf(
                MessageAction.Regenerate, MessageAction.Continue, MessageAction.Copy, MessageAction.Edit,
                MessageAction.Branch, MessageAction.Hide, MessageAction.Delete
            ),
            getActions(tappedMessage())
        )
    }

    @Test
    fun `latest user message gets Regenerate but not Continue`() {
        val actions = getActions(tappedMessage(isUser = true))
        assertTrue(MessageAction.Regenerate in actions)
        assertFalse(MessageAction.Continue in actions)
    }

    @Test
    fun `an untouched greeting gets no Regenerate`() {
        val actions = getActions(tappedMessage(id = 0, canRegenerate = false))
        assertFalse(MessageAction.Regenerate in actions)
        assertTrue(MessageAction.Continue in actions)
    }

    @Test
    fun `older messages get neither, a hidden one offers Show instead of Hide`() {
        val older = getActions(tappedMessage(isLatest = false))
        assertEquals(
            listOf(MessageAction.Copy, MessageAction.Edit, MessageAction.Branch, MessageAction.Hide, MessageAction.Delete),
            older
        )
        val hidden = getActions(tappedMessage(isLatest = false, isHidden = true))
        assertTrue(MessageAction.Show in hidden)
        assertFalse(MessageAction.Hide in hidden)
    }

    @Test
    fun `only Copy is enabled while generating`() {
        MessageAction.entries.forEach { action ->
            assertTrue(getActions.isEnabled(action, generating = false))
            assertEquals(action == MessageAction.Copy, getActions.isEnabled(action, generating = true))
        }
    }
}
