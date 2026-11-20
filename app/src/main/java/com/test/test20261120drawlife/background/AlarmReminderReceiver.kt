package com.test.test20261120drawlife.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class AlarmReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == AlarmReminderScheduler.ACTION_REMINDER) {
            AlarmReminderScheduler.onAlarmReceived(context)
        }
    }
}
