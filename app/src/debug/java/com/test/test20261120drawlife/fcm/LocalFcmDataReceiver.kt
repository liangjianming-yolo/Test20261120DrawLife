package com.test.test20261120drawlife.fcm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.firebase.messaging.RemoteMessage

class LocalFcmDataReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.test.test20261120drawlife.action.SIMULATE_FCM_DATA") return
        val value = intent.getStringExtra("test_value") ?: return
        val message = RemoteMessage.Builder("drawlife-local-test")
            .addData("test_value", value)
            .build()
        handleFcmMessage(context, message, source = "local")
    }
}
