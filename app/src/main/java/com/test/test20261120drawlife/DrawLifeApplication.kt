package com.test.test20261120drawlife

import android.app.Application
import android.os.Process
import android.util.Log
import androidx.work.Configuration
import com.drawlife.backgroundtoolkit.BackgroundToolkit

internal const val LOG_TAG = "DrawLife"

class DrawLifeApplication : Application(), Configuration.Provider {
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            // WorkManager 的系统任务 ID 与手动注册的 100 号 Job 分开。
            .setJobSchedulerJobIdRange(1_000, Int.MAX_VALUE)
            .build()

    /**
     * 对标 Blood Pressure 的后台启动初始化。新建应用进程时执行一次，
     * 包括系统为投递广播或运行 Job、Worker 而创建进程的情况。
     *
     * - 广播：注册进程内监听。收到广播后尝试启动前台服务，是否启动由系统决定。
     * - 闹钟：恢复已保存的每日精确闹钟。开机广播也会触发恢复，闹钟到点后由接收器安排下一次。
     * - 前台服务：由首页、系统广播或高优先级 FCM 请求启动，成功后显示工具栏通知（34953）。
     * - 后台任务：安排最少等待 30 秒的 Job 100，以及需要联网的 15 分钟周期 Work。
     *
     * 样例也开启 FCM 测试。原应用的 60 秒和 5 分钟 Timer、健康推荐逻辑尚未接入。
     */
    override fun onCreate() {
        super.onCreate()
        Log.i(LOG_TAG, "Application.onCreate pid=${Process.myPid()}")
        BackgroundToolkit.initialize(
            this,
            BackgroundToolkit.Config(
                launchActivity = MainActivity::class.java,
                enableForegroundToolbar = true,
                enableDailyReminder = true,
                jobId = 100,
                enablePeriodicWork = true,
                periodicWorkName = "WorkerServiceUnique",
                enableFcmNotifications = true,
                enableFcmPriorityHandling = true,
                legacyJobServiceName = "com.test.test20261120drawlife.background.DrawLifeJobService"
            )
        )
    }
}
