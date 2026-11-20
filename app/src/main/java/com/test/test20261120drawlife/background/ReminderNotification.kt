package com.test.test20261120drawlife.background

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
import com.test.test20261120drawlife.MainActivity
import com.test.test20261120drawlife.R

object ReminderNotification {
    const val CHANNEL_ID = "draw_life_reminders"
    private const val NOTIFICATION_ID = 34954

    fun notificationsEnabled(context: Context): Boolean {
        val manager = requireNotNull(context.getSystemService(NotificationManager::class.java))
        return NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            manager.getNotificationChannel(CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun show(context: Context) {
        val manager = requireNotNull(context.getSystemService(NotificationManager::class.java))
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.alarm_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
        if (
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED) || !notificationsEnabled(context)
        ) {
            Log.w(BackgroundTaskScheduler.LOG_TAG, "Alarm fired; reminder notifications are disabled")
            return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            action = "${context.packageName}.open_reminder"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_toolbar_notification)
            .setContentTitle(context.getString(R.string.alarm_notification_title))
            .setContentText(context.getString(R.string.alarm_notification_text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(PendingIntent.getActivity(
                context, NOTIFICATION_ID, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ))
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(NOTIFICATION_ID, notification)
        } catch (exception: SecurityException) {
            Log.w(BackgroundTaskScheduler.LOG_TAG, "Reminder notification permission rejected", exception)
        }
    }

    fun cancel(context: Context) {
        requireNotNull(context.getSystemService(NotificationManager::class.java)).cancel(NOTIFICATION_ID)
    }
}
