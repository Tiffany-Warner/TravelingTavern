package com.tiffles.travelingtavern.data.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.tiffles.travelingtavern.domain.model.SpeechError
import com.tiffles.travelingtavern.domain.model.SpeechEvent
import com.tiffles.travelingtavern.domain.repository.SpeechInputRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import javax.inject.Inject
import javax.inject.Singleton

/**
 * [SpeechInputRepository] on Android's on-device recognizer
 * ([SpeechRecognizer.createOnDeviceSpeechRecognizer], API 31). Audio never leaves the phone.
 * The recognizer is created, used and destroyed on the main thread, one per pass.
 */
@Singleton
class OnDeviceSpeechInput @Inject constructor(
    @ApplicationContext private val context: Context
) : SpeechInputRepository {

    override fun isAvailable(): Boolean = SpeechRecognizer.isOnDeviceRecognitionAvailable(context)

    override fun listen(silenceMs: Long): Flow<SpeechEvent> = callbackFlow {
        val recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        val intent = recognizerIntent(silenceMs)
        recognizer.setRecognitionListener(PassListener(this, recognizer, intent))
        recognizer.startListening(intent)
        awaitClose {
            // Cancelling (user tapped stop, left Chat) or the pass ending: release the mic.
            recognizer.cancel()
            recognizer.destroy()
        }
    }.flowOn(Dispatchers.Main)

    private fun recognizerIntent(silenceMs: Long) = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, silenceMs)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, silenceMs)
    }

    /** Turns one pass's callbacks into [SpeechEvent]s and closes the flow when the pass ends. */
    private class PassListener(
        private val scope: ProducerScope<SpeechEvent>,
        private val recognizer: SpeechRecognizer,
        private val intent: Intent
    ) : RecognitionListener {

        override fun onReadyForSpeech(params: Bundle?) {
            scope.trySend(SpeechEvent.Listening)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            firstResult(partialResults)?.let { scope.trySend(SpeechEvent.Partial(it)) }
        }

        override fun onResults(results: Bundle?) {
            val text = firstResult(results)
            scope.trySend(if (text.isNullOrBlank()) SpeechEvent.Ended else SpeechEvent.Final(text))
            scope.close()
        }

        override fun onError(error: Int) {
            val mapped = error.toSpeechError()
            if (mapped == SpeechError.LanguagePackMissing && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Starts the download in the background; the user is told to wait for it.
                runCatching { recognizer.triggerModelDownload(intent) }
                    .onFailure { Log.w(TAG, "Couldn't start the speech pack download", it) }
            }
            if (mapped == SpeechError.Other) Log.w(TAG, "Speech recognizer error code $error")
            scope.trySend(SpeechEvent.Error(mapped))
            scope.close()
        }

        override fun onEndOfSpeech() = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        private fun firstResult(bundle: Bundle?): String? =
            bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
    }

    private companion object {
        const val TAG = "OnDeviceSpeechInput"

        fun Int.toSpeechError(): SpeechError = when (this) {
            SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SpeechError.NoMatch
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SpeechError.PermissionDenied
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> SpeechError.Busy
            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                SpeechError.LanguagePackMissing
            else -> SpeechError.Other
        }
    }
}
