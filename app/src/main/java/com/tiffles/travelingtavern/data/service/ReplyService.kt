package com.tiffles.travelingtavern.data.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.tiffles.travelingtavern.R
import com.tiffles.travelingtavern.data.bridge.LeaseLifecycle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The reply foreground service (A6): a `shortService` that keeps the app's process out of the
 * cached state, so Android doesn't freeze the WebView's renderer while a reply finishes in the
 * background. Started and stopped through [AndroidReplyServiceController]; it always calls
 * `startForeground` first, then stops itself once the controller no longer wants it.
 *
 * At the time limit (`onTimeout`, about 3 minutes) [LeaseLifecycle] decides what happens before
 * the service stops; Android allows only a few seconds after it (Notes, slice 7).
 */
@AndroidEntryPoint
class ReplyService : Service() {

    @Inject
    lateinit var controller: AndroidReplyServiceController

    @Inject
    lateinit var leaseLifecycle: LeaseLifecycle

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var watch: Job? = null
    private var timedOut = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE
        )
        if (watch?.isActive != true) {
            watch = scope.launch {
                controller.wanted.first { !it }
                stopNow()
            }
        }
        return START_NOT_STICKY
    }

    /** API 34: the `shortService` time limit. */
    override fun onTimeout(startId: Int) = onTimeLimit()

    /** API 35+: the time limit, with the service type. Both may be called; handled once. */
    override fun onTimeout(startId: Int, fgsType: Int) = onTimeLimit()

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun onTimeLimit() {
        if (timedOut) return
        timedOut = true
        Log.i(TAG, "Time limit reached")
        // From here LeaseLifecycle decides when the service stops, not the reply's end.
        watch?.cancel()
        leaseLifecycle.onReplyServiceTimeout(::stopNow)
    }

    private fun stopNow() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notification(): Notification {
        val manager = NotificationManagerCompat.from(this)
        manager.createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(getString(R.string.reply_channel_name))
                .build()
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentText(getString(R.string.reply_notification_text))
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private companion object {
        const val TAG = "ReplyService"
        const val CHANNEL_ID = "replies_in_progress"
        const val NOTIFICATION_ID = 1
    }
}
