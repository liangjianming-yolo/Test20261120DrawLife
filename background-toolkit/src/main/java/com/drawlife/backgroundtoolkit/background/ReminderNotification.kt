package com.drawlife.backgroundtoolkit.background

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.drawlife.backgroundtoolkit.BackgroundToolkit
import com.drawlife.backgroundtoolkit.R

/** 每日闹钟到点后发布提醒通知。点击通知会打开宿主配置的 Activity。 */
internal object ReminderNotification {
    const val CHANNEL_ID = "draw_life_reminders"
    private const val NOTIFICATION_ID = 34954

    /** 检查应用和提醒渠道的通知开关。首次创建渠道前，渠道缺失也允许继续。 */
    fun notificationsEnabled(context: Context): Boolean {
        val manager = requireNotNull(context.getSystemService(NotificationManager::class.java))
        return NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            manager.getNotificationChannel(CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun show(context: Context, key: String?, title: String?, message: String?) {
        val launchActivity = BackgroundToolkit.configuration?.launchActivity ?: return
        val manager = requireNotNull(context.getSystemService(NotificationManager::class.java))
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.drawlife_toolkit_reminder_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
        // 到点广播可能已正常收到，但发布通知还需要应用和渠道允许，Android 13 起还要检查通知权限。
        if (
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) || !notificationsEnabled(context)
        ) {
            Log.w(BackgroundTaskScheduler.LOG_TAG, "Alarm fired; reminder notifications are disabled")
            return
        }

        val intent = Intent(context, launchActivity).apply {
            action = "${context.packageName}.open_reminder"
            data = AlarmReminderScheduler.reminderData(key)
            putExtra(BackgroundToolkit.EXTRA_DAILY_REMINDER_KEY, key)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.drawlife_toolkit_ic_notification)
            .setContentTitle(title ?: context.getString(R.string.drawlife_toolkit_reminder_notification_title))
            .setContentText(message ?: context.getString(R.string.drawlife_toolkit_reminder_notification_text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(PendingIntent.getActivity(
                context, NOTIFICATION_ID, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ))
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(key, NOTIFICATION_ID, notification)
        } catch (exception: SecurityException) {
            Log.w(BackgroundTaskScheduler.LOG_TAG, "Reminder notification permission rejected", exception)
        }
    }

    /** 用户取消每日提醒时，同时撤下通知栏中已显示的那条提醒。 */
    fun cancel(context: Context, key: String?) {
        requireNotNull(context.getSystemService(NotificationManager::class.java)).cancel(key, NOTIFICATION_ID)
    }
}
