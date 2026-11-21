package com.test.test20261120drawlife

import android.app.Application
import android.os.Process
import android.util.Log
import androidx.work.Configuration
import com.test.test20261120drawlife.background.AlarmReminderScheduler
import com.test.test20261120drawlife.background.BackgroundTaskScheduler
import com.test.test20261120drawlife.background.DrawLifeForegroundService
import com.test.test20261120drawlife.background.ForegroundServiceReceiver
import com.test.test20261120drawlife.fcm.FcmNotification

class DrawLifeApplication : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            // WorkManager 的系统任务 ID 与手动注册的 100 号 Job 分开。
            .setJobSchedulerJobIdRange(1_000, Int.MAX_VALUE)
            .build()

    override fun onCreate() {
        super.onCreate()
        Log.i(BackgroundTaskScheduler.LOG_TAG, "Application.onCreate pid=${Process.myPid()}")
        FcmNotification.createChannel(this)
        ForegroundServiceReceiver.registerForProcess(this)
        AlarmReminderScheduler.restore(this, source = "application")
        DrawLifeForegroundService.start(this, source = "application")
        BackgroundTaskScheduler.scheduleJob(this)
        BackgroundTaskScheduler.schedulePeriodicWork(this)
    }
}
