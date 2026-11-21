package com.test.test20261120drawlife.fcm

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.test.test20261120drawlife.R
import com.test.test20261120drawlife.background.BackgroundTaskScheduler
import com.test.test20261120drawlife.background.DrawLifeForegroundService

class DrawLifeMessagingService : FirebaseMessagingService() {
    @Suppress("OVERRIDE_DEPRECATION") // Firebase Console test sends still use registration tokens.
    override fun onNewToken(token: String) {
        Log.i(BackgroundTaskScheduler.LOG_TAG, "FCM registration token refreshed")
    }

    override fun onMessageReceived(message: RemoteMessage) {
        Log.i(
            BackgroundTaskScheduler.LOG_TAG,
            "FCM priority: requested=${priorityName(message.originalPriority)}, " +
                "received=${priorityName(message.priority)}"
        )
        handleFcmMessage(this, message, source = "fcm")
        runForegroundServiceProbe(message)
    }

    override fun onDeletedMessages() {
        Log.w(BackgroundTaskScheduler.LOG_TAG, "FCM reported undelivered messages")
    }

    private fun runForegroundServiceProbe(message: RemoteMessage) {
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0 ||
            message.notification != null ||
            message.data["test_value"] != "fgs-high-priority-test"
        ) {
            return
        }

        if (message.priority == RemoteMessage.PRIORITY_HIGH) {
            DrawLifeForegroundService.start(this, source = "fcm_high_priority_test")
        } else {
            Log.i(
                BackgroundTaskScheduler.LOG_TAG,
                "FCM foreground service probe skipped: received=${priorityName(message.priority)}"
            )
        }
        FcmNotification.show(
            this,
            message.messageId,
            getString(R.string.fcm_foreground_probe_title),
            getString(R.string.fcm_foreground_probe_body)
        )
    }

    private fun priorityName(priority: Int): String = when (priority) {
        RemoteMessage.PRIORITY_HIGH -> "high"
        RemoteMessage.PRIORITY_NORMAL -> "normal"
        else -> "unknown"
    }
}

internal fun handleFcmMessage(context: Context, message: RemoteMessage, source: String) {
    val notification = message.notification
    val kind = if (notification == null) "data" else "notification"
    Log.i(
        BackgroundTaskScheduler.LOG_TAG,
        "FCM received: kind=$kind, dataFields=${message.data.size}, source=$source"
    )
    if (notification != null) {
        FcmNotification.show(context, message.messageId, notification.title, notification.body)
    }
}
