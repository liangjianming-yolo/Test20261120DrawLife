package com.test.test20261120drawlife

import android.app.Application
import android.os.Process
import android.util.Log
import androidx.work.Configuration
import com.drawlife.backgroundtoolkit.BackgroundToolkit
import com.test.test20261120drawlife.background.DemoDailyReminderNotification
import com.test.test20261120drawlife.background.DemoHeartbeat
import com.test.test20261120drawlife.background.DemoToolbarNotification
import com.test.test20261120drawlife.fcm.DemoFcmMessageHandler

internal const val LOG_TAG = "DrawLife"

class DrawLifeApplication : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            // WorkManager 的系统任务 ID 与手动注册的 100 号 Job 分开。
            .setJobSchedulerJobIdRange(1_000, Int.MAX_VALUE)
            .build()

    override fun onCreate() {
        super.onCreate()
        Log.i(LOG_TAG, "Application.onCreate pid=${Process.myPid()}")
        // 广播或 FCM 也可以创建应用进程，因此在这里注册宿主实现。
        DemoFcmMessageHandler.initialize(this)
        BackgroundToolkit.initialize(
            this,
            BackgroundToolkit.Config(
                foregroundNotification = DemoToolbarNotification,
                dailyReminderNotification = DemoDailyReminderNotification,
                fcmMessageHandler = DemoFcmMessageHandler,
                heartbeat = DemoHeartbeat.config(this),
                jobId = 100,
                enablePeriodicWork = true,
                periodicWorkName = "WorkerServiceUnique",
                legacyJobServiceName = "com.test.test20261120drawlife.background.DrawLifeJobService"
            )
        )
    }
}
