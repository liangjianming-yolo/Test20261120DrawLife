package com.test.test20261120drawlife.background

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
import com.drawlife.backgroundtoolkit.ForegroundNotificationProvider
import com.test.test20261120drawlife.MainActivity
import com.test.test20261120drawlife.R

/** 构建通知栏工具栏，并为追踪、信息和设置三个按钮绑定各自的打开入口。 */
internal object DemoToolbarNotification : ForegroundNotificationProvider {
    override val notificationId = 34953
    override val channelId = "draw_life_toolbar"
    private const val EXTRA_ENTRY = "toolbar_entry"

    enum class Entry(val intentValue: String, val requestCode: Int) {
        TRACKER("tracker", 628592),
        INFO("info", 628593),
        SETTINGS("settings", 628594)
    }

    fun entry(intent: Intent): Entry? {
        val value = intent.getStringExtra(EXTRA_ENTRY)
        return Entry.entries.firstOrNull { it.intentValue == value }
    }

    override fun createNotification(context: Context): Notification {
        val manager = requireNotNull(context.getSystemService(NotificationManager::class.java))
        if (manager.getNotificationChannel(channelId) == null) {
            val channel = NotificationChannel(
                channelId,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
            }
            manager.createNotificationChannel(channel)
        }

        val expanded = toolbarViews(context, R.layout.demo_toolbar_expanded)
        // Android 12 起为折叠状态使用紧凑布局，旧版本复用展开布局。
        val collapsed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            toolbarViews(context, R.layout.demo_toolbar_collapsed)
        } else {
            expanded
        }

        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.demo_ic_notification)
            .setContentTitle(context.getString(R.string.notification_title))
            .setCustomContentView(collapsed)
            .setCustomBigContentView(expanded)
            .setContentIntent(openEntry(context, Entry.TRACKER))
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun toolbarViews(context: Context, @LayoutRes layout: Int): RemoteViews {
        return RemoteViews(context.packageName, layout).apply {
            setOnClickPendingIntent(R.id.demo_toolbar_tracker, openEntry(context, Entry.TRACKER))
            setOnClickPendingIntent(R.id.demo_toolbar_info, openEntry(context, Entry.INFO))
            setOnClickPendingIntent(R.id.demo_toolbar_settings, openEntry(context, Entry.SETTINGS))
        }
    }

    /** 三个按钮打开同一个 Activity，各自的 action 和请求码区分点击动作，宿主从 EXTRA_ENTRY 读取入口。 */
    private fun openEntry(context: Context, entry: Entry): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = "${context.packageName}.toolbar.${entry.intentValue}"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_ENTRY, entry.intentValue)
        }
        return PendingIntent.getActivity(
            context,
            entry.requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
