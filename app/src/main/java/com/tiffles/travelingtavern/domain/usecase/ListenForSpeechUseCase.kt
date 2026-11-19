package com.tiffles.travelingtavern.domain.usecase

import com.tiffles.travelingtavern.domain.model.SpeechError
import com.tiffles.travelingtavern.domain.model.SpeechEvent
import com.tiffles.travelingtavern.domain.model.VoiceSessionEvent
import com.tiffles.travelingtavern.domain.repository.SpeechInputRepository
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * One voice input session: listens until [PAUSE_TOLERANCE_MS] pass without a new word, so a
 * pause of a few seconds doesn't end recording.
 *
 * The recognizer is asked to wait [PAUSE_TOLERANCE_MS] after speech, but may end a pass much
 * sooner. Whenever a pass ends (silence or `NoMatch`), the next one starts right away and the
 * text so far is kept: each pass's final result is committed, and [VoiceSessionEvent.Text]
 * carries the committed text plus the current pass's partial. A watchdog ends the session once
 * no new word has arrived for [PAUSE_TOLERANCE_MS] — so if the recognizer does honour the wait,
 * the session simply ends with its first pass.
 *
 * Ends with [VoiceSessionEvent.Failed] on a recognizer error other than `NoMatch`, or with
 * `Failed(NoMatch)` if the whole session recognized nothing. Collectors stop the session early
 * by cancelling. Never sends anything anywhere: the caller decides what to do with the text.
 */
class ListenForSpeechUseCase @Inject constructor(
    private val speech: SpeechInputRepository
) {
    operator fun invoke(): Flow<VoiceSessionEvent> = channelFlow {
        val session = Session()
        val passes = launch { runPasses(session) }
        val watchdog = launch {
            // The pause only counts once the recognizer can hear: on a cold start the phone
            // takes a few seconds to load its speech model (2.4 s on the Pixel 8).
            withTimeoutOrNull(START_TIMEOUT_MS) { session.ready.first { it } }
            awaitSilence(session.wordsHeard)
            passes.cancel()
        }
        passes.join()
        watchdog.cancel()
        if (!session.failed && session.committed.isEmpty() && session.wordsHeard.value == 0) {
            send(VoiceSessionEvent.Failed(SpeechError.NoMatch))
        }
    }

    /** Recognizer passes back to back until one fails (or the watchdog cancels this). */
    private suspend fun ProducerScope<VoiceSessionEvent>.runPasses(session: Session) {
        while (true) {
            var started = false
            var failure: SpeechError? = null
            var partial = ""
            speech.listen(PAUSE_TOLERANCE_MS).collect { event ->
                when (event) {
                    SpeechEvent.Listening -> {
                        started = true
                        session.ready.value = true
                    }
                    is SpeechEvent.Partial -> if (event.text.isNotBlank() && event.text != partial) {
                        partial = event.text
                        session.heard()
                        send(VoiceSessionEvent.Text(join(session.committed, partial)))
                    }
                    is SpeechEvent.Final -> if (event.text.isNotBlank()) {
                        session.committed = join(session.committed, event.text)
                        partial = ""
                        session.heard()
                        send(VoiceSessionEvent.Text(session.committed))
                    }
                    is SpeechEvent.Error -> if (event.error != SpeechError.NoMatch) failure = event.error
                    SpeechEvent.Ended -> Unit
                }
            }
            failure?.let {
                session.failed = true
                send(VoiceSessionEvent.Failed(it))
                return
            }
            // A pass that ended before it even started listening would otherwise restart in a
            // tight loop.
            if (!started) delay(RESTART_BACKOFF_MS)
        }
    }

    /** Returns once [PAUSE_TOLERANCE_MS] pass without [wordsHeard] changing. */
    private suspend fun awaitSilence(wordsHeard: StateFlow<Int>) {
        while (true) {
            val seen = wordsHeard.value
            withTimeoutOrNull(PAUSE_TOLERANCE_MS) { wordsHeard.first { it != seen } } ?: return
        }
    }

    private class Session {
        var committed = ""
        var failed = false
        val wordsHeard = MutableStateFlow(0)

        /** True once the first pass is listening; the pause tolerance runs from then. */
        val ready = MutableStateFlow(false)

        fun heard() {
            wordsHeard.value += 1
        }
    }

    companion object {
        /** How long a pause may last before recording stops. One constant; not a user setting yet. */
        const val PAUSE_TOLERANCE_MS = 4_000L
        private const val RESTART_BACKOFF_MS = 250L

        /** Longest wait for the recognizer to start listening before the pause starts counting anyway. */
        private const val START_TIMEOUT_MS = 10_000L

        private fun join(committed: String, next: String): String =
            if (committed.isEmpty()) next.trim() else "$committed ${next.trim()}"
    }
}
