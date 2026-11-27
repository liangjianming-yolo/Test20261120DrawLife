package com.drawlife.backgroundtoolkit.background.alarm

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.drawlife.backgroundtoolkit.BackgroundToolkit
import com.drawlife.backgroundtoolkit.background.work.BackgroundTaskScheduler

/** 按提醒 key 发布或取消宿主创建的通知。 */
internal object ReminderNotification {
    private const val TAG_PREFIX = "com.drawlife.backgroundtoolkit.daily_reminder"
    private const val NOTIFICATION_ID = 34956
    private const val LEGACY_ID = 34954
    private const val LEGACY_CHANNEL_ID = "draw_life_reminders"

    private fun tag(key: String?): String = if (key == null) TAG_PREFIX else "$TAG_PREFIX:$key"

    fun show(context: Context, key: String?, title: String?, message: String?) {
        val provider = BackgroundToolkit.configuration?.dailyReminderNotification ?: return
        if (!BackgroundToolkit.reminderNotificationsEnabled(context)) {
            Log.w(BackgroundTaskScheduler.LOG_TAG, "Alarm fired; reminder notifications are disabled")
            return
        }

        try {
            val notification = provider.createNotification(context, key, title, message) ?: return
            val manager = requireNotNull(context.getSystemService(NotificationManager::class.java))
            manager.notify(tag(key), NOTIFICATION_ID, notification)
            cancelLegacyReminder(context, manager, key)
        } catch (exception: SecurityException) {
            Log.w(BackgroundTaskScheduler.LOG_TAG, "Reminder notification permission rejected", exception)
        }
    }

    /** 用户取消每日提醒时，同时撤下通知栏中已显示的那条提醒。 */
    fun cancel(context: Context, key: String?) {
        val manager = requireNotNull(context.getSystemService(NotificationManager::class.java))
        manager.cancel(tag(key), NOTIFICATION_ID)
        cancelLegacyReminder(context, manager, key)
    }

    @Suppress("DEPRECATION") // GET_ACTIVITIES 的 int 重载兼容 API 26。
    private fun cancelLegacyReminder(context: Context, manager: NotificationManager, key: String?) {
        val notification = manager.activeNotifications.firstOrNull {
            it.id == LEGACY_ID && it.tag == key
        }?.notification ?: return
        if (notification.channelId != LEGACY_CHANNEL_ID ||
            notification.category != Notification.CATEGORY_REMINDER ||
            notification.flags and Notification.FLAG_FOREGROUND_SERVICE != 0
        ) return
        val click = notification.contentIntent ?: return

        // 旧版允许宿主指定任意点击 Activity。只认领持有旧版同一个 PendingIntent 的提醒。
        val activities = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_ACTIVITIES).activities.orEmpty()
        val intent = Intent("${context.packageName}.open_reminder")
            .setData(AlarmReminderScheduler.reminderData(key))
        for (activity in activities) {
            intent.setClassName(context, activity.name)
            val oldClick = PendingIntent.getActivity(
                context, LEGACY_ID, intent, PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (oldClick == click) {
                manager.cancel(key, LEGACY_ID)
                return
            }
        }
    }
}
