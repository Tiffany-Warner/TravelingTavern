package com.tiffles.travelingtavern.domain.usecase

import com.tiffles.travelingtavern.domain.repository.SpeechInputRepository
import javax.inject.Inject

/** True when the phone can recognize speech on-device. There is no cloud fallback. */
class IsVoiceInputAvailableUseCase @Inject constructor(
    private val speech: SpeechInputRepository
) {
    operator fun invoke(): Boolean = speech.isAvailable()
}
