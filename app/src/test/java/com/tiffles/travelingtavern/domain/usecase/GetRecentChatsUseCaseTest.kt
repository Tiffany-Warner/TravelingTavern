package com.tiffles.travelingtavern.domain.usecase

import com.tiffles.travelingtavern.domain.FakeCharacterRepository
import com.tiffles.travelingtavern.domain.FakeHiddenChatsRepository
import com.tiffles.travelingtavern.domain.activity
import com.tiffles.travelingtavern.domain.character
import com.tiffles.travelingtavern.domain.model.ChatRef
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class GetRecentChatsUseCaseTest {

    private val repository = FakeCharacterRepository()
    private val hidden = FakeHiddenChatsRepository()
    private val useCase = GetRecentChatsUseCase(repository, hidden)

    @Test
    fun `null until both lists have arrived`() = runTest {
        assertNull(useCase().first())

        repository.characters.value = listOf(character("Bob"))
        assertNull(useCase().first())

        repository.chatActivity.value = emptyList()
        assertEquals(emptyList<Any>(), useCase().first()?.visible)
    }

    @Test
    fun `sorted by last message, newest first`() = runTest {
        repository.characters.value = listOf(character("Bob"), character("Oliver"), character("Seraphina"))
        repository.chatActivity.value = listOf(
            activity("Seraphina.png", "Hello", "2026-11-16T14:04:15Z"),
            activity("Oliver.png", "Hi", "2026-11-17T18:39:38Z"),
            activity("Bob.png", "Test?", "2026-11-17T14:19:57Z")
        )

        val chats = useCase().first()!!.visible

        assertEquals(listOf("Oliver", "Bob", "Seraphina"), chats.map { it.character.name })
        assertEquals("Hi", chats[0].lastMessage)
    }

    @Test
    fun `one row per chat, so a character with two chats has two rows`() = runTest {
        repository.characters.value = listOf(character("Bob"))
        repository.chatActivity.value = listOf(
            activity("Bob.png", "old chat", "2026-11-01T10:00:00Z"),
            activity("Bob.png", "new chat", "2026-11-17T10:00:00Z"),
            activity("Bob.png", "undated", null)
        )

        val chats = useCase().first()!!.visible

        assertEquals(listOf("new chat", "old chat", "undated"), chats.map { it.lastMessage })
        assertEquals(Instant.parse("2026-11-17T10:00:00Z"), chats[0].lastMessageAt)
    }

    @Test
    fun `characters without chats and chats of unknown characters are left out`() = runTest {
        repository.characters.value = listOf(character("Bob"), character("NoChats"))
        repository.chatActivity.value = listOf(
            activity("Bob.png", "Hi", "2026-11-17T10:00:00Z"),
            activity("Deleted.png", "Bye", "2026-11-17T11:00:00Z")
        )

        assertEquals(listOf("Bob"), useCase().first()!!.visible.map { it.character.name })
    }

    @Test
    fun `undated chats go last, then by name`() = runTest {
        repository.characters.value = listOf(character("Zed"), character("Amy"), character("Bob"))
        repository.chatActivity.value = listOf(
            activity("Zed.png", null, null),
            activity("Amy.png", null, null),
            activity("Bob.png", "Hi", "2026-11-17T10:00:00Z")
        )

        assertEquals(listOf("Bob", "Amy", "Zed"), useCase().first()!!.visible.map { it.character.name })
    }

    @Test
    fun `hidden chats are left out and counted`() = runTest {
        repository.characters.value = listOf(character("Bob"))
        repository.chatActivity.value = listOf(
            activity("Bob.png", "old chat", "2026-11-01T10:00:00Z", chatFile = "Bob - 1"),
            activity("Bob.png", "new chat", "2026-11-17T10:00:00Z", chatFile = "Bob - 2")
        )
        hidden.hiddenChats.value = setOf(ChatRef("Bob.png", "Bob - 1"))

        val list = useCase().first()!!

        assertEquals(listOf("new chat"), list.visible.map { it.lastMessage })
        assertEquals(1, list.hiddenCount)
    }

    @Test
    fun `hidden entries for chats that no longer exist hide nothing and don't count`() = runTest {
        repository.characters.value = listOf(character("Bob"))
        repository.chatActivity.value = listOf(activity("Bob.png", "Hi", "2026-11-17T10:00:00Z", chatFile = "Bob - 2"))
        hidden.hiddenChats.value = setOf(ChatRef("Bob.png", "Bob - deleted"), ChatRef("Gone.png", "Gone - 1"))

        val list = useCase().first()!!

        assertEquals(1, list.visible.size)
        assertEquals(0, list.hiddenCount)
    }
}
