package com.tiffles.travelingtavern.data.repository

import android.util.Log
import com.tiffles.travelingtavern.data.bridge.BridgeClient
import com.tiffles.travelingtavern.data.bridge.BridgeRepository
import com.tiffles.travelingtavern.data.bridge.LeaseStateStore
import com.tiffles.travelingtavern.data.bridge.PersonaInput
import com.tiffles.travelingtavern.data.media.PictureEncoder
import com.tiffles.travelingtavern.data.webview.WebViewHolder
import com.tiffles.travelingtavern.domain.model.PersonaChanges
import com.tiffles.travelingtavern.domain.repository.PersonaException
import com.tiffles.travelingtavern.domain.repository.PersonaFailure
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PersonaRepositoryImplTest {

    private val bridge = BridgeRepository(LeaseStateStore())
    private val client: BridgeClient = mockk(relaxed = true)
    private val encodedPictures = mutableMapOf("content://ok" to "data:image/jpeg;base64,AA==")
    private val repository = PersonaRepositoryImpl(
        bridge,
        client,
        mockk<WebViewHolder>(relaxed = true),
        PictureEncoder { uri -> encodedPictures[uri] }
    )

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        bridge.onRawMessage("""{"v":1,"type":"ready"}""")
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    private fun answer(json: String) = bridge.onRawMessage(json)

    private fun ok(requestId: String, extra: String = "") =
        answer("""{"v":1,"type":"personaResult","requestId":"$requestId","ok":true$extra}""")

    private fun failureOf(result: Result<*>) = (result.exceptionOrNull() as PersonaException).failure

    @Test
    fun `the list comes from the personas message`() = runTest {
        answer("""{"v":1,"type":"personas","items":[{"avatarId":"a.png","name":"A","isActive":true}]}""")

        val personas = repository.personas.first()!!

        assertEquals("A", personas.single().name)
        assertTrue(personas.single().isActive)
    }

    @Test
    fun `select passes the lock flag, with and without`() = runTest {
        coEvery { client.selectPersona(any(), any(), any()) } coAnswers { ok(firstArg()) }

        assertTrue(repository.select("a.png", lockToChat = true).isSuccess)
        assertTrue(repository.select("a.png", lockToChat = false).isSuccess)

        coVerify { client.selectPersona(any(), "a.png", true) }
        coVerify { client.selectPersona(any(), "a.png", false) }
    }

    @Test
    fun `answers are matched by request ID`() = runTest {
        coEvery { client.requestPersona(any(), "a.png") } coAnswers {
            answer("""{"v":1,"type":"personaResult","requestId":"other","ok":true,"persona":{"avatarId":"x.png","name":"X"}}""")
            ok(firstArg(), ""","persona":{"avatarId":"a.png","name":"A","description":"Full text"}""")
        }

        val persona = repository.details("a.png").getOrThrow()

        assertEquals("A", persona.name)
        assertEquals("Full text", persona.description)
    }

    @Test
    fun `create encodes the picture and returns the new id`() = runTest {
        coEvery { client.createPersona(any(), any()) } coAnswers { ok(firstArg(), ""","avatarId":"123-Ash.png"""") }

        val id = repository.create(PersonaChanges(name = "Ash", title = "", description = "", pictureUri = "content://ok"))

        assertEquals("123-Ash.png", id.getOrThrow())
        coVerify { client.createPersona(any(), PersonaInput("Ash", "", "", "data:image/jpeg;base64,AA==")) }
    }

    @Test
    fun `an unreadable picture fails before asking the page`() = runTest {
        val result = repository.save("a.png", PersonaChanges(pictureUri = "content://broken"))

        assertEquals(PersonaFailure.PictureUnreadable, failureOf(result))
        coVerify(exactly = 0) { client.savePersona(any(), any(), any()) }
    }

    @Test
    fun `save sends only the given fields`() = runTest {
        coEvery { client.savePersona(any(), any(), any()) } coAnswers { ok(firstArg()) }

        assertTrue(repository.save("a.png", PersonaChanges(description = "New")).isSuccess)

        coVerify { client.savePersona(any(), "a.png", PersonaInput(description = "New")) }
    }

    @Test
    fun `delete and default map bridge errors`() = runTest {
        coEvery { client.deletePersona(any(), any()) } coAnswers {
            answer("""{"v":1,"type":"personaResult","requestId":"${firstArg<String>()}","ok":false,"error":"notFound"}""")
        }
        coEvery { client.setDefaultPersona(any(), any(), any()) } coAnswers {
            answer("""{"v":1,"type":"personaResult","requestId":"${firstArg<String>()}","ok":false,"error":"failed"}""")
        }

        assertEquals(PersonaFailure.NotFound, failureOf(repository.delete("gone.png")))
        assertEquals(PersonaFailure.Failed, failureOf(repository.setDefault("a.png", true)))
    }

    @Test
    fun `no answer in time is Failed`() = runTest {
        val result = async { repository.delete("a.png") }

        advanceTimeBy(PersonaRepositoryImpl.ACTION_TIMEOUT_MS + 1)

        assertEquals(PersonaFailure.Failed, failureOf(result.await()))
    }
}
