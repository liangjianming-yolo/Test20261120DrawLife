package com.drawlife.backgroundtoolkit.background

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.Process
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.drawlife.backgroundtoolkit.BackgroundToolkit
import java.util.concurrent.atomic.AtomicBoolean

/** 用前台服务承载通知栏工具栏，服务创建时立即提交通知，后续启动请求可刷新同一张通知。 */
internal class DrawLifeForegroundService : Service() {
    override fun onCreate() {
        super.onCreate()
        if (BackgroundToolkit.configuration?.enableForegroundToolbar != true) {
            stopSelf()
            return
        }
        Log.i(BackgroundTaskScheduler.LOG_TAG, "ForegroundService.onCreate pid=${Process.myPid()}")
        promoteToForeground()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!isRunning.get()) return START_NOT_STICKY

        val source = intent?.getStringExtra(EXTRA_START_SOURCE) ?: "system_restart"
        Log.i(
            BackgroundTaskScheduler.LOG_TAG,
            "ForegroundService.onStartCommand startId=$startId, source=$source"
        )
        // 成功后允许系统在进程被回收时重建服务，此时再次进入 onStartCommand 的 intent 可能为 null。
        return if (promoteToForeground()) START_STICKY else START_NOT_STICKY
    }

    private fun promoteToForeground(): Boolean {
        return try {
            val serviceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            }
            ServiceCompat.startForeground(
                this,
                ToolbarNotification.NOTIFICATION_ID,
                ToolbarNotification.create(this),
                serviceType
            )
            isRunning.set(true)
            Log.i(
                BackgroundTaskScheduler.LOG_TAG,
                "Foreground service active, notification=${ToolbarNotification.NOTIFICATION_ID}, pid=${Process.myPid()}"
            )
            true
        } catch (exception: Exception) {
            isRunning.set(false)
            Log.e(BackgroundTaskScheduler.LOG_TAG, "Unable to enter foreground", exception)
            stopSelf()
            false
        }
    }

    /** 系统通知前台服务运行超时时，移除工具栏通知并结束服务。 */
    override fun onTimeout(startId: Int, fgsType: Int) {
        Log.w(BackgroundTaskScheduler.LOG_TAG, "ForegroundService.onTimeout startId=$startId, type=$fgsType")
        isRunning.set(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        isRunning.set(false)
        Log.i(BackgroundTaskScheduler.LOG_TAG, "ForegroundService.onDestroy pid=${Process.myPid()}")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val EXTRA_START_SOURCE = "foreground_start_source"
        private val isRunning = AtomicBoolean(false)

        /** source 记录启动来源。服务已经运行时，只有显式请求刷新通知才再次发送启动请求。 */
        fun start(context: Context, source: String, refreshNotification: Boolean = false) {
            if (BackgroundToolkit.configuration?.enableForegroundToolbar != true) return
            if (isRunning.get() && !refreshNotification) {
                Log.i(BackgroundTaskScheduler.LOG_TAG, "Foreground service already active: $source")
                return
            }

            try {
                val intent = Intent(context, DrawLifeForegroundService::class.java)
                    .putExtra(EXTRA_START_SOURCE, source)
                ContextCompat.startForegroundService(context, intent)
                Log.i(BackgroundTaskScheduler.LOG_TAG, "Foreground service requested by $source")
            } catch (exception: Exception) {
                // 后台启动可能被 Android 拒绝，记录结果，等页面回到前台时再次申请。
                Log.e(BackgroundTaskScheduler.LOG_TAG, "Foreground service request rejected: $source", exception)
            }
        }
    }
}
