package com.drawlife.backgroundtoolkit.background

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.drawlife.backgroundtoolkit.BackgroundToolkit

/** 接收影响闹钟的系统广播，按已保存的计划重新安排每日提醒。 */
class AlarmRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (BackgroundToolkit.configuration?.enableDailyReminder != true) return
        val action = intent.action ?: return
        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> {
                // 开机、应用更新或获得精确闹钟权限后，沿用计划中保存的到期时间。
                AlarmReminderScheduler.restore(context, source = action, forceReschedule = true)
            }
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> {
                // 系统时间或时区改变后，按用户选择的当地时分重新计算到期时间。
                AlarmReminderScheduler.restore(context, source = action, timeChanged = true, forceReschedule = true)
            }
        }
    }
}
