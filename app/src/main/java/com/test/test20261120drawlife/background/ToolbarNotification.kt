package com.test.test20261120drawlife.background

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.RemoteViews
import androidx.annotation.LayoutRes
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.test.test20261120drawlife.MainActivity
import com.test.test20261120drawlife.R

object ToolbarNotification {
    const val NOTIFICATION_ID = 34953
    const val CHANNEL_ID = "draw_life_toolbar"
    const val EXTRA_ENTRY = "toolbar_entry"
    const val ENTRY_TRACKER = "tracker"
    const val ENTRY_INFO = "info"
    const val ENTRY_SETTINGS = "settings"

    fun restoreAfterBoot(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = requireNotNull(context.getSystemService(NotificationManager::class.java))
        if (!manager.areNotificationsEnabled() ||
            manager.getNotificationChannel(CHANNEL_ID)?.importance == NotificationManager.IMPORTANCE_NONE
        ) {
            return
        }
        try {
            // 与前台服务使用同一通知 ID，打开应用后更新原来的卡片。
            manager.notify(NOTIFICATION_ID, create(context, forForegroundService = false))
            Log.i(BackgroundTaskScheduler.LOG_TAG, "Toolbar notification restored after boot")
        } catch (exception: SecurityException) {
            Log.w(BackgroundTaskScheduler.LOG_TAG, "Toolbar notification permission rejected", exception)
        }
    }

    fun create(context: Context, forForegroundService: Boolean): Notification {
        val manager = requireNotNull(context.getSystemService(NotificationManager::class.java))
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
            }
            manager.createNotificationChannel(channel)
        }

        val expanded = toolbarViews(context, R.layout.notification_toolbar_expanded)
        val collapsed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            toolbarViews(context, R.layout.notification_toolbar_collapsed)
        } else {
            expanded
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_toolbar_notification)
            .setContentTitle(context.getString(R.string.notification_title))
            .setCustomContentView(collapsed)
            .setCustomBigContentView(expanded)
            .setContentIntent(openEntry(context, ENTRY_TRACKER, 628592))
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .apply {
                if (forForegroundService) {
                    setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                }
            }
            .build()
    }

    private fun toolbarViews(context: Context, @LayoutRes layout: Int): RemoteViews {
        return RemoteViews(context.packageName, layout).apply {
            setOnClickPendingIntent(R.id.toolbar_tracker, openEntry(context, ENTRY_TRACKER, 628592))
            setOnClickPendingIntent(R.id.toolbar_info, openEntry(context, ENTRY_INFO, 628593))
            setOnClickPendingIntent(R.id.toolbar_settings, openEntry(context, ENTRY_SETTINGS, 628594))
        }
    }

    private fun openEntry(context: Context, entry: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = "${context.packageName}.toolbar.$entry"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_ENTRY, entry)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
