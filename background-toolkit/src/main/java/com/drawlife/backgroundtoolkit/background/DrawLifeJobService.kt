package com.drawlife.backgroundtoolkit.background

import android.app.job.JobParameters
import android.app.job.JobService
import android.os.Process
import android.util.Log
import com.drawlife.backgroundtoolkit.BackgroundToolkit

/** JobScheduler 投递任务时调用 onStartJob，记录任务 ID 和进程号后再提交下一轮。 */
internal class DrawLifeJobService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        if (params.jobId != BackgroundToolkit.configuration?.jobId) return false
        Log.i(
            BackgroundTaskScheduler.LOG_TAG,
            "Job.onStartJob id=${params.jobId}, pid=${Process.myPid()}"
        )

        BackgroundTaskScheduler.scheduleJob(this, keepExisting = false)

        // 返回 false 表示本轮工作已在回调内结束，无需稍后调用 jobFinished。
        return false
    }

    override fun onStopJob(params: JobParameters): Boolean {
        if (params.jobId != BackgroundToolkit.configuration?.jobId) return false
        Log.i(
            BackgroundTaskScheduler.LOG_TAG,
            "Job.onStopJob id=${params.jobId}, pid=${Process.myPid()}"
        )
        // onStartJob 已负责安排下一次，停止时无需再重复提交。
        return false
    }
}
