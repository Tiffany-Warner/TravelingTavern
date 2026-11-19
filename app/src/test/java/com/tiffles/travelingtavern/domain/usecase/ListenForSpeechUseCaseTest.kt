package com.tiffles.travelingtavern.domain.usecase

import com.tiffles.travelingtavern.domain.FakeSpeechInputRepository
import com.tiffles.travelingtavern.domain.model.SpeechError
import com.tiffles.travelingtavern.domain.model.SpeechEvent.Ended
import com.tiffles.travelingtavern.domain.model.SpeechEvent.Error
import com.tiffles.travelingtavern.domain.model.SpeechEvent.Final
import com.tiffles.travelingtavern.domain.model.SpeechEvent.Listening
import com.tiffles.travelingtavern.domain.model.SpeechEvent.Partial
import com.tiffles.travelingtavern.domain.model.VoiceSessionEvent
import com.tiffles.travelingtavern.domain.model.VoiceSessionEvent.Failed
import com.tiffles.travelingtavern.domain.model.VoiceSessionEvent.Text
import com.tiffles.travelingtavern.domain.step
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ListenForSpeechUseCaseTest {

    private class Session(val events: List<VoiceSessionEvent>, val endedAtMs: Long)

    /** Runs one whole session on virtual time; [Session.endedAtMs] is when it finished. */
    private fun TestScope.listen(speech: FakeSpeechInputRepository): Session {
        val events = mutableListOf<VoiceSessionEvent>()
        val job = launch { ListenForSpeechUseCase(speech)().collect { events += it } }
        advanceUntilIdle()
        assertTrue("session didn't end", job.isCompleted)
        return Session(events, currentTime)
    }

    @Test
    fun `a 2 s pause keeps listening and joins both parts`() = runTest {
        val speech = FakeSpeechInputRepository(
            passes = listOf(
                listOf(step(0, Listening), step(500, Partial("hello")), step(500, Final("hello there"))),
                // The recognizer ended the first pass on its own; the next word comes 2 s later.
                listOf(step(0, Listening), step(2_000, Partial("how")), step(500, Final("how are you")))
            )
        )

        val session = listen(speech)

        assertEquals(
            listOf(Text("hello"), Text("hello there"), Text("hello there how"), Text("hello there how are you")),
            session.events
        )
        // Last word at 3.5 s, then the 4 s tolerance.
        assertEquals(7_500L, session.endedAtMs)
        assertEquals(3, speech.listenCalls)
    }

    @Test
    fun `a 5 s pause ends the session`() = runTest {
        val speech = FakeSpeechInputRepository(
            passes = listOf(
                listOf(step(0, Listening), step(1_000, Final("hello"))),
                listOf(step(0, Listening), step(5_000, Partial("too late")))
            )
        )

        val session = listen(speech)

        assertEquals(listOf<VoiceSessionEvent>(Text("hello")), session.events)
        assertEquals(5_000L, session.endedAtMs)
    }

    @Test
    fun `NoMatch on a restart pass is not an error`() = runTest {
        val speech = FakeSpeechInputRepository(
            passes = listOf(
                listOf(step(0, Listening), step(1_000, Final("hi"))),
                listOf(step(0, Listening), step(1_500, Error(SpeechError.NoMatch))),
                listOf(step(0, Listening), step(1_000, Final("again")))
            )
        )

        val session = listen(speech)

        assertEquals(listOf<VoiceSessionEvent>(Text("hi"), Text("hi again")), session.events)
    }

    @Test
    fun `a session that recognized nothing ends with NoMatch after the tolerance`() = runTest {
        val speech = FakeSpeechInputRepository(
            passes = listOf(listOf(step(0, Listening), step(2_000, Ended)))
        )

        val session = listen(speech)

        assertEquals(listOf<VoiceSessionEvent>(Failed(SpeechError.NoMatch)), session.events)
        assertEquals(4_000L, session.endedAtMs)
    }

    @Test
    fun `other recognizer errors end the session and keep what was said`() = runTest {
        val speech = FakeSpeechInputRepository(
            passes = listOf(
                listOf(step(0, Listening), step(1_000, Final("hello"))),
                listOf(step(0, Listening), step(500, Error(SpeechError.Busy)))
            )
        )

        val session = listen(speech)

        assertEquals(listOf(Text("hello"), Failed(SpeechError.Busy)), session.events)
        assertEquals(1_500L, session.endedAtMs)
    }

    @Test
    fun `passes that fail before listening restart with a back-off, not in a tight loop`() = runTest {
        val failingAtOnce = List(100) { listOf(step(0, Error(SpeechError.NoMatch))) }
        val speech = FakeSpeechInputRepository(passes = failingAtOnce)

        val session = listen(speech)

        assertEquals(listOf<VoiceSessionEvent>(Failed(SpeechError.NoMatch)), session.events)
        // Never listening: 10 s start cap + 4 s tolerance of 250 ms back-offs, not thousands of passes.
        assertEquals(14_000L, session.endedAtMs)
        assertTrue("passes: ${speech.listenCalls}", speech.listenCalls in 55..58)
    }

    @Test
    fun `the pause only counts once the recognizer is listening, so a slow start loses nothing`() = runTest {
        val speech = FakeSpeechInputRepository(
            passes = listOf(
                // Cold start: the speech model takes 2.5 s to load; the user speaks 3.5 s after the tap.
                listOf(step(2_500, Listening), step(3_500, Partial("hello")), step(500, Final("hello there")))
            )
        )

        val session = listen(speech)

        assertEquals(listOf<VoiceSessionEvent>(Text("hello"), Text("hello there")), session.events)
        // Last word at 6.5 s, then the 4 s tolerance.
        assertEquals(10_500L, session.endedAtMs)
    }
}
