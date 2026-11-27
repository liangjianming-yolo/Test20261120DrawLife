package com.drawlife.backgroundtoolkit.fcm

import android.util.Log
import com.drawlife.backgroundtoolkit.background.work.BackgroundTaskScheduler
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class DrawLifeMessagingService : FirebaseMessagingService() {
    @Suppress("OVERRIDE_DEPRECATION") // Firebase Console test sends still use registration tokens.
    override fun onNewToken(token: String) {
        ToolkitFcm.onNewToken(this, token)
    }

    override fun onMessageReceived(message: RemoteMessage) {
        ToolkitFcm.onMessageReceived(this, message)
    }

    override fun onDeletedMessages() {
        Log.w(BackgroundTaskScheduler.LOG_TAG, "FCM reported undelivered messages")
    }
}
