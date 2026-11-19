package com.tiffles.travelingtavern.domain.repository

import com.tiffles.travelingtavern.domain.model.SpeechEvent
import kotlinx.coroutines.flow.Flow

/** The phone's on-device speech recognizer. Never a cloud recognizer. */
interface SpeechInputRepository {

    /** False when on-device recognition isn't available on this phone. */
    fun isAvailable(): Boolean

    /**
     * One recognizer pass. Emits [SpeechEvent]s and completes after [SpeechEvent.Final],
     * [SpeechEvent.Ended] or [SpeechEvent.Error]. Cancelling the flow stops the recognizer and
     * releases it. [silenceMs] asks the recognizer to wait that long after speech before ending
     * the pass; it may not honour it.
     */
    fun listen(silenceMs: Long): Flow<SpeechEvent>
}
