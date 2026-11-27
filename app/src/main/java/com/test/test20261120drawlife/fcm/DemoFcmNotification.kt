package com.test.test20261120drawlife.fcm

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
import androidx.core.content.ContextCompat
import com.test.test20261120drawlife.LOG_TAG
import com.test.test20261120drawlife.MainActivity
import com.test.test20261120drawlife.R

internal object DemoFcmNotification {
    private const val NOTIFICATION_ID = 34955

    fun createChannel(context: Context) {
        val manager = requireNotNull(context.getSystemService(NotificationManager::class.java))
        manager.createNotificationChannel(
            NotificationChannel(
                context.getString(R.string.demo_fcm_channel_id),
                context.getString(R.string.demo_fcm_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }

    fun show(
        context: Context,
        messageId: String?,
        title: String,
        body: String
    ): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        val manager = requireNotNull(context.getSystemService(NotificationManager::class.java))
        val channelId = context.getString(R.string.demo_fcm_channel_id)
        if (!manager.areNotificationsEnabled() ||
            manager.getNotificationChannel(channelId)?.importance == NotificationManager.IMPORTANCE_NONE
        ) {
            return false
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            action = "${context.packageName}.open_fcm_notification"
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.demo_ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(PendingIntent.getActivity(
                context, NOTIFICATION_ID, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ))
            .setAutoCancel(true)
            .build()
        return try {
            manager.notify(messageId, NOTIFICATION_ID, notification)
            true
        } catch (exception: SecurityException) {
            Log.w(LOG_TAG, "FCM notification permission rejected", exception)
            false
        }
    }
}
