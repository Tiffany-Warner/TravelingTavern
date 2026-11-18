package com.tiffles.travelingtavern.domain.usecase

import com.tiffles.travelingtavern.domain.FakeCharacterRepository
import com.tiffles.travelingtavern.domain.activity
import com.tiffles.travelingtavern.domain.character
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class GetRecentChatsUseCaseTest {

    private val repository = FakeCharacterRepository()
    private val useCase = GetRecentChatsUseCase(repository)

    @Test
    fun `null until both lists have arrived`() = runTest {
        assertNull(useCase().first())

        repository.characters.value = listOf(character("Bob"))
        assertNull(useCase().first())

        repository.chatActivity.value = emptyList()
        assertEquals(emptyList<Any>(), useCase().first())
    }

    @Test
    fun `sorted by last message, newest first`() = runTest {
        repository.characters.value = listOf(character("Bob"), character("Oliver"), character("Seraphina"))
        repository.chatActivity.value = listOf(
            activity("Seraphina.png", "Hello", "2026-11-16T14:04:15Z"),
            activity("Oliver.png", "Hi", "2026-11-17T18:39:38Z"),
            activity("Bob.png", "Test?", "2026-11-17T14:19:57Z")
        )

        val chats = useCase().first()!!

        assertEquals(listOf("Oliver", "Bob", "Seraphina"), chats.map { it.character.name })
        assertEquals("Hi", chats[0].lastMessage)
    }

    @Test
    fun `one row per character, from its newest chat`() = runTest {
        repository.characters.value = listOf(character("Bob"))
        repository.chatActivity.value = listOf(
            activity("Bob.png", "old chat", "2026-11-01T10:00:00Z"),
            activity("Bob.png", "new chat", "2026-11-17T10:00:00Z"),
            activity("Bob.png", "undated", null)
        )

        val chats = useCase().first()!!

        assertEquals(1, chats.size)
        assertEquals("new chat", chats[0].lastMessage)
        assertEquals(Instant.parse("2026-11-17T10:00:00Z"), chats[0].lastMessageAt)
    }

    @Test
    fun `characters without chats and chats of unknown characters are left out`() = runTest {
        repository.characters.value = listOf(character("Bob"), character("NoChats"))
        repository.chatActivity.value = listOf(
            activity("Bob.png", "Hi", "2026-11-17T10:00:00Z"),
            activity("Deleted.png", "Bye", "2026-11-17T11:00:00Z")
        )

        assertEquals(listOf("Bob"), useCase().first()!!.map { it.character.name })
    }

    @Test
    fun `undated chats go last, then by name`() = runTest {
        repository.characters.value = listOf(character("Zed"), character("Amy"), character("Bob"))
        repository.chatActivity.value = listOf(
            activity("Zed.png", null, null),
            activity("Amy.png", null, null),
            activity("Bob.png", "Hi", "2026-11-17T10:00:00Z")
        )

        assertEquals(listOf("Bob", "Amy", "Zed"), useCase().first()!!.map { it.character.name })
    }
}
