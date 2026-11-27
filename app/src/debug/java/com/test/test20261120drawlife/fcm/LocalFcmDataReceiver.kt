package com.test.test20261120drawlife.fcm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.drawlife.backgroundtoolkit.fcm.ToolkitFcm

class LocalFcmDataReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.test.test20261120drawlife.action.SIMULATE_FCM_DATA") return
        val value = intent.getStringExtra("test_value") ?: return
        ToolkitFcm.simulateDataMessage(context, value)
    }
}
