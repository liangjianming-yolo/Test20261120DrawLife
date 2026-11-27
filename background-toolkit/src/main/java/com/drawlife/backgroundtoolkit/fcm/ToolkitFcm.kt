package com.drawlife.backgroundtoolkit.fcm

import android.content.Context
import android.content.pm.ApplicationInfo
import android.util.Log
import com.drawlife.backgroundtoolkit.BackgroundToolkit
import com.drawlife.backgroundtoolkit.R
import com.drawlife.backgroundtoolkit.background.BackgroundTaskScheduler
import com.drawlife.backgroundtoolkit.background.DrawLifeForegroundService
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.RemoteMessage
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.Executors

object ToolkitFcm {
    private const val TOKEN_FILE = "drawlife-fcm-registration.json"
    private val tokenExecutor = Executors.newSingleThreadExecutor()
    private var tokenGeneration = 0L

    fun isConfigured(context: Context): Boolean =
        FirebaseApp.getApps(context).any { it.name == FirebaseApp.DEFAULT_APP_NAME }

    @Suppress("DEPRECATION") // Firebase Console test sends use the registration token.
    @Synchronized
    fun requestRegistrationToken(context: Context): Task<String> {
        if (!isConfigured(context)) {
            return Tasks.forException(IllegalStateException("Default Firebase app is not configured"))
        }
        val app = context.applicationContext
        val generation = tokenGeneration
        return FirebaseMessaging.getInstance().token.addOnSuccessListener(tokenExecutor) { token ->
            synchronized(this) {
                // A rotation may have arrived while this request was in flight.
                if (generation == tokenGeneration) saveToken(app, token)
            }
        }
    }

    @Synchronized
    fun onNewToken(context: Context, token: String) {
        if (BackgroundToolkit.configuration?.enableFcmNotifications != true || !isConfigured(context)) return
        tokenGeneration++
        saveToken(context, token)
    }

    private fun saveToken(context: Context, token: String) {
        if (token.isBlank()) return
        val options = FirebaseApp.getInstance().options
        val snapshot = JSONObject()
            .put("project_id", options.projectId)
            .put("app_id", options.applicationId)
            .put("package_name", context.packageName)
            .put("token", token)
            .toString()
        val file = File(context.noBackupFilesDir, TOKEN_FILE)
        try {
            if (file.isFile && file.readText() == snapshot) return
            val temp = File.createTempFile("drawlife-fcm-", ".tmp", file.parentFile)
            try {
                temp.writeText(snapshot)
                Files.move(temp.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } finally {
                temp.delete()
            }
            Log.i(BackgroundTaskScheduler.LOG_TAG, "FCM registration token saved")
            printToken(context)
        } catch (error: IOException) {
            Log.w(BackgroundTaskScheduler.LOG_TAG, "Unable to save FCM registration token", error)
        }
    }

    fun printToken(context: Context) {
        if (!isDebuggable(context)) return
        val file = File(context.noBackupFilesDir, TOKEN_FILE)
        if (!file.isFile) {
            Log.i(BackgroundTaskScheduler.LOG_TAG, "FCM token not cached yet")
            return
        }
        try {
            val token = JSONObject(file.readText()).getString("token")
            Log.i(BackgroundTaskScheduler.LOG_TAG, "FCM token $token")
        } catch (error: IOException) {
            Log.w(BackgroundTaskScheduler.LOG_TAG, "Unable to read cached FCM token", error)
        } catch (_: JSONException) {
            Log.w(BackgroundTaskScheduler.LOG_TAG, "Invalid FCM token cache")
        }
    }

    /** Call from the host's FirebaseMessagingService when it owns the FCM manifest entry. */
    fun onMessageReceived(context: Context, message: RemoteMessage) {
        val config = BackgroundToolkit.configuration ?: return
        if (!config.enableFcmNotifications) return

        // SDK 先取 delivered_priority，缺失时检查 priority_reduced，再回退到 google.priority。
        val deliveredPriority = message.priority
        Log.i(
            BackgroundTaskScheduler.LOG_TAG,
            "FCM priority: requested=${priorityName(message.originalPriority)}, " +
                "received=${priorityName(deliveredPriority)}"
        )
        logMessage(message, source = "fcm")

        if (config.enableFcmPriorityHandling) {
            if (deliveredPriority == RemoteMessage.PRIORITY_HIGH) {
                DrawLifeForegroundService.start(context, source = "fcm_high_priority")
                FcmReminderPolicy.showHigh(context, config.launchActivity, message.messageId)
            } else {
                FcmReminderPolicy.checkNormal(context, config.launchActivity, message.messageId)
            }
            return
        }

        val notification = message.notification
        if (notification != null) {
            FcmNotification.show(
                context, config.launchActivity, message.messageId, notification.title, notification.body
            )
        } else if (config.enableForegroundToolbar && config.enableFcmForegroundServiceProbe &&
            isDebuggable(context) && message.data["test_value"] == "fgs-high-priority-test"
        ) {
            runForegroundServiceProbe(context, message, config)
        }
    }

    /** Records a local test message in a debuggable host without invoking a real FCM callback. */
    fun simulateDataMessage(context: Context, testValue: String) {
        if (!isDebuggable(context) || BackgroundToolkit.configuration?.enableFcmNotifications != true) return

        val message = RemoteMessage.Builder("drawlife-local-test").addData("test_value", testValue).build()
        logMessage(message, source = "local")
    }

    internal fun createChannel(context: Context) = FcmNotification.createChannel(context)

    private fun runForegroundServiceProbe(
        context: Context,
        message: RemoteMessage,
        config: BackgroundToolkit.Config
    ) {
        when (message.priority) {
            RemoteMessage.PRIORITY_HIGH ->
                DrawLifeForegroundService.start(context, source = "fcm_high_priority_test")
            RemoteMessage.PRIORITY_NORMAL -> Log.i(
                BackgroundTaskScheduler.LOG_TAG,
                "FCM foreground service probe skipped: received=normal"
            )
            else -> Log.w(
                BackgroundTaskScheduler.LOG_TAG,
                "FCM foreground service probe skipped: unknown delivery priority (${message.priority})"
            )
        }
        FcmNotification.show(
            context,
            config.launchActivity,
            message.messageId,
            context.getString(R.string.drawlife_toolkit_fcm_foreground_probe_title),
            context.getString(R.string.drawlife_toolkit_fcm_foreground_probe_body)
        )
    }

    private fun logMessage(message: RemoteMessage, source: String) {
        val kind = if (message.notification == null) "data" else "notification"
        Log.i(
            BackgroundTaskScheduler.LOG_TAG,
            "FCM received: kind=$kind, dataFields=${message.data.size}, source=$source"
        )
    }

    private fun isDebuggable(context: Context): Boolean =
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private fun priorityName(priority: Int): String = when (priority) {
        RemoteMessage.PRIORITY_HIGH -> "high"
        RemoteMessage.PRIORITY_NORMAL -> "normal"
        else -> "unknown"
    }
}
