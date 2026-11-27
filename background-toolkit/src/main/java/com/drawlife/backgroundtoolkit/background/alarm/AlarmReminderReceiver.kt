package com.drawlife.backgroundtoolkit.background.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.drawlife.backgroundtoolkit.BackgroundToolkit

/** 系统通过 AlarmManager 投递每日提醒广播时，会调用这个接收器的 onReceive。 */
class AlarmReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // 配置尚未初始化，或宿主已关闭每日提醒时，直接忽略这次广播。
        if (BackgroundToolkit.configuration?.dailyReminderNotification == null) return

        // 设置闹钟时已显式指定此接收器，这里再核对「宿主包名.action.DAILY_REMINDER」。
        if (intent.action == AlarmReminderScheduler.reminderAction(context)) {
            // 调度器会核对已保存的提醒时间。确认到点后，先安排下一次闹钟，再尝试显示本次通知。
            val data = intent.data
            val key = data?.let {
                if (it.scheme != AlarmReminderScheduler.REMINDER_SCHEME || !it.isOpaque) return
                it.schemeSpecificPart?.takeIf(String::isNotBlank) ?: return
            }
            AlarmReminderScheduler.onAlarmReceived(context, key)
        }
    }
}
