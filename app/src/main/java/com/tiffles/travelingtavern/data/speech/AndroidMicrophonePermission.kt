package com.tiffles.travelingtavern.data.speech

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.tiffles.travelingtavern.domain.repository.MicrophonePermission
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** [MicrophonePermission] from the system's current grant for `RECORD_AUDIO`. */
@Singleton
class AndroidMicrophonePermission @Inject constructor(
    @ApplicationContext private val context: Context
) : MicrophonePermission {
    override fun isGranted(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
}
