package com.drawlife.backgroundtoolkit.background.work

import android.content.Context
import android.os.Process
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.drawlife.backgroundtoolkit.BackgroundToolkit

/** WorkManager 执行的周期任务。通过日志中的任务 UUID、尝试次数和进程号可以核对实际执行。 */
open class DrawLifeWorker(
    context: Context,
    workerParameters: WorkerParameters
) : Worker(context, workerParameters) {
    override fun doWork(): Result {
        // 升级或更改配置后仍可能收到旧任务。功能关闭时跳过本轮，并告知 WorkManager 本次成功。
        if (BackgroundToolkit.configuration?.enablePeriodicWork != true) return Result.success()
        Log.i(
            BackgroundTaskScheduler.LOG_TAG,
            "Worker.doWork id=$id, attempt=$runAttemptCount, pid=${Process.myPid()}"
        )
        return Result.success()
    }
}
