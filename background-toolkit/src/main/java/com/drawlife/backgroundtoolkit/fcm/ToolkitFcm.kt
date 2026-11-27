package com.drawlife.backgroundtoolkit.fcm

import android.content.Context
import android.util.Log
import com.drawlife.backgroundtoolkit.BackgroundToolkit
import com.drawlife.backgroundtoolkit.background.DrawLifeForegroundService
import com.drawlife.backgroundtoolkit.background.work.BackgroundTaskScheduler
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.RemoteMessage
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

    /** 宿主可直接从 FirebaseMessagingService.onNewToken 转交刷新的 Token。 */
    @Synchronized
    fun onNewToken(context: Context, token: String) {
        if (token.isBlank() || !isConfigured(context)) return
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
        } catch (error: IOException) {
            Log.w(BackgroundTaskScheduler.LOG_TAG, "Unable to save FCM registration token", error)
        }
    }

    /** 宿主自有 FirebaseMessagingService 可调用此方法，将消息交给已配置的处理器。 */
    fun onMessageReceived(context: Context, message: RemoteMessage) {
        val handler = BackgroundToolkit.configuration?.fcmMessageHandler ?: return
        handler.onMessageReceived(context, message, message.deliveryPriority())
    }

    /** 宿主在真实 FCM 回调内显式调用。实际投递为高优先级时才尝试启动工具栏服务。 */
    fun requestForegroundToolbar(context: Context, message: RemoteMessage) {
        if (message.deliveryPriority() != FcmDeliveryPriority.HIGH) return
        // start() 会检查前台通知是否已配置。宿主可以使用自己的 FCM 消息服务。
        DrawLifeForegroundService.start(context, source = "fcm_high_priority")
    }

    private fun RemoteMessage.deliveryPriority(): FcmDeliveryPriority = when (priority) {
        RemoteMessage.PRIORITY_HIGH -> FcmDeliveryPriority.HIGH
        RemoteMessage.PRIORITY_NORMAL -> FcmDeliveryPriority.NORMAL
        else -> FcmDeliveryPriority.UNKNOWN
    }
}
