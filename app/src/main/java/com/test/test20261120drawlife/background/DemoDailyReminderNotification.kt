package com.test.test20261120drawlife.background

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import com.drawlife.backgroundtoolkit.BackgroundToolkit
import com.drawlife.backgroundtoolkit.DailyReminderNotificationProvider
import com.test.test20261120drawlife.MainActivity
import com.test.test20261120drawlife.R

internal object DemoDailyReminderNotification : DailyReminderNotificationProvider {
    override val channelId = "draw_life_reminders"
    private const val CLICK_REQUEST_CODE = 34954

    override fun createNotification(
        context: Context,
        key: String?,
        title: String?,
        message: String?
    ): Notification {
        val manager = requireNotNull(context.getSystemService(NotificationManager::class.java))
        manager.createNotificationChannel(
            NotificationChannel(
                channelId,
                context.getString(R.string.alarm_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )

        val intent = Intent(context, MainActivity::class.java).apply {
            action = "${context.packageName}.open_reminder"
            // Intent.data 区分同一时间触发的不同提醒，也兼容已有无 key 的点击入口。
            data = key?.let { Uri.fromParts("drawlife-reminder", it, null) }
            putExtra(BackgroundToolkit.EXTRA_DAILY_REMINDER_KEY, key)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.demo_ic_notification)
            .setContentTitle(title ?: context.getString(R.string.alarm_notification_title))
            .setContentText(message ?: context.getString(R.string.alarm_notification_text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(
                PendingIntent.getActivity(
                    context, CLICK_REQUEST_CODE, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setAutoCancel(true)
            .build()
    }
}
