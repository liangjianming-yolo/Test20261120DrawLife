package com.drawlife.backgroundtoolkit.background

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import androidx.annotation.LayoutRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.drawlife.backgroundtoolkit.BackgroundToolkit
import com.drawlife.backgroundtoolkit.R

/** 构建通知栏工具栏，并为追踪、信息和设置三个按钮绑定各自的打开入口。 */
internal object ToolbarNotification {
    const val NOTIFICATION_ID = 34953
    const val CHANNEL_ID = "draw_life_toolbar"
    const val EXTRA_ENTRY = "toolbar_entry"
    const val ENTRY_TRACKER = "tracker"
    const val ENTRY_INFO = "info"
    const val ENTRY_SETTINGS = "settings"

    fun notificationsEnabled(context: Context): Boolean {
        val manager = requireNotNull(context.getSystemService(NotificationManager::class.java))
        return NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            manager.getNotificationChannel(CHANNEL_ID)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    fun create(context: Context): Notification {
        val manager = requireNotNull(context.getSystemService(NotificationManager::class.java))
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.drawlife_toolkit_toolbar_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
            }
            manager.createNotificationChannel(channel)
        }

        val expanded = toolbarViews(context, R.layout.drawlife_toolkit_toolbar_expanded)
        // Android 12 起为折叠状态使用紧凑布局，旧版本复用展开布局。
        val collapsed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            toolbarViews(context, R.layout.drawlife_toolkit_toolbar_collapsed)
        } else {
            expanded
        }

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.drawlife_toolkit_ic_notification)
            .setContentTitle(context.getString(R.string.drawlife_toolkit_toolbar_title))
            .setCustomContentView(collapsed)
            .setCustomBigContentView(expanded)
            .setContentIntent(openEntry(context, ENTRY_TRACKER, 628592))
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun toolbarViews(context: Context, @LayoutRes layout: Int): RemoteViews {
        return RemoteViews(context.packageName, layout).apply {
            setOnClickPendingIntent(R.id.drawlife_toolkit_toolbar_tracker, openEntry(context, ENTRY_TRACKER, 628592))
            setOnClickPendingIntent(R.id.drawlife_toolkit_toolbar_info, openEntry(context, ENTRY_INFO, 628593))
            setOnClickPendingIntent(R.id.drawlife_toolkit_toolbar_settings, openEntry(context, ENTRY_SETTINGS, 628594))
        }
    }

    /** 三个按钮打开同一个 Activity，各自的 action 和请求码区分点击动作，宿主从 EXTRA_ENTRY 读取入口。 */
    private fun openEntry(context: Context, entry: String, requestCode: Int): PendingIntent {
        val launchActivity = checkNotNull(BackgroundToolkit.configuration).launchActivity
        val intent = Intent(context, launchActivity).apply {
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
