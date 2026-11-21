package com.test.test20261120drawlife.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

class ForegroundServiceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        when (action) {
            Intent.ACTION_BOOT_COMPLETED -> Unit
            in stateActions -> Unit
            in packageActions -> if (intent.data?.scheme != "package") return
            in mediaActions -> if (intent.data?.scheme != "file") return
            else -> return
        }

        Log.i(BackgroundTaskScheduler.LOG_TAG, "Foreground trigger received: $action")
        if (action == Intent.ACTION_BOOT_COMPLETED &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM
        ) {
            // Android 15 起禁止在开机广播中启动 dataSync 前台服务。
            if (!DrawLifeForegroundService.isActive) {
                ToolbarNotification.restoreAfterBoot(context)
            }
            return
        }
        // 复用运行状态检查和异常处理。收到广播不代表系统允许启动前台服务。
        DrawLifeForegroundService.start(context, source = "broadcast:$action")
    }

    companion object {
        private val stateActions = listOf(
            Intent.ACTION_USER_PRESENT,
            Intent.ACTION_POWER_CONNECTED,
            Intent.ACTION_POWER_DISCONNECTED,
            WifiManager.WIFI_STATE_CHANGED_ACTION,
            WifiManager.NETWORK_STATE_CHANGED_ACTION
        )
        private val packageActions = listOf(
            Intent.ACTION_PACKAGE_ADDED,
            Intent.ACTION_PACKAGE_REPLACED,
            Intent.ACTION_PACKAGE_REMOVED
        )
        private val mediaActions = listOf(Intent.ACTION_MEDIA_EJECT, Intent.ACTION_MEDIA_MOUNTED)
        private val processReceiver = ForegroundServiceReceiver()
        private val registeredGroups = mutableSetOf<String>()

        @Synchronized
        fun registerForProcess(context: Context) {
            val appContext = context.applicationContext
            // 这 8 个动作在当前 targetSdk 下采用动态注册，生命周期与应用进程一致。
            // 开机及存储事件由清单接收，避免同一事件同时走静态、动态两个入口。
            val filters = mapOf(
                "state" to IntentFilter().apply {
                    stateActions.forEach { addAction(it) }
                },
                "package" to IntentFilter().apply {
                    packageActions.forEach { addAction(it) }
                    addDataScheme("package")
                }
            )
            for ((group, filter) in filters) {
                if (group in registeredGroups) continue
                try {
                    // 仅监听上述系统广播；EXPORTED 也允许系统中的特权进程投递。
                    ContextCompat.registerReceiver(
                        appContext, processReceiver, filter, ContextCompat.RECEIVER_EXPORTED
                    )
                    registeredGroups.add(group)
                    Log.i(BackgroundTaskScheduler.LOG_TAG, "Foreground broadcasts registered: $group")
                } catch (exception: RuntimeException) {
                    Log.e(
                        BackgroundTaskScheduler.LOG_TAG,
                        "Foreground broadcast registration failed: $group",
                        exception
                    )
                }
            }
        }
    }
}
