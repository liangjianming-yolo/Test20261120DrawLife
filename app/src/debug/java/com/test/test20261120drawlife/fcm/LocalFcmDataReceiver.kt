package com.test.test20261120drawlife.fcm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.test.test20261120drawlife.LOG_TAG

class LocalFcmDataReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.test.test20261120drawlife.action.SIMULATE_FCM_DATA") return
        if (intent.getStringExtra("test_value") == null) return
        Log.i(LOG_TAG, "FCM received: kind=data, dataFields=1, source=local")
    }
}
