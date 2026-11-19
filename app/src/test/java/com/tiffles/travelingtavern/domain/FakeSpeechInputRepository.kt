package com.tiffles.travelingtavern.domain

import com.tiffles.travelingtavern.domain.model.SpeechEvent
import com.tiffles.travelingtavern.domain.repository.SpeechInputRepository
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Scripted recognizer for tests. Each call to [listen] plays the next pass from [passes]: every
 * step waits its delay (virtual time) and then emits its event. Once the script runs out, a pass
 * starts listening and hears nothing until it's cancelled.
 */
class FakeSpeechInputRepository(
    var available: Boolean = true,
    passes: List<List<Step>> = emptyList()
) : SpeechInputRepository {

    /** One scripted moment of a pass: wait [afterMs], then emit [event]. */
    data class Step(val afterMs: Long, val event: SpeechEvent)

    private val script = ArrayDeque(passes)
    var listenCalls = 0
        private set

    override fun isAvailable(): Boolean = available

    override fun listen(silenceMs: Long): Flow<SpeechEvent> {
        listenCalls++
        val pass = script.removeFirstOrNull()
        return flow {
            if (pass == null) {
                emit(SpeechEvent.Listening)
                awaitCancellation()
            }
            pass.forEach { step ->
                delay(step.afterMs)
                emit(step.event)
            }
        }
    }
}

fun step(afterMs: Long, event: SpeechEvent) = FakeSpeechInputRepository.Step(afterMs, event)
